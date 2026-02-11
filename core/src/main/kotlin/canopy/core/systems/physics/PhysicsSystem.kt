package anchors.framework.systems.physics

import anchors.framework.managers.GameManager
import anchors.framework.nodes.core.GlobalNodeSystem
import anchors.framework.nodes.core.Node
import anchors.framework.nodes.core.UpdatePhase
import anchors.framework.nodes.types.physics.body.PhysicsBody2D
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.World
import ktx.box2d.createWorld
import ktx.log.logger

class PhysicsSystem :
    GlobalNodeSystem(
        phase = UpdatePhase.PhysicsBeforeScene,
        PhysicsBody2D::class,
    ) {
    private val logger = logger<PhysicsSystem>()
    val debugRenderer: Box2DDebugRenderer? =
        if (GameManager.onDebugMode()) Box2DDebugRenderer() else null

    // Physics world
    private var world: World? = null
    private val contactListener = PhysicsContactListener()

    override fun onSystemInit() {
        sceneManager.registerInjectable(World::class) { world }
    }

    override fun afterProcess(delta: Float) {
        val worldSnapshot = world ?: return
        worldSnapshot.step(delta, 6, 2)
        debugRenderer?.render(world, sceneManager.activeCamera?.camera?.combined)
    }

    override fun onNodeRemoved(node: Node<*>) {
        world?.destroyBody((node as PhysicsBody2D).body)
    }

    fun replaceWorld(gravity: Vector2 = Vector2.Zero) {
        world?.dispose()
        world =
            createWorld(gravity).apply {
                // Contact callbacks
                setContactListener(contactListener)
            }
    }
}
