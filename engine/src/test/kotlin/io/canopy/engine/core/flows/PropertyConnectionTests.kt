package io.canopy.engine.core.flows

import kotlin.test.*
import io.canopy.engine.core.flows.events.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class PropertyConnectionTests {
    private lateinit var scenes: SceneManager
    private class State : Node<State>("state") {
        var hunger by signal(100)
        val hungry by computed { hunger < 20 }
        val fish by signal(mutableListOf<String>())
        val fishNum by computed { fish.size }
        val ordinary by nodeProperty(42)
    }
    private class Listener(private val source: State, private val values: MutableList<Int>) :
        Node<Listener>("listener") {
        override fun onEnterTree() {
            (source::hunger).connect { values += it }
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
    fun `bound scalar and computed connections skip initial value suppress equal results and disconnect`() {
        // Arrange
        val state = State()
        scenes.currScene = state
        val values = mutableListOf<Int>()
        val derived = mutableListOf<Boolean>()
        val first = (state::hunger).connect(owner = null) { values += it }
        val second = (state::hungry).connect(owner = null) { derived += it }
        assertTrue(values.isEmpty())
        assertTrue(derived.isEmpty())

        // Act
        state.hunger = 50
        state.hunger = 50
        state.hunger = 10
        first.disconnect()
        first.disconnect()
        state.hunger = 0
        second.disconnect()
        state.hunger = 100

        // Assert
        assertEquals(listOf(50, 10), values)
        assertEquals(listOf(true), derived)
    }

    @Test
    fun `collection connection reports edits and computed counts without tracking listener reads`() {
        // Arrange
        val state = State()
        scenes.currScene = state
        val snapshots = mutableListOf<List<String>>()
        val counts = mutableListOf<Int>()
        val edits = (state::fish).connect(owner = null) {
            state.hunger
            snapshots += it.toList()
        }
        val population = (state::fishNum).connect(owner = null) { counts += it }

        // Act
        state.fish.add("Bubbles")
        state.hunger = 50
        state.fish[0] = "Coral"
        state.fish.listIterator().apply {
            next()
            remove()
        }

        // Assert
        assertEquals(listOf(listOf("Bubbles"), listOf("Coral"), emptyList()), snapshots)
        assertEquals(listOf(1, 0), counts)
        edits.disconnect()
        population.disconnect()
    }

    @Test
    fun `ambient listener ownership stops at exit and recreates connections on reentry`() {
        // Arrange
        val root = EmptyNode("root")
        val source = State()
        val values = mutableListOf<Int>()
        val listener = Listener(source, values)
        root.addChild(source)
        root.addChild(listener)
        scenes.currScene = root
        source.hunger = 90

        // Act
        root.removeChild(listener)
        source.hunger = 80
        root.addChild(listener)
        source.hunger = 70
        listener.queueFree()
        scenes.onUpdate(0f)
        source.hunger = 60

        // Assert
        assertEquals(listOf(90, 70), values)
    }

    @Test
    fun `source exit clears property captures and requires a fresh connection after reentry`() {
        // Arrange
        val root = EmptyNode("root")
        val source = State()
        root.addChild(source)
        scenes.currScene = root
        val values = mutableListOf<Int>()
        val connection = (source::hunger).connect(owner = null) { values += it }
        val computedConnection = (source::hungry).connect(owner = null) {}
        val collectionConnection = (source::fish).connect(owner = null) {}
        source.hunger = 90

        // Act / Assert
        root.removeChild(source)
        assertNull(capturedListener(connection))
        assertNull(capturedListener(computedConnection))
        assertNull(capturedListener(collectionConnection))
        source.hunger = 80
        assertFailsWith<IllegalArgumentException> { (source::hunger).connect(owner = null) {} }
        root.addChild(source)
        source.hunger = 70
        assertEquals(listOf(90), values)
        val renewed = (source::hunger).connect(owner = null) { values += it }
        source.hunger = 60
        source.queueFree()
        scenes.onUpdate(0f)
        assertNull(capturedListener(renewed))
        assertEquals(listOf(90, 60), values)
    }

    @Test
    fun `reentrant writes and self disconnection settle without further callbacks`() {
        // Arrange
        val source = State()
        scenes.currScene = source
        val values = mutableListOf<Int>()
        lateinit var connection: EventDisconnectHandler
        connection = (source::hunger).connect(owner = null) {
            values += it
            if (it == 90) source.hunger = 80
            if (it == 80) connection.disconnect()
        }

        // Act
        source.hunger = 90
        source.hunger = 70

        // Assert
        assertEquals(listOf(90, 80), values)
        assertNull(capturedListener(connection))
    }

    @Test
    fun `plain properties reject observation and initial getter failures leave no subscriptions`() {
        // Arrange
        val source = State()
        scenes.currScene = source
        val plain = assertFailsWith<IllegalArgumentException> { (source::ordinary).connect(owner = null) {} }
        assertTrue(plain.message!!.contains("ordinary"))
        val raw = signal(0)
        val failure = IllegalStateException("getter failed")
        class Broken {
            val value: Int get() {
                raw()
                throw failure
            }
        }

        // Act / Assert
        assertSame(failure, assertFails { (Broken()::value).connect(owner = null) {} })
        raw.update { 1 }
        raw.dispose()
    }

    @Test
    fun `ordinary Kotlin delegates and dynamic property getters use current dependencies`() {
        // Arrange
        class Values {
            var first by signal(1)
            var second by signal(2)
            var useFirst by signal(true)
            val selected get() = if (useFirst) first else second
        }
        val source = Values()
        val values = mutableListOf<Int>()
        val connection = (source::selected).connect(owner = null) { values += it }

        // Act
        source.first = 3
        source.useFirst = false
        source.first = 4
        source.second = 5

        // Assert
        assertEquals(listOf(3, 2, 5), values)
        connection.disconnect()
    }

    private fun capturedListener(connection: EventDisconnectHandler): Any? =
        EventDisconnectHandler::class.java.getDeclaredField("listener").apply { isAccessible = true }.get(connection)
}
