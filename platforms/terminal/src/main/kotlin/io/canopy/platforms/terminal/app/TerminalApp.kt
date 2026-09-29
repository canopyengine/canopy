package io.canopy.platforms.terminal.app

import kotlin.time.Duration.Companion.milliseconds
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.coroutines.receiveEventsFlow
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputSystem
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent
import io.canopy.engine.logging.EngineLogs
import io.canopy.platforms.terminal.data.assets.TerminalAssetsManager
import io.canopy.tooling.utils.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.takeWhile

class TerminalApp internal constructor() : App<AppConfig>() {

    private val log = EngineLogs.app
    private val terminal = Terminal(interactive = true)

    private val inputManager = MordantInputManager()
    private val assetsManager = TerminalAssetsManager()

    @Volatile
    private var lineInputMode = false

    private var hasRenderedFrame = false

    // App-wide coroutine scope
    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Clears the interactive terminal and renders one frame of demo output. */
    fun renderFrame(lines: List<String>) {
        if (!lineInputMode) {
            val frame = buildString {
                if (!hasRenderedFrame) {
                    append(terminal.cursor.getMoves { clearScreen() })
                    hasRenderedFrame = true
                }
                lines.forEachIndexed { index, line ->
                    append(
                        terminal.cursor.getMoves {
                            setPosition(1, index + 1)
                            clearLine()
                        }
                    )
                    append(line).append('\n')
                }
            }
            terminal.rawPrint(frame)
        }
    }

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Terminal Canopy App"
    )

    override fun provideManagers() = listOf(
        inputManager,
        assetsManager
    )

    @OptIn(UnstableApi::class)
    override fun SceneManager.configureSceneManager() {
        addSystem(InputSystem())
    }

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        log.info { "Starting terminal runtime" }

        val frameNanos = 1_000_000_000L / config.fps
        var running = true

        installBackendHandle(
            requestExit = { running = false },
            forceClose = { running = false }
        )

        enter()

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
                    lineInputMode = true
                    while (running) {
                        val line = withContext(Dispatchers.IO) { readln() }
                        lineInputMode = false
                        inputManager.enqueue(TextInputEvent(line))
                        inputManager.enqueue(KeyInputEvent(Key.ENTER, state = InputState.JustPressed))
                        delay(50.milliseconds)
                        lineInputMode = true
                    }
                } catch (e: CancellationException) {
                    // Normal shutdown
                } catch (fallbackError: Throwable) {
                    log.error(fallbackError) { "Line input loop crashed" }
                }
            } finally {
                // Ctrl+C or flow ended → stop app
                running = false
            }
        }

        var lastTime = System.nanoTime()

        // 🔹 Main loop (sync)
        while (running && !Thread.currentThread().isInterrupted) {
            val now = System.nanoTime()
            val deltaNanos = now - lastTime
            lastTime = now

            val delta = deltaNanos / 1_000_000_000f

            // Process input FIRST (drains queue → updates action states)
            inputManager.processEvents()

            // Process frame
            update(delta)

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
        inputJob.cancel()
        appScope.cancel()

        exit()
    }
}

fun terminalApp(builder: TerminalApp.() -> Unit = {}): TerminalApp = TerminalApp().apply(builder)
