package io.canopy.platforms.terminal.app

import java.util.concurrent.atomic.AtomicBoolean
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.coroutines.receiveEventsFlow
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.adapters.logback.LogbackLogging
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.input.InputSystem
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.ui.UiManager
import io.canopy.platforms.terminal.data.assets.TerminalAssetsManager
import io.canopy.tooling.utils.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.takeWhile

/**
 * Application hosted by the terminal runtime, with queued keyboard input and a synchronous frame loop.
 * Engine and game diagnostics go to `.canopy/logs` by default, leaving terminal output for the game.
 * Select `logging(LoggingPolicy.Host)` before launch to retain host-owned logging instead.
 */
class TerminalApp internal constructor(
    private val terminal: Terminal = Terminal(interactive = true),
    private val output: (String) -> Unit = { terminal.rawPrint(it) },
    private val viewport: () -> Size = { terminal.updateSize() },
) : App<AppConfig>() {

    private val log = EngineLogs.app
    private var notifiedViewport: Pair<Int, Int>? = null

    /**
     * Maximum bottom-panel height in terminal rows, including its editor row; defaults to 8.
     * Must be positive. Clamped to leave a world row when the viewport has at least two rows.
     * Together with [commandPanelHeightFraction], changes take effect on the next lifecycle-thread frame;
     * ignored in line mode.
     */
    var commandPanelRows: Int = 8
        set(value) {
            require(value > 0) { "commandPanelRows must be positive" }
            field = value
        }

    /**
     * Fraction of terminal height allocated to the open command panel, defaulting to one third.
     * Must be finite and in `(0, 1]`. Rounded up to whole rows, capped by [commandPanelRows], and clamped
     * to keep the editor visible and leave a world row whenever the viewport has at least two rows.
     * Raw terminal resizing is observed each lifecycle-thread frame, including while gameplay is paused.
     * Resizing preserves command focus, draft and transcript. Ignored in line mode.
     */
    var commandPanelHeightFraction: Double = 1.0 / 3.0
        set(value) {
            require(value.isFinite() && value > 0.0 && value <= 1.0) {
                "commandPanelHeightFraction must be finite and in (0, 1]"
            }
            field = value
        }

    private val inputManager = MordantInputManager()
    private val assetsManager = TerminalAssetsManager()

    @Volatile
    private var lineInputMode = false

    private val lineInput = TerminalLineInputBridge(inputManager)
    private val surface = TerminalSurface(
        terminal,
        viewport,
        output,
        { lineInputMode }
    )
    private val commandPresentation = TerminalCommandPresentation(
        lineMode = { lineInputMode },
        output = output,
        ui = TerminalCommandUi(terminal, viewport, surface, { commandPanelRows }, { commandPanelHeightFraction })
    )
    private val commandHost = CommandPromptHost(this, commandPresentation)

    // App-wide coroutine scope
    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Submits a copied world frame on the lifecycle thread. In raw mode, the open command panel overlays the
     * bottom rows while world updates remain visible above it. Closing restores the latest copied world.
     * Rows and text cells are clipped to the viewport; the final column is reserved to prevent scrolling.
     * SGR styling is preserved; cursor and other control input is sanitized.
     * Line input suppresses screen output so it cannot overwrite the blocking editor.
     */
    fun renderFrame(lines: List<String>) {
        surface.renderWorld(lines)
    }

    override fun defaultLoggingPolicy() = LogbackLogging()

    /** Native host boundary: forward geometry before entering frame dispatch, never from a manager callback. */
    internal fun updateTerminalFrame(delta: Float) {
        val size = if (lineInputMode) {
            null
        } else {
            viewport().let {
                Size(it.width.coerceAtLeast(0), it.height.coerceAtLeast(0))
            }
        }
        if (size != null && (size.width to size.height) != notifiedViewport) {
            engineLoop.resize(size.width, size.height)
            // Retry unchanged geometry if a resize listener failed.
            notifiedViewport = size.width to size.height
        }
        val processedLine = lineInput.processEvents()
        engineLoop.update(delta)
        processedLine?.complete(Unit)
    }

    internal fun prepareLineInputPresentation() = lineInput.preparePresentation { lineInputMode = true }

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Terminal Canopy App"
    )

    override fun provideManagers() = listOf(
        inputManager,
        assetsManager,
        commandHost,
        surface
    )

    @OptIn(UnstableApi::class)
    override fun SceneManager.configureSceneManager() {
        addSystem(InputSystem())
    }

    override fun afterEnter() {
        manager<UiManager>().backend = TerminalUiBackend(terminal, surface::renderUi)
    }

    override fun beforeExit() {
        appScope.cancel()
        lineInput.cancel()
    }

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        val frameNanos = 1_000_000_000L / config.fps
        val running = AtomicBoolean(true)

        installBackendHandle(
            requestExit = { running.set(false) },
            forceClose = { running.set(false) }
        )

        engineLoop.enter()
        withLoggingContext { log.info { "Starting terminal runtime" } }

        // 🔹 Start async input handling
        val inputJob = appScope.launch {
            try {
                terminal.receiveEventsFlow()
                    .takeWhile { !(it is KeyboardEvent && it.isCtrlC) }
                    .collect { event ->
                        when (event) {
                            is KeyboardEvent -> {
                                withLoggingContext { log.trace("key" to event.key) { "Key: ${event.key}" } }

                                // Forward into engine input system (converts to Canopy InputEvent)
                                inputManager.enqueueMordantKeyEvent(event)
                            }

                            else -> {
                                // Mordant only produces keyboard events
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // Normal shutdown
            } catch (t: Throwable) {
                withLoggingContext {
                    log.info { "Raw terminal input unavailable; switching to line input: ${t.message}" }
                }
                try {
                    prepareLineInputPresentation().await()
                    while (true) {
                        if (!running.get()) break
                        val line = withContext(Dispatchers.IO) { readLine() } ?: break
                        if (!running.get()) break
                        lineInput.submit(line).await()
                    }
                } catch (e: CancellationException) {
                    // Normal shutdown
                } catch (fallbackError: Throwable) {
                    withLoggingContext { log.error(fallbackError) { "Line input loop crashed" } }
                }
            } finally {
                // Ctrl+C or flow ended → stop app
                running.set(false)
            }
        }

        var lastTime = System.nanoTime()

        // 🔹 Main loop (sync)
        try {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val now = System.nanoTime()
                val deltaNanos = now - lastTime
                lastTime = now

                val delta = deltaNanos / 1_000_000_000f

                // Notify geometry, then drain input and process the frame before acknowledging a submitted line.
                updateTerminalFrame(delta)

                // Frame limiting
                val elapsed = System.nanoTime() - now
                val sleepNanos = frameNanos - elapsed
                if (sleepNanos > 0) {
                    Thread.sleep(
                        sleepNanos / 1_000_000L,
                        (sleepNanos % 1_000_000L).toInt()
                    )
                }
            }

            // 🔹 Shutdown
        } catch (_: InterruptedException) {
            // Interrupted frame-limiting sleep is a graceful stop. Callback failures remain retained.
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            engineLoop.reportFailure(error)
            throw error
        } finally {
            inputJob.cancel()
            appScope.cancel()
            lineInput.cancel()
            engineLoop.exit()
        }
    }
}

/** Constructs and configures an application without launching it. */
fun terminalApp(builder: TerminalApp.() -> Unit = {}): TerminalApp = TerminalApp().apply(builder)
