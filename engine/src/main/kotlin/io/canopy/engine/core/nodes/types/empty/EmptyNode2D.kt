package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node2D

class EmptyNode2D(name: String, block: EmptyNode2D.() -> Unit = {}) : Node2D<EmptyNode2D>(name, block)
