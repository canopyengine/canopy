package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node

/** Extensible grouping node with no built-in behavior. */
open class EmptyNode(name: String, block: EmptyNode.() -> Unit = {}) : Node<EmptyNode>(name, block = block)
