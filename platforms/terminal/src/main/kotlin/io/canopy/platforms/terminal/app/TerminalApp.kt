package io.canopy.platforms.terminal.app

import java.util.concurrent.atomic.AtomicBoolean
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.coroutines.receiveEventsFlow
import com.github.ajalt.mordant.input.isCtrlC
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
class TerminalApp internal constructor() : App<AppConfig>() {

    private val log = EngineLogs.app
    private val terminal = Terminal(interactive = true)

    private val inputManager = MordantInputManager()
    private val assetsManager = TerminalAssetsManager()

    @Volatile
    private var lineInputMode = false

    private val lineInput = TerminalLineInputBridge(inputManager)
    private val commandPresentation = TerminalCommandPresentation(
        lineMode = { lineInputMode },
        output = { terminal.rawPrint(it) },
        restoreFrame = {}
    )
    private val commandHost = CommandPromptHost(this, commandPresentation)

    // App-wide coroutine scope
    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Replaces the interactive screen with one frame, including when [lines] is empty.
     * Rendering is suspended while command presentation or line input owns the terminal.
     */
    fun renderFrame(lines: List<String>) {
        if (!lineInputMode && !commandPresentation.isVisible) {
            terminal.rawPrint(buildTerminalFrame(terminal, lines))
        }
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

/** Full-screen replacement also erases rows occupied by wrapped output in the previous frame. */
internal fun buildTerminalFrame(terminal: Terminal, lines: List<String>): String = buildString {
    append(
        terminal.cursor.getMoves {
            clearScreen()
            setPosition(0, 0)
        }
    )
    lines.forEachIndexed { index, line ->
        append(
            terminal.cursor.getMoves {
                setPosition(0, index)
                clearLine()
            }
        )
        append(line).append('\n')
    }
}
