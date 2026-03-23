package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node

/** Empty Node with no Behavior **/
class EmptyNode(name: String, block: EmptyNode.() -> Unit = {}) : Node<EmptyNode>(name, block = block)
