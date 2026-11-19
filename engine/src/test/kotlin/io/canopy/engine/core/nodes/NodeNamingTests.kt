package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.exceptions.InvalidNodeOperationException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodeNamingTests {
    private class Fish(name: String? = null) : Node<Fish>(name)
    private class Other(name: String? = null) : Node<Other>(name)

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope { register(SceneManager()) }
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `unnamed nodes use their concrete class and sibling type count`() {
        val root = EmptyNode()

        val first = Fish()
        val second = Fish()
        val third = Fish()
        root.addChild(first)
        root.addChild(second)
        root.addChild(third)

        assertEquals("EmptyNode", root.name)
        assertEquals("/EmptyNode", root.path)
        assertEquals(listOf("Fish", "Fish1", "Fish2"), root.children.keys.toList())
        assertEquals("/EmptyNode/Fish2", third.path)
        assertSame(second, root.getNode<Fish>("Fish1"))
    }

    @Test
    fun `builder-created children receive automatic names during construction`() {
        val root = EmptyNode("root") {
            Fish()
            Fish()
        }

        root.buildTree()

        assertEquals(listOf("Fish", "Fish1"), root.children.keys.toList())
    }

    @Test
    fun `explicit siblings count toward numbering and existing names are skipped`() {
        val root = EmptyNode("root")
        val named = Fish("custom")
        val firstAutomatic = Fish()
        val other = Other("Fish2")
        val secondAutomatic = Fish()
        root.addChild(named)
        root.addChild(firstAutomatic)
        root.addChild(other)
        root.addChild(secondAutomatic)

        assertEquals("custom", named.name)
        assertEquals("Fish1", firstAutomatic.name)
        assertEquals("Fish3", secondAutomatic.name)
        assertSame(other, root.children["Fish2"])
    }

    @Test
    fun `removal gaps do not cause duplicate automatic names`() {
        val root = EmptyNode("root")
        val first = Fish()
        val middle = Fish()
        val last = Fish()
        root.addChild(first)
        root.addChild(middle)
        root.addChild(last)

        root.removeChild(middle)
        val added = Fish()
        root.addChild(added)

        assertEquals("Fish3", added.name)
        assertEquals(listOf("Fish", "Fish2", "Fish3"), root.children.keys.toList())
    }

    @Test
    fun `reparenting renumbers automatic names in the destination`() {
        val root = EmptyNode("root")
        val source = EmptyNode("source")
        val destination = EmptyNode("destination")
        val moving = Fish()
        root.addChild(source)
        root.addChild(destination)
        source.addChild(moving)
        destination.addChild(Fish())
        destination.addChild(Fish())
        root.buildTree()

        source.reparent(moving, destination)

        assertEquals("Fish2", moving.name)
        assertEquals("/root/destination/Fish2", moving.path)
        assertTrue(source.children.isEmpty())
        assertSame(moving, destination.children["Fish2"])
    }

    @Test
    fun `explicit rename freezes an automatic name and duplicate explicit names still fail`() {
        val root = EmptyNode("root")
        val source = EmptyNode("source")
        val destination = EmptyNode("destination")
        val moving = Fish()
        root.addChild(source)
        root.addChild(destination)
        source.addChild(moving)
        destination.addChild(Fish("Fish"))
        moving.name = moving.name

        assertFailsWith<InvalidNodeOperationException> { source.reparent(moving, destination) }
        assertFailsWith<InvalidNodeOperationException> { source.addChild(Fish("Fish")) }
        assertSame(source, moving.parent)
        assertEquals("/root/source/Fish", moving.path)
    }
}
