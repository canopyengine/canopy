package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node

/**
 * Extensible grouping node with no built-in behavior.
 * @param N Concrete node type exposed to its initialization block.
 * @param block Runs once after nodeInit, before entry callbacks; not replayed on reentry.
 */
open class EmptyNode<N : EmptyNode<N>>(name: String, block: N.() -> Unit = {}) : Node<N>(name, block = block)
