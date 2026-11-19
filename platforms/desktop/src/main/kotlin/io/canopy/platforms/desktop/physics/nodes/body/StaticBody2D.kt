package io.canopy.platforms.desktop.physics.nodes.body

import com.badlogic.gdx.physics.box2d.BodyDef
import io.canopy.engine.physics.systems.PhysicsSystem
import ktx.log.logger

/**
 * Extensible node backed by a static Box2D body, synchronized during physics callbacks.
 * @param N Concrete node type exposed to its initialization block.
 * @param name Optional explicit name; otherwise the concrete class and its sibling count determine the name.
 * @param block Runs once after onSetup, before entry callbacks; not replayed on reentry.
 */
open class StaticBody2D<N : StaticBody2D<N>>(
    // Base props
    name: String? = null,
    block: N.() -> Unit = {},
) : PhysicsBody2D<N>(
    name,
    bodyType = BodyDef.BodyType.StaticBody,
    block
) {
    private val logger = logger<StaticBody2D<*>>()

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
