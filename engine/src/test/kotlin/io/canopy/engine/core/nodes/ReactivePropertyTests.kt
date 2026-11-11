package io.canopy.engine.core.nodes

import kotlin.test.*
import java.lang.reflect.Modifier
import io.canopy.engine.core.exceptions.NodeCallbackException
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.events.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ReactivePropertyTests {
    private lateinit var scenes: SceneManager

    private class Fish(private val observed: MutableList<Int>) : Node<Fish>("fish") {
        var hunger by signal(100)
            private set
        var nickname: String? by signal<String?>(null)
        val needsFood by computed { hunger < 20 }
        fun feed() {
            hunger = 100
        }
        fun swim() {
            hunger -= 50
        }
        override fun onEnterTree() {
            effect { observed += hunger }
        }
    }

    private class Sources : Node<Sources>("sources") {
        val raw = Signal(1, owner = this)
        val derived = Computed({ raw() * 2 }, owner = this)
    }

    private class FailedReaction(private val source: Signal<Int>, private val trace: MutableList<Int>) :
        Node<FailedReaction>("failed-reaction") {
        private val reaction = effect {
            trace += source()
            error("first entry failed")
        }
    }

    private class Shared(source: Signal<Any>) : Node<Shared>("shared") {
        var value by source
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
    fun `native state and computed delegates preserve values without work during detachment`() {
        // Arrange
        val observed = mutableListOf<Int>()
        val fish = Fish(observed)
        val root = EmptyNode("root")
        root.addChild(fish)
        assertTrue(observed.isEmpty())
        scenes.currScene = root

        // Act / Assert
        assertEquals(listOf(100), observed)
        fish.swim()
        fish.swim()
        assertTrue(fish.needsFood)
        assertEquals(listOf(100, 50, 0), observed)
        fish.nickname = "Bubbles"
        assertEquals("Bubbles", fish.nickname)
        root.removeChild(fish)
        fish.feed()
        assertFalse(fish.needsFood)
        assertEquals(listOf(100, 50, 0), observed)
        root.addChild(fish)
        assertEquals(listOf(100, 50, 0, 100), observed)
        fish.swim()
        assertFalse(fish.needsFood)
        fish.swim()
        assertTrue(fish.needsFood)
        assertEquals(listOf(100, 50, 0, 100, 50, 0), observed)
    }

    @Test
    fun `removal disconnects source callbacks and flow publication and reentry uses fresh connections`() {
        // Arrange
        val node = Sources()
        val root = EmptyNode("root")
        root.addChild(node)
        scenes.currScene = root
        val values = mutableListOf<Int>()
        val connection = node.raw.connect(owner = null) { values += it }
        val flow = node.raw.flow
        assertEquals(2, node.derived())

        // Act
        node.raw.update { 2 }
        root.removeChild(node)
        node.raw.update { 3 }

        // Assert
        assertEquals(listOf(2), values)
        assertTrue(flow.replayCache.isEmpty())
        assertEquals(6, node.derived())
        assertFailsWith<IllegalStateException> { node.raw.connect(owner = null) {} }
        root.addChild(node)
        assertEquals(6, node.derived())
        val renewedFlow = node.raw.flow
        assertNotSame(flow, renewedFlow)
        assertEquals(listOf(3), renewedFlow.replayCache)
        val renewed = node.raw.connect(owner = null) { values += it }
        node.raw.update { 4 }
        assertEquals(listOf(4), renewedFlow.replayCache)
        assertTrue(flow.replayCache.isEmpty())
        assertEquals(8, node.derived())
        assertEquals(listOf(2, 4), values)
        connection.disconnect()
        renewed.disconnect()
    }

    @Test
    fun `retained facades keep only guarded descriptors and shared state stays independently owned`() {
        // Arrange
        val source = Signal(Any(), owner = null)
        val node = Shared(source)
        val payload = node.state("inspect delegate storage")
        val descriptor = Shared::class.java.declaredFields.single { !Modifier.isStatic(it.modifiers) }
        assertEquals(SignalProperty::class.java, descriptor.type)
        scenes.currScene = node
        assertSame(source(), node.value)

        // Act
        node.queueFree()
        scenes.onUpdate(0f)

        // Assert
        assertTrue(payload.properties.isEmpty())
        assertTrue(payload.activity.isEmpty())
        assertFailsWith<NodeDestroyedException> { node.value }
        assertFailsWith<NodeDestroyedException> { node.value = Any() }
        assertNotNull(source())
        source.dispose()
    }

    @Test
    fun `failed deferred first reaction releases subscriptions before propagating the error`() {
        // Arrange
        val source = Signal(0)
        val trace = mutableListOf<Int>()
        val node = FailedReaction(source, trace)
        assertTrue(trace.isEmpty())

        // Act
        assertFailsWith<NodeCallbackException> { node.nodeEnterTree() }
        source.update { 1 }

        // Assert
        assertEquals(listOf(0), trace)
        node.queueFree()
        scenes.onUpdate(0f)
        source.dispose()
    }

    @Test
    fun `ordinary Kotlin delegates retain equality suppression and dynamic computed dependencies`() {
        // Arrange
        var first by Signal(1)
        var second by Signal(2)
        var useFirst by Signal(true)
        val selected by Computed({ if (useFirst) first else second })
        val values = mutableListOf<Int>()
        val reaction = effect { values += selected }

        // Act
        first = 1
        first = 3
        useFirst = false
        first = 4
        second = 5

        // Assert
        assertEquals(listOf(1, 3, 2, 5), values)
        reaction.dispose()
    }
}
