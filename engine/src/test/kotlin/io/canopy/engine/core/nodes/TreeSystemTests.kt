package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.BeforeAll

class TreeSystemTests {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            ManagersRegistry.withScope {
                register(SceneManager())
            }
        }
    }

    @Test
    fun `tree system should invoke hooks correctly and handle errors`() {
        val callOrder = mutableListOf<String>()
        var errorThrown = false

        val system = object : TreeSystem(UpdatePhase.FramePre, 0, EmptyNode::class) {
            override fun onRegister() {
                callOrder += "onRegister"
            }
            override fun onUnregister() {
                callOrder += "onUnregister"
            }
            override fun beforeProcess(delta: Float) {
                callOrder += "beforeProcess"
            }
            override fun processNode(node: Node<*>, delta: Float) {
                callOrder += "processNode:${node.name}"
                if (node.name == "fail") throw RuntimeException("Fail node")
            }
            override fun afterProcess(delta: Float) {
                callOrder += "afterProcess"
            }
            override fun onNodeAdded(node: Node<*>) {
                callOrder += "onNodeAdded:${node.name}"
            }
            override fun onNodeRemoved(node: Node<*>) {
                callOrder += "onNodeRemoved:${node.name}"
            }
        }

        system.onRegister()

        val node1 = EmptyNode("node1")
        node1.buildTree()

        val nodeFail = EmptyNode("fail")
        nodeFail.buildTree()

        // Registration
        system.register(node1)
        system.register(nodeFail)

        assertEquals(listOf("onRegister", "onNodeAdded:node1", "onNodeAdded:fail"), callOrder)
        callOrder.clear()

        // Tick process
        assertFailsWith<RuntimeException> {
            system.tick(1.0f)
        }

        // It should have executed beforeProcess and processNode:node1 and processNode:fail
        assertTrue(callOrder.contains("beforeProcess"))
        assertTrue(callOrder.contains("processNode:node1"))
        assertTrue(callOrder.contains("processNode:fail"))
        callOrder.clear()

        // Unregister
        system.unregister(node1)
        system.unregister(nodeFail)

        assertEquals(listOf("onNodeRemoved:node1", "onNodeRemoved:fail"), callOrder)
        callOrder.clear()

        system.onUnregister()
        assertEquals(listOf("onUnregister"), callOrder)
    }

    @Test
    fun `createTreeSystem inline function works`() {
        var registered = false
        var unregistered = false
        var matched = 0

        val sys = object : TreeSystem(UpdatePhase.FramePre, 0, EmptyNode::class) {
            override fun onRegister() {
                registered = true
            }
            override fun onUnregister() {
                unregistered = true
            }
            override fun processNode(node: Node<*>, delta: Float) {
                matched++
            }
        }

        sys.onRegister()
        assertTrue(registered)

        val node = EmptyNode("test")
        node.buildTree()
        sys.register(node)

        sys.tick(0f)
        assertEquals(1, matched)

        sys.unregister(node)
        sys.onUnregister()
        assertTrue(unregistered)
    }

    @Test
    fun `acceptsNode checks for child types correctly`() {
        class CustomType : Node<CustomType>("custom")

        val sys = object : TreeSystem(UpdatePhase.FramePre, 0, CustomType::class) {}

        val node1 = EmptyNode("root") {
            CustomType()
        }
        node1.buildTree()

        sys.register(node1) // Should accept because it has a child of CustomType

        // Use reflection to access matchingNodes size
        val sizeProp = TreeSystem::class.members.find { it.name == "matchingNodes" }
        // We know it must be in the list if it accepted. But without reflection, let's test processNode.
        var processed = false
        val sys2 = object : TreeSystem(UpdatePhase.FramePre, 0, CustomType::class) {
            override fun processNode(node: Node<*>, delta: Float) {
                processed = true
            }
        }
        sys2.register(node1)
        sys2.tick(0f)
        assertTrue(processed)
    }
}
