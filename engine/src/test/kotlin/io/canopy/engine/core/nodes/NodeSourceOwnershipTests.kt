package io.canopy.engine.core.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.computed
import io.canopy.engine.core.flows.events.effect
import io.canopy.engine.core.flows.events.event
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodeSourceOwnershipTests {
    private lateinit var scenes: SceneManager

    private class StateNode : Node<StateNode>("child") {
        val count = signal(1)
        val shared = signal(owner = null, 2)
        val changed = event()
        val valueChanged = event<Int>()
        val pairChanged = event<Int, Int>()
        val derived = computed { count() * 2 }
        val observed = mutableListOf<Int>()
        val reaction = effect { observed += count() + shared() }
    }

    private class EntryNode(private val source: Signal<Int>, private val observed: MutableList<Int>) :
        Node<EntryNode>("entry") {
        override fun onEnterTree() {
            effect { observed += source() }
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
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test
    fun `a node's signal field is destroyed with that node rather than its construction parent`() {
        // Arrange
        lateinit var child: StateNode
        val parent = EmptyNode("parent") { child = StateNode() }
        scenes.currScene = parent
        val local = child.count
        val shared = child.shared
        val changed = child.changed
        val valueChanged = child.valueChanged
        val pairChanged = child.pairChanged
        val derived = child.derived
        val observed = child.observed
        assertEquals(2, derived())

        // Act
        child.queueFree()
        scenes.onUpdate(0f)

        // Assert
        assertFailsWith<NodeDestroyedException> { local() }
        assertFailsWith<NodeDestroyedException> { derived() }
        assertFailsWith<NodeDestroyedException> { changed.emit() }
        assertFailsWith<NodeDestroyedException> { valueChanged.emit(1) }
        assertFailsWith<NodeDestroyedException> { pairChanged.emit(1, 2) }
        assertEquals(2, shared())
        shared.update { 3 }
        assertEquals(listOf(3), observed)
        assertFailsWith<NodeDestroyedException> { child.signal(0) }
        assertFailsWith<NodeDestroyedException> { child.computed { 0 } }
        assertFailsWith<NodeDestroyedException> { child.effect {} }
        shared.dispose()
    }

    @Test
    fun `node signal state survives detachment and reentry while entry effects are renewed`() {
        // Arrange
        val source = signal(owner = null, 0)
        val observed = mutableListOf<Int>()
        lateinit var state: StateNode
        lateinit var entry: EntryNode
        val parent = EmptyNode("parent") {
            state = StateNode()
            entry = EntryNode(source, observed)
        }
        scenes.currScene = parent
        val count = state.count
        val shared = state.shared
        assertEquals(listOf(0), observed)

        // Act
        parent.removeChild(state)
        parent.removeChild(entry)
        count.update { 5 }
        source.update { 1 }

        // Assert
        assertEquals(5, count())
        assertEquals(listOf(0), observed)
        parent.addChild(state)
        parent.addChild(entry)
        assertEquals(5, state.count())
        assertEquals(listOf(0, 1), observed)
        source.update { 2 }
        assertEquals(listOf(0, 1, 2), observed)
        shared.dispose()
        source.dispose()
    }
}
