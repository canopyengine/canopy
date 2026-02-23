package io.canopy.engine.app.core

import io.canopy.engine.core.CanopyBuildInfo
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.api.LogLevel
import io.canopy.engine.logging.bootstrap.CanopyLogging
import io.canopy.engine.logging.engine.EngineLogs
import ktx.app.KtxGame
import ktx.async.KtxAsync

abstract class CanopyApp<C : CanopyAppConfig>(
    protected val sceneManager: SceneManager = SceneManager(),
    config: C? = null,
    protected val onCreate: (CanopyApp<C>) -> Unit = {},
    protected val onResize: (CanopyApp<C>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    protected val onDispose: (CanopyApp<C>) -> Unit = {},
    protected val logLevel: LogLevel = LogLevel.DEBUG,
) : KtxGame<CanopyScreen>() {

    protected val injectionManager by lazy { ManagersRegistry.get(InjectionManager::class) }
    protected val config: C = config ?: defaultConfig()

    private var frame: Long = 0

    override fun create() {
        // Init logging FIRST (so startup logs are captured)
        val runId = CanopyLogging.defaultRunId()
        val logDir = CanopyLogging.defaultBaseLogDir()
        val engineVersion = CanopyBuildInfo.version

        ConsoleBanner.print(CanopyBuildInfo.version, ConsoleBanner.Mode.GRADIENT)

        CanopyLogging.init(
            CanopyLogging.Config(
                baseLogDir = logDir,
                runId = runId,
                engineVersion = engineVersion,
                consoleLevel = LogLevel.INFO,
                engineLogLevel = LogLevel.INFO,
                userLogLevel = logLevel
            )
        )

        EngineLogs.lifecycle.info(
            fields = mapOf(
                "event" to "app.launch",
                "backend" to this::class.simpleName
            )
        ) { "Launching app" }

        KtxAsync.initiate()

        ManagersRegistry.apply {
            register(InjectionManager())
            register(sceneManager)
        }.setup()

        onCreate(this)
        super.create()
    }

    override fun render() {
        frame++
        LogContext.with("frame" to frame) {
            super.render()
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        sceneManager.resize(width, height)
        onResize(this, width, height)
    }

    override fun dispose() {
        try {
            EngineLogs.lifecycle.info(fields = mapOf("event" to "app.dispose")) { "Disposing app" }
            ManagersRegistry.teardown()
            CanopyLogging.end(reason = "normal")
        } catch (t: Throwable) {
            CanopyLogging.end(reason = "crash", t = t)
            throw t
        } finally {
            onDispose(this)
            super.dispose()
        }
    }

    abstract fun defaultConfig(): C
    protected abstract fun internalLaunch(config: C, vararg args: String): CanopyAppHandle

    fun launch(vararg args: String): CanopyAppHandle = internalLaunch(config, *args)
}
