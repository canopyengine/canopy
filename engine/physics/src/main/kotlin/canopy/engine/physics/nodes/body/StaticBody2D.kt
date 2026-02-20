package canopy.engine.physics.nodes.body

import canopy.engine.core.nodes.core.Behavior
import canopy.engine.physics.systems.PhysicsSystem
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import ktx.log.logger

class StaticBody2D(
    // Base props
    name: String,
    script: (node: StaticBody2D) -> Behavior<StaticBody2D>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1F, 1F),
    rotation: Float = 0F,
    groups: MutableList<String> = mutableListOf(),
    block: StaticBody2D.() -> Unit = {},
) : PhysicsBody2D<StaticBody2D>(
    name,
    bodyType = BodyDef.BodyType.StaticBody,
    script,
    position,
    scale,
    rotation,
    groups,
    block
) {
    private val logger = logger<StaticBody2D>()

    init {
        if (!sceneManager.hasSystem(PhysicsSystem::class)) {
            logger.error {
                """

                [PHYSICS NODE]
                The physics pipeline wasn't setup - physics will be disabled.

                To fix it: register it into a Scene Manager!

                """.trimIndent()
            }
        }
    }

    override fun nodePhysicsUpdate(delta: Float) {
        super.nodePhysicsUpdate(delta)
        body.setTransform(globalPosition.x, globalPosition.y, rotation)
    }
}
