package canopy.core.app

import canopy.core.backend.CanopyBackend
import canopy.core.backend.CanopyBackendConfig
import canopy.core.backend.CanopyBackendHandle
import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
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
abstract class CanopyGame<C : CanopyBackendConfig>(
    protected val sceneManager: SceneManager = SceneManager(),
    private val backend: CanopyBackend<C>,
    // Lifecycle callbacks
    val onCreate: (SceneManager) -> Unit = {},
    val onResize: (SceneManager, width: Int, height: Int) -> Unit = { _, _, _ -> },
    val onDispose: (SceneManager) -> Unit = {},
) : KtxGame<CanopyScreen>() {
    /**
     * Initializes the game, setting up the scene manager and managers registry, then calling the onCreate callback.
     */
    override fun create() {
        KtxAsync.initiate()
        ManagersRegistry.register(sceneManager)
        ManagersRegistry.setup()

        onCreate(sceneManager)
    }

    /**
     * Resizes the game, delegating to the scene manager and then calling the onResize callback.
     */
    override fun resize(
        width: Int,
        height: Int,
    ) {
        sceneManager.resize(width, height)
        onResize(sceneManager, width, height)
    }

    /**
     * Disposes of the game, tearing down the managers registry and then calling the onDispose callback.
     */
    override fun dispose() {
        ManagersRegistry.teardown()
        onDispose(sceneManager)
    }

    abstract fun defaultConfig(): C

    open fun launch(
        config: C = defaultConfig(),
        vararg args: String,
    ): CanopyBackendHandle = backend.launch(this, config, *args)
}
