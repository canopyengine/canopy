package io.canopy.engine.physics.systems

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.World
import io.canopy.engine.core.managers.GameManager
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.nodes.core.TreeSystem
import io.canopy.engine.physics.nodes.body.PhysicsBody2D
import ktx.box2d.createWorld
import ktx.log.logger

class PhysicsSystem(gravity: Vector2 = Vector2.Zero) :
    TreeSystem(
        phase = UpdatePhase.PhysicsPre,
        0,
        PhysicsBody2D::class
    ) {
    private val logger = logger<PhysicsSystem>()
    val debugRenderer: Box2DDebugRenderer? =
        if (GameManager.onDebugMode()) Box2DDebugRenderer() else null

    private val contactListener = PhysicsContactListener()

    // Physics world
    private var world: World =
        createWorld(gravity).apply {
            setContactListener(contactListener)
        }

    override fun onRegister() {
        injectionManager.registerInjectable(World::class) { world }
    }

    override fun afterProcess(delta: Float) {
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
