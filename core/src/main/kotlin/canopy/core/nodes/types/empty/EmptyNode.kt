package anchors.framework.nodes.types.empty

import anchors.framework.nodes.core.Behavior
import anchors.framework.nodes.core.Node
import com.badlogic.gdx.math.Vector2

class EmptyNode(
    name: String,
    script: (node: EmptyNode) -> Behavior<EmptyNode>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1f, 1f),
    rotation: Float = 0f,
    groups: MutableList<String> = mutableListOf(),
    block: EmptyNode.() -> Unit = {},
) : Node<EmptyNode>(
        name,
        script,
        position,
        scale,
        rotation,
        groups,
        block,
    )
