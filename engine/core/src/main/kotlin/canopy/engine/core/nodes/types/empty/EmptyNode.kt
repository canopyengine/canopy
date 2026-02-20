package canopy.engine.core.nodes.types.empty

import canopy.engine.core.nodes.core.Behavior
import canopy.engine.core.nodes.core.Node
import com.badlogic.gdx.math.Vector2

class EmptyNode(
    name: String,
    behavior: (node: EmptyNode) -> Behavior<EmptyNode>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1f, 1f),
    rotation: Float = 0f,
    groups: MutableList<String> = mutableListOf(),
    block: EmptyNode.() -> Unit = {},
) : Node<EmptyNode>(
    name,
    behavior,
    position,
    scale,
    rotation,
    groups,
    block
)
