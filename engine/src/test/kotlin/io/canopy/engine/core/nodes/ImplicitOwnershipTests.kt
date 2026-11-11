package io.canopy.engine.core.nodes

import kotlin.coroutines.*
import kotlin.test.*
import io.canopy.engine.core.flows.events.*
import io.canopy.engine.core.flows.events.computed as derive
import io.canopy.engine.core.flows.events.signal as state
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ImplicitOwnershipTests {
    private lateinit var scenes: SceneManager
    private lateinit var root: EmptyNode<*>
    private class Source : Node<Source>("source") {
        var count by signal(0)
        val changed = event<Int>()
        val raw = Signal(0, owner = this)
        val derived = Computed({ raw() * 2 }, owner = this)
    }
    private class Receiver(
        private val source: Source,
        private val values: MutableList<String>,
        private val supplied: Signal<Int> = state(0),
    ) : Node<Receiver>("receiver") {
        private var childWatch: (() -> Unit)? = null
        fun suppliedValue() = supplied()
        fun childConnection() = EmptyNode("child") {
            this@Receiver.childWatch = {
                (this@Receiver.source::count).connect { this@Receiver.values += "child:$it" }
            }
        }
        fun startChildWatch() {
            childWatch!!()
        }
        fun connectAll() {
            (source::count).connect { values += "property:$it" }
            source.changed.connect { values += "event:$it" }
            source.raw.connect { values += "signal:$it" }
            source.derived.connect { values += "computed:$it" }
        }
        fun badConnection() = (source::name).connect {}
        fun deferred(): () -> Unit = { (source::count).connect { values += "deferred:$it" } }
        fun shared(): EventDisconnectHandler = (source::count).connect(owner = null) { values += "shared:$it" }
        fun createSignal() = state(0)
        fun createComputed(input: Signal<Int>) = derive { input() * 2 }
        suspend fun createAfter(load: suspend () -> Int) = state(load())
    }

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
        root = EmptyNode("root")
        scenes.currScene = root
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `ordinary methods infer the listening node for properties events signals and computed sources`() {
        // Arrange
        val source = Source()
        val values = mutableListOf<String>()
        val receiver = Receiver(source, values)
        val caller = EmptyNode("caller")
        root.addChild(source)
        root.addChild(receiver)
        root.addChild(caller)
        assertEquals(0, receiver.suppliedValue())

        // Act
        NodeLifetime.withOwner(caller) {
            receiver.connectAll()
            assertFailsWith<IllegalArgumentException> { receiver.badConnection() }
            assertSame(caller, NodeLifetime.current())
        }
        source.count = 1
        source.changed.emit(1)
        source.raw.update { 1 }
        root.removeChild(receiver)
        source.count = 2
        source.changed.emit(2)
        source.raw.update { 2 }

        // Assert
        assertEquals(listOf("property:1", "event:1", "signal:1", "computed:2"), values)
        assertTrue(caller.isInsideTree)
    }

    @Test
    fun `escaped callbacks retain lexical ownership while explicit shared ownership remains available`() {
        // Arrange
        val source = Source()
        val values = mutableListOf<String>()
        val receiver = Receiver(source, values)
        root.addChild(source)
        root.addChild(receiver)
        val callback = receiver.deferred()

        // Act
        callback()
        val shared = receiver.shared()
        source.count = 1
        root.removeChild(receiver)
        source.count = 2
        shared.disconnect()
        source.count = 3

        // Assert
        assertEquals(listOf("deferred:1", "shared:1", "shared:2"), values)
        assertNull(NodeLifetime.current())
    }

    @Test
    fun `node configuration callbacks capture their node rather than the enclosing builder`() {
        // Arrange
        val source = Source()
        val values = mutableListOf<String>()
        val receiver = Receiver(source, values)
        root.addChild(source)
        root.addChild(receiver)
        val child = receiver.childConnection()
        root.addChild(child)

        // Act
        receiver.startChildWatch()
        source.count = 1
        root.removeChild(child)
        source.count = 2

        // Assert
        assertEquals(listOf("child:1"), values)
        assertTrue(receiver.isInsideTree)
    }

    @Test
    fun `aliased factories infer the node and release connections when that node exits`() {
        // Arrange
        val source = Source()
        val receiver = Receiver(source, mutableListOf())
        root.addChild(source)
        root.addChild(receiver)
        val input = Signal(0, owner = null)
        val direct = receiver.createSignal()
        val derived = receiver.createComputed(input)
        val directValues = mutableListOf<Int>()
        val derivedValues = mutableListOf<Int>()
        val first = direct.connect(owner = null) { directValues += it }
        val second = derived.connect(owner = null) { derivedValues += it }

        // Act
        direct.update { 1 }
        input.update { 1 }
        root.removeChild(receiver)
        direct.update { 2 }
        input.update { 2 }

        // Assert
        assertEquals(listOf(1), directValues)
        assertEquals(listOf(2), derivedValues)
        assertNull(NodeLifetime.current())
        first.disconnect()
        second.disconnect()
        input.dispose()
    }

    @Test
    fun `suspending arguments complete before the synchronous ownership scope starts`() {
        // Arrange
        val source = Source()
        val receiver = Receiver(source, mutableListOf())
        root.addChild(source)
        root.addChild(receiver)
        var pending: Continuation<Int>? = null
        var result: Result<Signal<Int>>? = null
        val operation: suspend () -> Signal<Int> = {
            receiver.createAfter { suspendCoroutine { pending = it } }
        }

        // Act
        operation.startCoroutine(object : Continuation<Signal<Int>> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<Signal<Int>>) {
                result = value
            }
        })
        assertNull(result)
        assertNull(NodeLifetime.current())
        pending!!.resume(7)
        val signal = result!!.getOrThrow()
        val values = mutableListOf<Int>()
        val connection = signal.connect(owner = null) { values += it }
        signal.update { 8 }
        root.removeChild(receiver)
        signal.update { 9 }

        // Assert
        assertEquals(listOf(8), values)
        assertNull(NodeLifetime.current())
        connection.disconnect()
    }
}
