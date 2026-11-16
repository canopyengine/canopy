package io.canopy.engine.core.managers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.canopy.engine.core.flows.events.effect
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class SceneGroupsTests {
    private lateinit var scenes: SceneManager

    @BeforeEach
    fun setup() {
        ManagersRegistry.exit()
        scenes = SceneManager()
        ManagersRegistry.register(scenes)
        scenes.onEnter()
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test
    fun `unchanged group updates do not notify reactive readers`() {
        val root = EmptyNode("root")
        scenes.currScene = root
        var evaluations = 0
        val observer = effect {
            root.groups
            evaluations++
        }

        assertEquals(1, evaluations)
        root.updateGroups { }
        assertEquals(1, evaluations)
        scenes.updateGroups(root)
        assertEquals(1, evaluations)
        root.updateGroups { add("members") }
        assertEquals(2, evaluations)
        root.updateGroups { add("members") }
        assertEquals(2, evaluations)
        observer.dispose()
    }

    @Test
    fun `group updates preserve existing order and signal snapshot skips removed members`() {
        val root = EmptyNode("root")
        val first = EmptyNode("first")
        val second = EmptyNode("second")
        val third = EmptyNode("third")
        root.addChild(first)
        root.addChild(second)
        root.addChild(third)
        scenes.currScene = root
        first.addGroup("party")
        second.addGroup("party")

        first.updateGroups { add("other") }
        assertEquals(listOf(first, second), scenes.queryGroup("party"))
        first.updateGroups { }
        assertEquals(listOf(first, second), scenes.queryGroup("party"))
        first.removeGroup("party")
        first.addGroup("party")
        assertEquals(listOf(second, first), scenes.queryGroup("party"))

        val calls = mutableListOf<Node<*>>()
        scenes.signalGroup("party") { node ->
            calls += node
            if (node === second) {
                first.removeGroup("party")
                third.addGroup("party")
            }
        }
        assertEquals(listOf<Node<*>>(second), calls)
        calls.clear()
        scenes.signalGroup("party") { calls += it }
        assertEquals(listOf<Node<*>>(second, third), calls)
    }

    @Test
    fun `direct group membership remains separate and update reconciles it`() {
        val root = EmptyNode("root")
        scenes.currScene = root

        scenes.addToGroup("direct", root)
        assertTrue(root.groups.isEmpty())
        assertEquals(listOf(root), scenes.queryGroup("direct"))
        root.updateGroups { }
        assertTrue(scenes.queryGroup("direct").isEmpty())
        assertFailsWith<IllegalStateException> { scenes.signalGroup("direct") {} }
    }

    @Test
    fun `removing the last group member removes its group key`() {
        val root = EmptyNode("root")
        scenes.currScene = root

        root.addGroup("direct-removal")
        root.removeGroup("direct-removal")
        assertFailsWith<IllegalStateException> { scenes.signalGroup("direct-removal") {} }

        root.addGroup("bulk-removal")
        root.updateGroups { remove("bulk-removal") }
        assertFailsWith<IllegalStateException> { scenes.signalGroup("bulk-removal") {} }

        scenes.addToGroup("manager-removal", root)
        scenes.removeFromGroup("manager-removal", root)
        assertFailsWith<IllegalStateException> { scenes.signalGroup("manager-removal") {} }
    }
}
