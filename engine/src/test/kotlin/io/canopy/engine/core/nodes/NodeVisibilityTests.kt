package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.exceptions.CanopyException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.TextInputEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodeVisibilityTests {
    private class CountingNode(name: String) : Node<CountingNode>(name) {
        var updates by nodeProperty(0)
        var physics by nodeProperty(0)
        var inputs by nodeProperty(0)
        override fun onUpdate(delta: Float) {
            updates++
        }
        override fun onPhysicsUpdate(delta: Float) {
            physics++
        }
        override fun onInput(event: InputEvent) {
            inputs++
        }
    }

    private lateinit var scenes: SceneManager

    @BeforeEach
    fun setup() {
        scenes = SceneManager()
        ManagersRegistry.withScope { register(scenes) }
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `effective visibility follows reparenting and preserves child local state`() {
        // Arrange
        val root = EmptyNode("root") {
            EmptyNode("hidden")
            EmptyNode("visible")
        }
        scenes.currScene = root
        val hidden = root.getNode<EmptyNode>("hidden")
        val visible = root.getNode<EmptyNode>("visible")
        val child = EmptyNode("child")
        hidden.addChild(child)
        // Act
        hidden.hide()
        // Assert
        assertTrue(child.isVisible)
        assertFalse(child.isVisibleInTree)
        hidden.reparent(child, visible)
        assertTrue(child.isVisibleInTree)
        child.hide()
        visible.hide()
        visible.show()
        assertFalse(child.isVisibleInTree)
        child.show()
        assertTrue(child.isVisibleInTree)
        child.queueFree()
        scenes.onUpdate(0f)
        assertFailsWith<CanopyException> { child.isVisible }
        assertFailsWith<CanopyException> { child.show() }
    }

    @Test
    fun `hidden nodes still receive ordinary frame physics and input callbacks`() {
        // Arrange
        val node = CountingNode("root")
        scenes.currScene = node
        node.hide()
        // Act
        node.nodeUpdate(0.1f)
        node.nodePhysicsUpdate(0.1f)
        node.nodeInput(TextInputEvent("hidden gameplay"))
        // Assert
        assertEquals(1, node.updates)
        assertEquals(1, node.physics)
        assertEquals(1, node.inputs)
    }

    @Test
    fun `managed children reject imperative mutations but allow reconcile and destruction`() {
        // Arrange
        val root = EmptyNode("root") {
            EmptyNode("first")
            EmptyNode("second")
        }
        scenes.currScene = root
        val first = root.getNode<EmptyNode>("first")
        val second = root.getNode<EmptyNode>("second")
        val extra = EmptyNode("extra")
        root.markChildrenManaged()
        // Act and Assert
        assertFailsWith<CanopyException> { root.addChild(extra) }
        assertFailsWith<CanopyException> { root.removeChild(first) }
        assertFailsWith<CanopyException> { first.name = "renamed" }
        assertFailsWith<CanopyException> { first.queueFree() }
        assertFailsWith<CanopyException> { scenes.currScene = first }
        assertSame(root, scenes.currScene)
        assertFailsWith<CanopyException> { root.reparent(first, extra) }
        root.withManagedChildrenMutation {
            root.reorderManagedChildren(listOf(second, first))
            root.removeChild(first)
            root.addChild(extra)
        }
        assertEquals(listOf("second", "extra"), root.children.keys.toList())
        root.queueFree()
        scenes.onUpdate(0f)
        assertFalse(root.isValid)
        assertFalse(second.isValid)
    }
}
