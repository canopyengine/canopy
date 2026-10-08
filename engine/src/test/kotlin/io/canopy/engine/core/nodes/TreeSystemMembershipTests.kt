package io.canopy.engine.core.nodes

import kotlin.reflect.KClass
import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class TreeSystemMembershipTests {
    private lateinit var scenes: SceneManager
    private open class MatchingNode(name: String) : Node<MatchingNode>(name)
    private class DerivedNode(name: String) : MatchingNode(name)
    private class RecordingSystem(vararg types: KClass<out Node<*>>) :
        TreeSystem(UpdatePhase.FramePre, 0, *types) {
        val nodes get() = matchingNodes.toList()
        val added = mutableListOf<Node<*>>()
        val removed = mutableListOf<Node<*>>()
        override fun onNodeAdded(node: Node<*>) {
            added += node
        }
        override fun onNodeRemoved(node: Node<*>) {
            removed += node
        }
    }

    @BeforeEach
    fun setup() {
        ManagersRegistry.exit()
        scenes = SceneManager()
        ManagersRegistry.register(scenes)
        scenes.onEnter()
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `manual registration rejects a nonmatching parent with matching child`() {
        val parent = EmptyNode("parent")
        val child = MatchingNode("child")
        parent.addChild(child)
        val system = RecordingSystem(MatchingNode::class)

        system.register(parent)

        assertTrue(system.nodes.isEmpty())
        assertTrue(system.added.isEmpty())
        system.register(child)
        assertEquals(listOf<Node<*>>(child), system.nodes)
    }

    @Test
    fun `direct and inherited types match once across overlapping OR requirements`() {
        val direct = MatchingNode("direct")
        val inherited = DerivedNode("inherited")
        val system = RecordingSystem(MatchingNode::class, DerivedNode::class)
        system.register(direct)
        system.register(inherited)
        system.register(inherited)
        system.register(EmptyNode("other"))
        val baseOnly = RecordingSystem(MatchingNode::class)
        baseOnly.register(inherited)
        assertEquals(listOf<Node<*>>(inherited), baseOnly.nodes)
        assertEquals(listOf<Node<*>>(direct, inherited), system.nodes)
        assertEquals(listOf<Node<*>>(direct, inherited), system.added)
        val noTypes = RecordingSystem()
        noTypes.register(direct)
        assertTrue(noTypes.nodes.isEmpty())
    }

    @Test
    fun `late backfill and child attach detach retain only matching nodes without duplicates`() {
        val parent = EmptyNode("parent")
        val first = DerivedNode("first")
        parent.addChild(first)
        scenes.currScene = parent
        val system = RecordingSystem(MatchingNode::class)

        scenes.addSystem(system)
        system.register(parent)
        system.register(first)
        val later = MatchingNode("later")
        parent.addChild(later)
        assertEquals(listOf<Node<*>>(first, later), system.nodes)
        assertEquals(listOf<Node<*>>(first, later), system.added)
        parent.removeChild(first)
        assertEquals(listOf<Node<*>>(later), system.nodes)
        assertEquals(listOf<Node<*>>(first), system.removed)
        parent.addChild(first)
        assertEquals(listOf<Node<*>>(later, first), system.nodes)
        assertEquals(listOf<Node<*>>(first, later, first), system.added)
        assertFalse(system.nodes.any { it === parent })
    }
}
