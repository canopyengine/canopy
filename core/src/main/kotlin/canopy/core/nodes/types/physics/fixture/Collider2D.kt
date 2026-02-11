package anchors.framework.nodes.types.physics.fixture

import anchors.framework.nodes.core.Behavior
import anchors.framework.nodes.core.Node
import anchors.framework.nodes.types.physics.body.PhysicsBody2D
import anchors.framework.nodes.types.physics.shape.PhysicsShape2D
import anchors.framework.signals.createSignal
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Filter
import com.badlogic.gdx.physics.box2d.Fixture

class Collider2D(
    name: String,
    // Specific props
    var shape: PhysicsShape2D,
    val angle: Float = 0F,
    var friction: Float = 0.2f,
    var restitution: Float = 0.0f,
    var density: Float = 0.0f,
    val filter: Filter = Filter(),
    // Base props
    script: (node: Collider2D) -> Behavior<Collider2D>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1F, 1F),
    rotation: Float = 0F,
    groups: MutableList<String> = mutableListOf(),
    // DSL
    block: Node<*>.() -> Unit = {},
) : Node<Collider2D>(
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

    override fun enterTree() {
        val parentBody = (parent as? PhysicsBody2D)?.body ?: return
        fixture =
            shape.shapeFactory(
                this,
                parentBody,
                position,
                angle,
                friction,
                restitution,
                density,
                filter,
                false,
            )
    }

    override fun exitTree() {
        val parentBody = (parent as? PhysicsBody2D)?.body ?: return
        val fixtureSnapshot = fixture ?: return
        parentBody.destroyFixture(fixtureSnapshot)
    }
}
