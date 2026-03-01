package io.canopy.engine.physics.nodes.body

import com.badlogic.gdx.physics.box2d.BodyDef
import io.canopy.engine.physics.systems.PhysicsSystem
import ktx.log.logger

class StaticBody2D(
    // Base props
    name: String,
    block: StaticBody2D.() -> Unit = {},
) : PhysicsBody2D<StaticBody2D>(
    name,
    bodyType = BodyDef.BodyType.StaticBody,
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
