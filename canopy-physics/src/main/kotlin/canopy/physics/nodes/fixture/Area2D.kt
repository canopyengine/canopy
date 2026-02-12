package canopy.physics.nodes.fixture

import canopy.core.nodes.core.Behavior
import canopy.core.nodes.core.Node
import canopy.core.signals.createSignal
import canopy.physics.nodes.body.PhysicsBody2D
import canopy.physics.nodes.shape.PhysicsShape2D
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Filter
import com.badlogic.gdx.physics.box2d.Fixture

class Area2D(
    name: String,
    // Specific props
    var shape: PhysicsShape2D,
    val angle: Float = 0f,
    val filter: Filter = Filter(),
    // Base props
    script: (node: Area2D) -> Behavior<Area2D>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1f, 1f),
    rotation: Float = 0f,
    groups: MutableList<String> = mutableListOf(),
    // DSL
    block: Node<*>.() -> Unit = {},
) : Node<Area2D>(
        name,
        script,
        position,
        scale,
        rotation,
        groups,
        block,
    ) {
    private var fixture: Fixture? = null

    // Signals
    val bodyEntered = createSignal<Collider2D>()
    val bodyExited = createSignal<Collider2D>()
    val areaEntered = createSignal<Area2D>()
    val areaExited = createSignal<Area2D>()

    override fun enterTree() {
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
                true,
            )
    }

    override fun exitTree() {
        val parentBody = (parent as? PhysicsBody2D)?.body ?: return
        val fixtureSnapshot = fixture ?: return
        parentBody.destroyFixture(fixtureSnapshot)
    }
}
