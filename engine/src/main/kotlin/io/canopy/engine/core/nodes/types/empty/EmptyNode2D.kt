package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node2D

/**
 * An extensible transform-bearing node for grouping children without additional behavior.
 * @param N Concrete node type exposed to its initialization block.
 * @param name Optional explicit name; otherwise the concrete class and its sibling count determine the name.
 * @param block Runs once after onSetup, before entry callbacks; not replayed on reentry.
 */
open class EmptyNode2D<N : EmptyNode2D<N>>(name: String? = null, block: N.() -> Unit = {}) : Node2D<N>(name, block)
