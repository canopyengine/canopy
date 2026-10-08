package io.canopy.platforms.terminal.app

import java.util.concurrent.atomic.AtomicBoolean
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.coroutines.receiveEventsFlow
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputSystem
import io.canopy.engine.logging.EngineLogs
import io.canopy.platforms.terminal.data.assets.TerminalAssetsManager
import io.canopy.tooling.utils.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.takeWhile

/** Application hosted by the terminal runtime, with queued keyboard input and a synchronous frame loop. */
class TerminalApp internal constructor(
    private val terminal: Terminal = Terminal(interactive = true),
    private val output: (String) -> Unit = { terminal.rawPrint(it) },
    private val viewport: () -> Size = { terminal.updateSize() },
) : App<AppConfig>() {

    private val log = EngineLogs.app

    /**
     * Maximum bottom-panel height in terminal rows, including its editor row; defaults to 8.
     * Must be positive. Clamped to leave a world row when the viewport has at least two rows.
     * Changes take effect on the next lifecycle-thread presentation or world render; ignored in line mode.
     */
    var commandPanelRows: Int = 8
        set(value) {
            require(value > 0) { "commandPanelRows must be positive" }
            field = value
        }

    private val inputManager = MordantInputManager()
    private val assetsManager = TerminalAssetsManager()

    @Volatile
    private var lineInputMode = false

    private val lineInput = TerminalLineInputBridge(inputManager)
    private val surface = TerminalSurface(terminal, viewport, output, { commandPanelRows }, { lineInputMode })
    private val commandPresentation = TerminalCommandPresentation(
        lineMode = { lineInputMode },
        output = output,
        restoreFrame = surface::hidePrompt,
        renderOverlay = surface::renderPrompt
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

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Terminal Canopy App"
    )

    override fun provideManagers() = listOf(
        inputManager,
        assetsManager,
        commandHost
    )

    @OptIn(UnstableApi::class)
    override fun SceneManager.configureSceneManager() {
        addSystem(InputSystem())
    }

    override fun beforeExit() {
        appScope.cancel()
        lineInput.cancel()
    }

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        log.info { "Starting terminal runtime" }

        val frameNanos = 1_000_000_000L / config.fps
        val running = AtomicBoolean(true)

        installBackendHandle(
            requestExit = { running.set(false) },
            forceClose = { running.set(false) }
        )

        engineLoop.enter()

        // 🔹 Start async input handling
        val inputJob = appScope.launch {
            try {
                terminal.receiveEventsFlow()
                    .takeWhile { !(it is KeyboardEvent && it.isCtrlC) }
                    .collect { event ->
                        when (event) {
                            is KeyboardEvent -> {
                                log.trace("key" to event.key) { "Key: ${event.key}" }

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
                log.info { "Raw terminal input unavailable; switching to line input: ${t.message}" }
                try {
                    lineInput.preparePresentation { lineInputMode = true }.await()
                    while (true) {
                        if (!running.get()) break
                        val line = withContext(Dispatchers.IO) { readLine() } ?: break
                        if (!running.get()) break
                        lineInput.submit(line).await()
                    }
                } catch (e: CancellationException) {
                    // Normal shutdown
                } catch (fallbackError: Throwable) {
                    log.error(fallbackError) { "Line input loop crashed" }
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

                // Process input FIRST (drains queue → updates action states)
                val processedLine = lineInput.processEvents()

                // Process frame
                engineLoop.update(delta)
                processedLine?.complete(Unit)

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
