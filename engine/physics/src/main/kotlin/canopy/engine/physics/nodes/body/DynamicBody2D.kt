package canopy.engine.physics.nodes.body

import canopy.engine.core.nodes.core.Behavior
import canopy.engine.physics.systems.PhysicsSystem
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import ktx.log.logger

open class DynamicBody2D<T : DynamicBody2D<T>>(
    name: String,
    script: (node: T) -> Behavior<T>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1F, 1F),
    rotation: Float = 0F,
    groups: MutableList<String> = mutableListOf(),
    block: T.() -> Unit = {},
) : PhysicsBody2D<T>(
    name,
    bodyType = BodyDef.BodyType.DynamicBody,
    script,
    position,
    scale,
    rotation,
    groups,
    block
) {
    private val logger = logger<DynamicBody2D<T>>()

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

    /**
     * ⚡ Instant velocity movement (teleports velocity, not the body).
     * Good for direct controller movement.
     *
     * Example: character walking, flying, sliding.
     */
    fun moveInstant(velocity: Vector2) {
        body.linearVelocity = velocity
    }

    /**
     * 🌀 Smooth force-based movement.
     * Good for heavy bodies, vehicles, dragging effects.
     *
     * Add smoothing factor if needed.
     */
    fun moveSmooth(force: Vector2) {
        TODO("NEED TO FIX MOVEMENT")
        body.applyForceToCenter(force, true)
    }

    /**
     * 💥 Impulse burst.
     * Good for jumps, dashes, recoil, knockback.
     */
    fun moveImpulse(impulse: Vector2) {
        TODO("NEED TO FIX MOVEMENT")
        body.applyLinearImpulse(impulse, body.worldCenter, true)
    }

    /**
     * Teleports the body (allowed for dynamic bodies).
     * Use only for respawn or heavy repositioning.
     */
    fun teleport(position: Vector2) {
        body.setTransform(position, body.angle)
        body.isAwake = true
    }

    /**
     * Convenience: stop the body instantly.
     */
    fun stop() {
        body.linearVelocity.set(0f, 0f)
        body.angularVelocity = 0f
    }
}
