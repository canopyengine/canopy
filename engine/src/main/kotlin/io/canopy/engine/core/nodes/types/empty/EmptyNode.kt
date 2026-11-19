package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node

/**
 * Extensible grouping node with no built-in behavior.
 * @param N Concrete node type exposed to its initialization block.
 * @param name Optional explicit name; otherwise the concrete class and its sibling count determine the name.
 * @param block Runs once after onSetup, before entry callbacks; not replayed on reentry.
 */
open class EmptyNode<N : EmptyNode<N>>(name: String? = null, block: N.() -> Unit = {}) : Node<N>(name, block = block)
