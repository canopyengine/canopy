package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node2D

/** A transform-bearing node for grouping children without additional behavior. */
class EmptyNode2D(name: String, block: EmptyNode2D.() -> Unit = {}) : Node2D<EmptyNode2D>(name, block)
