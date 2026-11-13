package io.canopy.platforms.desktop.physics.systems

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.World
import io.canopy.adapters.libgdx.backends.desktop.physics.nodes.body.PhysicsBody2D
import io.canopy.engine.core.managers.GameManager
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.TreeSystem
import ktx.box2d.createWorld
import ktx.log.logger

/**
 * Steps a shared Box2D world in PhysicsPre and destroys bodies when tracked physics nodes are removed.
 * Application pause stops world simulation by default, independently of node processing modes.
 * Eligible nodes still receive physics callbacks while the world is stopped.
 */
class PhysicsSystem(gravity: Vector2 = Vector2.Zero) :
    TreeSystem(
        phase = TreeSystem.UpdatePhase.PhysicsPre,
        0,
        PhysicsBody2D::class
    ) {
    private val logger = logger<PhysicsSystem>()
    private val scenes by lazyManager<SceneManager>()

    /**
     * Whether the shared world advances while the application is paused. Defaults to false.
     * Enabling this advances every body, including bodies whose nodes cannot process; it does not
     * change node callback eligibility. Change this property on the engine thread.
     */
    var simulateWhilePaused: Boolean = false

    val debugRenderer: Box2DDebugRenderer? =
        if (GameManager.isDebugMode()) Box2DDebugRenderer() else null

    private val contactListener = PhysicsContactListener()

    // Physics world
    private var world: World =
        createWorld(gravity).apply {
            setContactListener(contactListener)
        }

    override fun onRegister() {
        manager<InjectionManager>() += { world }
    }

    override fun afterProcess(delta: Float) {
        if (scenes.isPaused && !simulateWhilePaused) return
        val worldSnapshot = world ?: return
        worldSnapshot.step(delta, 6, 2)
        // debugRenderer?.render(world, sceneManager.activeCamera?.camera?.combined)
    }

    override fun onNodeRemoved(node: Node<*>) {
        world.destroyBody((node as PhysicsBody2D).body)
    }

    fun replaceWorld(gravity: Vector2 = Vector2.Zero) {
        world.dispose()

        world =
            createWorld(gravity).apply {
                setContactListener(contactListener)
            }
    }
}
