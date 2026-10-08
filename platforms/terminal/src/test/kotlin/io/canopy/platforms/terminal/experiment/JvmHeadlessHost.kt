package io.canopy.platforms.terminal.experiment

import java.util.concurrent.atomic.AtomicBoolean
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.platforms.terminal.data.assets.TerminalAssetsManager

/** Unpublished experiment: no terminal input, rendering, LibGDX globals or native services. */
internal class JvmHeadlessHost(
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = { nanos ->
        Thread.sleep(nanos / 1_000_000L, (nanos % 1_000_000L).toInt())
    },
) {
    fun run(app: App<*>, fps: Int) {
        require(fps in 1..1_000_000_000) { "fps must be between 1 and 1,000,000,000" }
        val period = 1_000_000_000L / fps
        val owner = Thread.currentThread()
        val running = AtomicBoolean(true)
        val control = Any()
        var active = true
        val stop = {
            synchronized(control) {
                if (active) {
                    running.set(false)
                    // A retained handle must not interrupt unrelated work after the host has stopped.
                    if (Thread.currentThread() !== owner) owner.interrupt()
                }
            }
        }
        app.installBackendHandle(requestExit = stop, forceClose = stop)

        var failure: Throwable? = null
        try {
            // Entry followed by immediate exit completes both handles even on a pre-interrupted launch.
            app.engineLoop.enter()
            var lastTime = nanoTime()
            while (running.get() && !owner.isInterrupted) {
                val frameStart = nanoTime()
                app.engineLoop.update((frameStart - lastTime) / 1_000_000_000f)
                lastTime = frameStart
                if (!running.get() || owner.isInterrupted) break

                // A slow frame starts a fresh budget next iteration; there is no host catch-up burst.
                val remaining = frameStart + period - nanoTime()
                if (remaining > 0) {
                    try {
                        sleep(remaining)
                    } catch (_: InterruptedException) {
                        owner.interrupt()
                        running.set(false)
                    }
                }
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            synchronized(control) { active = false }
            try {
                app.engineLoop.exit()
            } catch (error: Throwable) {
                val first = failure
                if (first == null) {
                    failure = error
                } else if (first !== error) {
                    first.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }
}

/** Test-only application deliberately reuses existing JVM assets instead of copying their implementation. */
internal class JvmHeadlessApp(private val host: JvmHeadlessHost = JvmHeadlessHost()) : App<AppConfig>() {
    override fun defaultConfig() = AppConfig(title = "JVM headless experiment")
    override fun provideManagers() = listOf(TerminalAssetsManager())
    override fun internalLaunch(config: AppConfig, vararg args: String) = host.run(this, config.fps)
}
