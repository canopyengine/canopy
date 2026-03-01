package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.core.Node

class EmptyNode(name: String, block: EmptyNode.() -> Unit = {}) : Node<EmptyNode>(name, block)
