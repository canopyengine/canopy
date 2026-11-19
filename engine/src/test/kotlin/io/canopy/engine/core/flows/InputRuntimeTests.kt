package io.canopy.engine.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import io.canopy.engine.core.flows.events.Computed
import io.canopy.engine.core.flows.events.InputRuntime
import io.canopy.engine.core.flows.events.computed
import io.canopy.engine.core.flows.events.connect
import io.canopy.engine.core.flows.events.effect
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class InputRuntimeTests {
    private lateinit var scenes: SceneManager

    private class InputNode(initial: Int, onConstruction: () -> Unit = {}) : Node<InputNode>("input") {
        init {
            compilerPropertySet("InputNode:amount", initial)
            onConstruction()
        }

        val amount: Int get() = compilerInputGet("InputNode:amount", 0)
    }

    private class CollectionInputNode(initial: List<Int>) : Node<CollectionInputNode>("collection input") {
        init {
            compilerPropertySet("CollectionInputNode:items", initial)
        }

        val items: List<Int> get() = compilerInputGet("CollectionInputNode:items", emptyList())
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
    fun `capture evaluates once without subscribing and resynchronizes before entry reads`() {
        val source = signal(owner = null, value = 1)
        var evaluations = 0
        val capture = InputRuntime.capture {
            evaluations++
            source() * 2
        }
        val receiver = InputNode(capture.initialValue)

        assertEquals(1, evaluations)
        source.update { 2 }
        assertEquals(1, evaluations)
        InputRuntime.install(receiver, "InputNode:amount", capture)
        assertEquals(4, receiver.amount)
        assertEquals(2, evaluations)

        scenes.currScene = receiver

        assertEquals(4, receiver.amount)
        assertEquals(2, evaluations)
        source.update { 3 }
        assertEquals(6, receiver.amount)
        assertEquals(3, evaluations)
    }

    @Test
    fun `construction mutation resynchronizes preentry reads without subscribing`() {
        val source = signal(owner = null, value = 1)
        var evaluations = 0
        val capture = InputRuntime.capture {
            evaluations++
            source()
        }
        val receiver = InputNode(capture.initialValue) { source.update { 2 } }
        InputRuntime.install(receiver, "InputNode:amount", capture)

        assertEquals(2, receiver.amount)
        assertEquals(2, evaluations)
        source.update { 3 }
        assertEquals(2, evaluations)
        assertEquals(3, receiver.amount)
        assertEquals(3, evaluations)

        scenes.currScene = receiver
        assertEquals(3, receiver.amount)
        assertEquals(3, evaluations)
        source.update { 4 }
        assertEquals(4, receiver.amount)
        assertEquals(4, evaluations)
    }

    @Test
    fun `capture records source revision before an initial self write`() {
        val source = signal(owner = null, value = 0)
        var evaluations = 0
        val capture = InputRuntime.capture {
            val value = source()
            evaluations++
            if (value == 0) source.update { 1 }
            value
        }
        assertEquals(0, capture.initialValue)
        assertEquals(1, evaluations)
        val receiver = InputNode(capture.initialValue)
        InputRuntime.install(receiver, "InputNode:amount", capture)

        assertEquals(1, receiver.amount)
        assertEquals(2, evaluations)
        scenes.currScene = receiver
        assertEquals(1, receiver.amount)
        source.update { 2 }
        assertEquals(2, receiver.amount)
    }

    @Test
    fun `duplicate installation is rejected without replacing the original input`() {
        val source = signal(owner = null, value = 1)
        val capture = InputRuntime.capture { source() }
        val receiver = InputNode(capture.initialValue)
        InputRuntime.install(receiver, "InputNode:amount", capture)
        val activityBefore = receiver.state("inspect activity").activity.size
        val destructionBefore = receiver.state("inspect destruction").destruction.size

        assertFailsWith<IllegalStateException> {
            InputRuntime.install(receiver, "InputNode:amount", capture)
        }
        assertEquals(activityBefore, receiver.state("inspect activity").activity.size)
        assertEquals(destructionBefore, receiver.state("inspect destruction").destruction.size)
        scenes.currScene = receiver
        source.update { 2 }
        assertEquals(2, receiver.amount)
    }

    @Test
    fun `static input retains its construction value without an upstream rerun`() {
        var evaluations = 0
        val capture = InputRuntime.capture { ++evaluations }
        val receiver = InputNode(capture.initialValue)
        InputRuntime.install(receiver, "InputNode:amount", capture)

        assertEquals(1, receiver.amount)
        scenes.currScene = receiver

        assertEquals(1, receiver.amount)
        assertEquals(1, evaluations)
        receiver.nodeExitTree()
        assertEquals(1, receiver.amount)
        assertEquals(1, evaluations)
        receiver.buildTree()
        assertEquals(1, receiver.amount)
        assertEquals(1, evaluations)
    }

    @Test
    fun `capture rejects an exited source instead of freezing its value`() {
        val sourceOwner = EmptyNode("source")
        val source = signal(owner = sourceOwner, value = 1)
        scenes.currScene = sourceOwner
        sourceOwner.nodeExitTree()

        assertFailsWith<IllegalStateException> { InputRuntime.capture { source() } }
        assertFailsWith<IllegalStateException> { InputRuntime.capture { untrack { source() } } }
    }

    @Test
    fun `capture rejects an exited computed even when its value has no tracked sources`() {
        val sourceOwner = EmptyNode("source")
        val selected = computed(owner = sourceOwner) { 7 }
        scenes.currScene = sourceOwner
        sourceOwner.nodeExitTree()

        assertEquals(7, selected())
        assertFailsWith<IllegalStateException> { InputRuntime.capture { selected() } }
        assertFailsWith<IllegalStateException> { InputRuntime.capture { untrack { selected() } } }
        selected.dispose()
    }

    @Test
    fun `failed install on active owner releases lifecycle hooks and keeps raw value`() {
        val root = EmptyNode("root")
        val sourceOwner = EmptyNode("source")
        val source = signal(owner = sourceOwner, value = 1)
        val capture = InputRuntime.capture { source() }
        val receiver = InputNode(capture.initialValue)
        root.addChild(sourceOwner)
        root.addChild(receiver)
        scenes.currScene = root
        sourceOwner.nodeExitTree()
        val activityBefore = receiver.state("inspect activity").activity.size
        val destructionBefore = receiver.state("inspect destruction").destruction.size

        assertFailsWith<IllegalStateException> {
            InputRuntime.install(receiver, "InputNode:amount", capture)
        }
        assertEquals(activityBefore, receiver.state("inspect activity").activity.size)
        assertEquals(destructionBefore, receiver.state("inspect destruction").destruction.size)
        assertEquals(1, receiver.amount)

        sourceOwner.buildTree()
        InputRuntime.install(receiver, "InputNode:amount", capture)
        source.update { 2 }
        assertEquals(2, receiver.amount)
    }

    @Test
    fun `source clear rearms on read and source exit fails until reentry`() {
        val root = EmptyNode("root")
        val sourceOwner = EmptyNode("source")
        val source = signal(owner = sourceOwner, value = 1)
        val capture = InputRuntime.capture { source() }
        val receiver = InputNode(capture.initialValue)
        root.addChild(sourceOwner)
        root.addChild(receiver)
        InputRuntime.install(receiver, "InputNode:amount", capture)
        scenes.currScene = root
        assertEquals(1, receiver.amount)

        source.clear()
        assertEquals(1, receiver.amount)
        source.update { 2 }
        assertEquals(2, receiver.amount)

        sourceOwner.nodeExitTree()
        assertFailsWith<IllegalStateException> { receiver.amount }
        sourceOwner.buildTree()
        assertEquals(2, receiver.amount)
        source.update { 3 }
        assertEquals(3, receiver.amount)
    }

    @Test
    fun `inactive source can leave a conditional computed branch without blocking reevaluation`() {
        val root = EmptyNode("root")
        val sourceOwner = EmptyNode("source")
        root.addChild(sourceOwner)
        scenes.currScene = root
        val source = signal(owner = sourceOwner, value = 7)
        val enabled = signal(owner = null, value = true)
        val selected = computed(owner = null) { if (enabled()) source() else 0 }
        assertEquals(7, selected())

        sourceOwner.nodeExitTree()
        enabled.update { false }

        assertEquals(0, selected())
        selected.dispose()
    }

    @Test
    fun `receiver detachment suspends observation and reentry restores latest value`() {
        val source = signal(owner = null, value = 1)
        val capture = InputRuntime.capture { source() }
        val receiver = InputNode(capture.initialValue)
        InputRuntime.install(receiver, "InputNode:amount", capture)
        scenes.currScene = receiver
        assertEquals(1, receiver.amount)

        receiver.nodeExitTree()
        source.update { 2 }
        assertEquals(2, receiver.amount)
        receiver.buildTree()

        assertEquals(2, receiver.amount)
        source.update { 3 }
        assertEquals(3, receiver.amount)
    }

    @Test
    fun `input reentry waits until a later source sibling has entered`() {
        val root = EmptyNode("root")
        val sourceOwner = EmptyNode("source")
        val source = signal(owner = sourceOwner, value = 1)
        val capture = InputRuntime.capture { source() }
        val receiver = InputNode(capture.initialValue)
        InputRuntime.install(receiver, "InputNode:amount", capture)
        root.addChild(receiver)
        root.addChild(sourceOwner)
        scenes.currScene = root
        assertEquals(1, receiver.amount)

        root.nodeExitTree()
        source.update { 2 }
        root.buildTree()

        assertEquals(2, receiver.amount)
        source.update { 3 }
        assertEquals(3, receiver.amount)
    }

    @Test
    fun `ordinary computed read before first entry resynchronizes and subscribes on entry`() {
        val source = signal(owner = null, value = 1)
        val owner = EmptyNode("owner")
        val result = computed(owner = owner) { source() * 2 }
        assertEquals(2, result())
        source.update { 2 }

        scenes.currScene = owner
        assertEquals(4, result())
        source.update { 3 }
        assertEquals(6, result())
        result.dispose()
        source.dispose()
    }

    @Test
    fun `ordinary computed remains lazy after scene exit and reentry`() {
        val source = signal(owner = null, value = 1)
        val owner = EmptyNode("owner")
        var evaluations = 0
        val result = computed(owner = owner) {
            evaluations++
            source()
        }
        scenes.currScene = owner
        assertEquals(0, evaluations)
        assertEquals(1, result())
        owner.nodeExitTree()
        source.update { 2 }

        owner.buildTree()
        assertEquals(1, evaluations)
        assertEquals(2, result())
        assertEquals(2, evaluations)
        result.dispose()
        source.dispose()
    }

    @Test
    fun `computed direct cycles fail deterministically`() {
        lateinit var result: Computed<Int>
        result = computed(owner = null) { result() + 1 }

        assertFailsWith<IllegalStateException> { result() }
        result.dispose()
    }

    @Test
    fun `detached computed cycles fail deterministically`() {
        val owner = EmptyNode("owner")
        lateinit var result: Computed<Int>
        result = computed(owner = owner) { result() + 1 }
        scenes.currScene = owner
        owner.nodeExitTree()

        assertFailsWith<IllegalStateException> { result() }
        result.dispose()
    }

    @Test
    fun `mutual computed cycles fail deterministically`() {
        lateinit var first: Computed<Int>
        lateinit var second: Computed<Int>
        first = computed(owner = null) { second() + 1 }
        second = computed(owner = null) { first() + 1 }

        assertFailsWith<IllegalStateException> { first() }
        first.dispose()
        second.dispose()
    }

    @Test
    fun `computed reentrant invalidation reruns to the latest source value`() {
        val source = signal(owner = null, value = 0)
        var evaluations = 0
        val result = computed(owner = null) {
            evaluations++
            source().also { current -> if (current == 1) source.update { 2 } }
        }
        assertEquals(0, result())

        source.update { 1 }

        assertEquals(2, result())
        assertEquals(3, evaluations)
        result.dispose()
    }

    @Test
    fun `failed computed reevaluation retries without publishing a failed value`() {
        val source = signal(owner = null, value = 1)
        var fail = false
        val result = computed(owner = null) {
            val value = source()
            if (fail) throw IllegalStateException("calculation failed")
            value * 2
        }
        val values = mutableListOf<Int>()
        val connection = result connect { values += it }
        assertEquals(2, result())

        fail = true
        assertFailsWith<IllegalStateException> { source.update { 2 } }
        assertEquals(emptyList<Int>(), values)
        assertFailsWith<IllegalStateException> { result() }
        fail = false

        assertEquals(4, result())
        assertEquals(listOf(4), values)
        connection.disconnect()
        result.dispose()
    }

    @Test
    fun `equal derived inputs do not rerun tracked readers`() {
        val source = signal(owner = null, value = 1)
        val capture = InputRuntime.capture { source() % 2 }
        val receiver = InputNode(capture.initialValue)
        InputRuntime.install(receiver, "InputNode:amount", capture)
        scenes.currScene = receiver
        var observations = 0
        val observer = effect {
            receiver.amount
            observations++
        }

        source.update { 3 }
        assertEquals(1, observations)
        source.update { 4 }
        assertEquals(2, observations)
        observer.dispose()
    }

    @Test
    fun `observable collection input edits reach property connections and downstream computed`() {
        val source = signal(owner = null, value = mutableListOf(1))
        val capture = InputRuntime.capture<List<Int>> { source() }
        val receiver = CollectionInputNode(capture.initialValue)
        InputRuntime.install(receiver, "CollectionInputNode:items", capture)
        scenes.currScene = receiver
        val forwarded = computed(owner = null) { receiver.items }
        val propertyValues = mutableListOf<List<Int>>()
        val forwardedValues = mutableListOf<List<Int>>()
        val propertyConnection = (receiver::items).connect(owner = null) { propertyValues += it.toList() }
        val forwardedConnection = forwarded.connect(owner = null) { forwardedValues += it.toList() }

        source().add(2)

        assertSame(receiver, scenes.currScene)
        assertEquals(listOf(listOf(1, 2)), propertyValues)
        assertEquals(listOf(listOf(1, 2)), forwardedValues)
        propertyConnection.disconnect()
        forwardedConnection.disconnect()
        forwarded.dispose()
        source.dispose()
    }

    @Test
    fun `equal observable collection branch switch adopts the new source across input observers`() {
        val chooseSecond = signal(owner = null, value = false)
        val first = signal(owner = null, value = mutableListOf(1))
        val second = signal(owner = null, value = mutableListOf(1))
        val capture = InputRuntime.capture<List<Int>> { if (chooseSecond()) second() else first() }
        val receiver = CollectionInputNode(capture.initialValue)
        InputRuntime.install(receiver, "CollectionInputNode:items", capture)
        scenes.currScene = receiver
        val forwarded = computed(owner = null) { receiver.items }
        val propertyValues = mutableListOf<List<Int>>()
        val forwardedValues = mutableListOf<List<Int>>()
        val propertyConnection = (receiver::items).connect(owner = null) { propertyValues += it.toList() }
        val forwardedConnection = forwarded.connect(owner = null) { forwardedValues += it.toList() }

        chooseSecond.update { true }
        assertSame(second(), receiver.items)
        assertSame(second(), forwarded())
        first().add(2)
        assertEquals(listOf(listOf(1)), propertyValues)
        assertEquals(listOf(listOf(1)), forwardedValues)
        second().add(2)

        assertEquals(listOf(listOf(1), listOf(1, 2)), propertyValues)
        assertEquals(listOf(listOf(1), listOf(1, 2)), forwardedValues)
        propertyConnection.disconnect()
        forwardedConnection.disconnect()
        forwarded.dispose()
        chooseSecond.dispose()
        first.dispose()
        second.dispose()
    }

    @Test
    fun `source clear disconnects downstream input observers transitively`() {
        val source = signal(owner = null, value = 1)
        val capture = InputRuntime.capture { source() }
        val receiver = InputNode(capture.initialValue)
        InputRuntime.install(receiver, "InputNode:amount", capture)
        scenes.currScene = receiver
        val forwarded = computed(owner = null) { receiver.amount + 1 }
        val propertyValues = mutableListOf<Int>()
        val forwardedValues = mutableListOf<Int>()
        val propertyConnection = (receiver::amount).connect(owner = null) { propertyValues += it }
        val forwardedConnection = forwarded.connect(owner = null) { forwardedValues += it }
        var effectRuns = 0
        val reaction = effect(owner = null) {
            forwarded()
            effectRuns++
        }

        source.clear()
        source.update { 2 }
        assertEquals(2, receiver.amount)
        assertEquals(3, forwarded())
        source.update { 3 }

        assertEquals(emptyList<Int>(), propertyValues)
        assertEquals(emptyList<Int>(), forwardedValues)
        assertEquals(1, effectRuns)
        propertyConnection.disconnect()
        forwardedConnection.disconnect()
        reaction.dispose()
        forwarded.dispose()
        source.dispose()
    }
}
