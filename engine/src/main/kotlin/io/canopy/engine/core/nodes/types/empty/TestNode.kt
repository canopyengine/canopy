package io.canopy.engine.core.nodes.types.empty

import io.canopy.engine.core.nodes.Node

/** Test/utility Node with no Behavior **/
class TestNode(name: String, block: TestNode.() -> Unit = {}) : Node<TestNode>(name, block = block)
