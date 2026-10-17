package io.canopy.platforms.desktop.physics.nodes.fixture

import com.badlogic.gdx.physics.box2d.Filter
import com.badlogic.gdx.physics.box2d.Fixture
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.reactive.event
import io.canopy.engine.physics.nodes.body.PhysicsBody2D
import io.canopy.engine.physics.nodes.shape.PhysicsShape2D

/**
 * Extensible node that creates a sensor fixture on its parent physics body and emits contact events.
 * @param N Concrete node type exposed to its initialization block.
 * @param block Runs once after nodeInit, before entry callbacks; not replayed on reentry.
 */
open class Area2D<N : Area2D<N>>(
    name: String,
    // Specific props
    var shape: PhysicsShape2D,
    val angle: Float = 0f,
    val filter: Filter = Filter(),
    // DSL
    block: N.() -> Unit = {},
) : Node<N>(
    name,
    block
) {
    private var fixture: Fixture? = null

    // Signals
    val bodyEntered = event<Collider2D<*>>()
    val bodyExited = event<Collider2D<*>>()
    val areaEntered = event<Area2D<*>>()
    val areaExited = event<Area2D<*>>()

    override fun nodeEnterTree() {
        val parentBody = (parent as? PhysicsBody2D)?.body ?: return
        fixture =
            shape.shapeFactory(
                this,
                parentBody,
                position,
                angle,
                0f,
                0f,
                0f,
                filter,
                true
            )
    }

    override fun nodeExitTree() {
        val parentBody = (parent as? PhysicsBody2D)?.body ?: return
        val fixtureSnapshot = fixture ?: return
        parentBody.destroyFixture(fixtureSnapshot)
    }
}
