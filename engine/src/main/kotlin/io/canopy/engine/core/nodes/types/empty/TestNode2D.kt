package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node2D

class TestNode2D(name: String, block: TestNode2D.() -> Unit = {}) : Node2D<TestNode2D>(name, block)
