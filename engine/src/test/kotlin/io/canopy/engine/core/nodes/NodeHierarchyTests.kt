package io.canopy.engine.core.nodes

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import io.canopy.engine.core.exceptions.InvalidNodeOperationException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NodeHierarchyTests {
    private lateinit var scenes: SceneManager

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test
    fun `rename preserves siblings on collision and refreshes descendant paths`() {
        val root = EmptyNode("root") {
            EmptyNode("parent") {
                EmptyNode("branch") { EmptyNode("leaf") }
                EmptyNode("taken")
            }
        }
        scenes.currScene = root
        val parent = root.getNode<EmptyNode<*>>("parent")
        val branch = parent.getNode<EmptyNode<*>>("branch")
        val leaf = branch.getNode<EmptyNode<*>>("leaf")

        assertFailsWith<InvalidNodeOperationException> { branch.name = "taken" }
        assertEquals("branch", branch.name)
        assertEquals(listOf("branch", "taken"), parent.children.keys.toList())

        branch.name = "renamed"

        assertEquals("/root/parent/renamed/leaf", leaf.path)
        assertSame(branch, parent.getNode<EmptyNode<*>>("renamed"))
        assertNull(parent.getNodeOrNull<EmptyNode<*>>("branch"))
        assertEquals(listOf("taken", "renamed"), parent.children.keys.toList())
    }

    @Test
    fun `reparent rejects collisions and cycles without changing the tree`() {
        val root = EmptyNode("root") {
            EmptyNode("first") {
                EmptyNode("branch") { EmptyNode("leaf") }
            }
            EmptyNode("second") { EmptyNode("branch") }
            EmptyNode("third")
        }
        scenes.currScene = root
        val first = root.getNode<EmptyNode<*>>("first")
        val second = root.getNode<EmptyNode<*>>("second")
        val third = root.getNode<EmptyNode<*>>("third")
        val branch = first.getNode<EmptyNode<*>>("branch")
        val leaf = branch.getNode<EmptyNode<*>>("leaf")

        assertFailsWith<InvalidNodeOperationException> { first.reparent(branch, second) }
        assertFailsWith<InvalidNodeOperationException> { root.reparent(first, branch) }
        assertSame(first, branch.parent)
        assertSame(root, first.parent)
        assertEquals("/root/first/branch/leaf", leaf.path)
        assertSame(branch, first.getNode<EmptyNode<*>>("branch"))

        first.reparent(branch, third)

        assertSame(third, branch.parent)
        assertEquals("/root/third/branch/leaf", leaf.path)
        assertNull(first.getNodeOrNull<EmptyNode<*>>("branch"))
        assertSame(branch, third.getNode<EmptyNode<*>>("branch"))
    }
}
