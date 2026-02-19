package canopy.app.game

import canopy.app.CanopyScreen
import canopy.core.managers.InjectionManager
import canopy.core.managers.ManagersRegistry
import canopy.core.managers.SceneManager
import ktx.app.KtxGame
import ktx.async.KtxAsync

/**
 * CanopyGame is the main entry point for a Canopy application. It extends KtxGame, providing lifecycle management and integration with the scene manager and managers registry.
 * It also defines callbacks for creation, resizing, and disposal, allowing users to customize behavior at
 * different stages of the game lifecycle. The internalLaunch function is used by variants to launch the game with the appropriate backend and configuration, while the abstract launch function must be implemented by each variant to specify how to launch the game.
 * The CanopyGame class is designed to be flexible and extensible, allowing for different backends and configurations while maintaining a consistent structure for game development.
 * The lifecycle of the game is as follows:
 * 1. create() is called, which initializes KtxAsync, registers the scene manager
 *   and sets up the managers registry, then calls the onCreate callback.
 * 2. resize() is called whenever the game window is resized, which delegates to the scene manager and then calls the onResize callback.
 * 3. dispose() is called when the game is closed, which tears down the managers registry and then calls the onDispose callback.
 * The launch() function is called by variants to start the game, which in turn calls internalLaunch() with the appropriate backend and configuration. This allows for different backends (e.g., desktop, web) to be used while keeping the launch logic centralized in CanopyGame.
 * Overall, CanopyGame provides a structured and flexible foundation for building games with the Canopy framework, handling common lifecycle events and allowing for customization through callbacks and variant-specific launch implementations.
 *
 */
abstract class CanopyGame<C : CanopyGameConfig>(
    protected val sceneManager: SceneManager = SceneManager(),
    config: C? = null,
    // Lifecycle callbacks
    protected val onCreate: (CanopyGame<C>) -> Unit = {},
    protected val onResize: (CanopyGame<C>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    protected val onDispose: (CanopyGame<C>) -> Unit = {},
) : KtxGame<CanopyScreen>() {

    protected val injectionManager by lazy { ManagersRegistry.get(InjectionManager::class) }

    protected val config: C = config ?: defaultConfig()

    /**
     * Initializes the game, setting up the scene manager and managers registry, then calling the onCreate callback.
     */
    override fun create() {
        KtxAsync.initiate()

        // Register core managers
        ManagersRegistry.apply {
            register(InjectionManager())
            register(sceneManager)
        }.setup()

        onCreate(this)
        super.create()
    }

    /**
     * Resizes the game, delegating to the scene manager and then calling the onResize callback.
     */
    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        sceneManager.resize(width, height)
        onResize(this, width, height)
    }

    /**
     * Disposes of the game, tearing down the managers registry and then calling the onDispose callback.
     */
    override fun dispose() {
        ManagersRegistry.teardown()
        onDispose(this)
        super.dispose()
    }

    abstract fun defaultConfig(): C

    protected abstract fun internalLaunch(config: C, vararg args: String): CanopyGameHandle

    fun launch(vararg args: String): CanopyGameHandle = internalLaunch(
        config,
        *args
    )
}
