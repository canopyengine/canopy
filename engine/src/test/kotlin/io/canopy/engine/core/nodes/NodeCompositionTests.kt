package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.exceptions.InvalidNodeOperationException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodeCompositionTests {
    private lateinit var scenes: SceneManager
    private class Child(name: String, private val trace: MutableList<String>) : Node<Child>(name) {
        override fun onEnterTree() {
            trace += "enter:$name"
        }
        override fun onReady() {
            trace += "ready:$name"
        }
    }
    private class Parent(private val trace: MutableList<String>) : Node<Parent>("parent") {
        override fun nodeInit() {
            trace += "init:start"
            addChild(Child("initial", trace))
            trace += "init:end"
        }
        override fun onEnterTree() {
            trace += "enter:parent"
            if (getNodeOrNull<Child>("entered") == null) addChild(Child("entered", trace))
        }
        override fun onReady() {
            trace += "ready:parent"
        }
    }

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `explicit composition finishes configuration before child entry and readies once per entry`() {
        // Arrange
        val trace = mutableListOf<String>()
        val parent = Parent(trace)
        val root = EmptyNode("root")
        root.addChild(parent)

        // Act
        scenes.currScene = root

        // Assert
        assertEquals(
            listOf(
                "init:start",
                "init:end",
                "enter:parent",
                "enter:entered",
                "ready:entered",
                "enter:initial",
                "ready:initial",
                "ready:parent"
            ),
            trace
        )
        trace.clear()
        root.removeChild(parent)
        root.addChild(parent)
        assertEquals(
            listOf(
                "enter:parent",
                "enter:initial",
                "enter:entered",
                "ready:initial",
                "ready:entered",
                "ready:parent"
            ),
            trace
        )
        root.addChild(parent)
        assertEquals(6, trace.size)
        assertFailsWith<InvalidNodeOperationException> { EmptyNode("other").addChild(parent) }
    }
}
