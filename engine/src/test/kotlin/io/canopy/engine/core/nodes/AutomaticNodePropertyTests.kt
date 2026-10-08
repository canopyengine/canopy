package io.canopy.engine.core.nodes

import kotlin.test.*
import java.lang.reflect.Modifier
import io.canopy.engine.core.exceptions.CanopyException
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.effect
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/** Uses the real compiler plugin and runtime together, rather than a compiler-only storage stub. */
class AutomaticNodePropertyTests {
    private lateinit var scenes: SceneManager

    private class Enemy(val initial: Int, resource: Any?) : Node<Enemy>("enemy") {
        var health = initial
        val resource = resource
        var nullable: String? = "initial"
        var clamped = 1
            set(value) {
                field = value.coerceAtLeast(0)
            }
        val alive get() = health > 0
        var explicit by nodeProperty(7)
        fun damage(amount: Int) {
            health -= amount
        }
    }

    private open class Parent<N : Parent<N>>(trace: MutableList<String>) : Node<N>("derived") {
        private var same = 2
        open var progress = 5
        init {
            trace += "base:$progress"
        }
        fun baseValue() = same
        fun changeBase(value: Int) {
            same = value
        }
    }

    private class Derived(val seed: Int, trace: MutableList<String>) : Parent<Derived>(trace) {
        private var same = 8
        override var progress = seed
        val doubled = progress * 2
        init {
            trace += "child:$doubled"
        }
        fun childValue() = same
    }

    private open class Defaults<N : Defaults<N>>(trace: MutableList<List<Any?>>) : Node<N>("defaults") {
        init {
            trace += values()
        }
        open fun values(): List<Any?> = emptyList()
    }

    private class Primitives(trace: MutableList<List<Any?>>) : Defaults<Primitives>(trace) {
        val flag = true
        val byte: Byte = 1
        val short: Short = 2
        val count = 3
        val total = 4L
        val fraction = 5f
        val precise = 6.0
        val character = 'x'
        val nullable: Int? = 7
        val text = "ready"
        override fun values(): List<Any?> =
            listOf(flag, byte, short, count, total, fraction, precise, character, nullable, text)
    }

    private class Generic<T>(val initial: T) : Node<Generic<T>>("generic") {
        var current = initial
        fun replace(value: T) {
            current = value
        }
    }

    private class Reactive(shared: Signal<Int>, observations: MutableList<Int>) : Node<Reactive>("reactive") {
        val shared = shared
        val owned = signal(this, 1)
        val reaction = effect(null) { observations += shared() }
    }

    private class Failing(trace: MutableList<String>) : Node<Failing>("failing") {
        val first = trace.add("first")
        val second: Any = throw IllegalStateException("initializer")
        init {
            trace += "unreachable"
        }
    }

    private class ExplicitFailing(trace: MutableList<String>) : Node<ExplicitFailing>("explicit-failing") {
        val first by nodeProperty(trace.add("first"))
        val second by nodeProperty<Any>(failInitializer())
        private fun failInitializer(): Any = throw IllegalStateException("initializer")
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
    fun `ordinary constructor and custom accessor properties use guarded storage`() {
        val resource = Any()
        val enemy = Enemy(100, resource)
        assertEquals(100, enemy.initial)
        assertSame(resource, enemy.resource)
        enemy.damage(20)
        enemy.clamped = -1
        enemy.nullable = null
        enemy.explicit++
        assertEquals(80, enemy.health)
        assertEquals(80, Enemy::health.get(enemy))
        assertEquals(0, enemy.clamped)
        assertNull(enemy.nullable)
        assertTrue(enemy.alive)
        assertEquals(8, enemy.explicit)

        val root = EmptyNode("root")
        scenes.currScene = root
        root.addChild(enemy)
        root.removeChild(enemy)
        enemy.health = 42
        root.addChild(enemy)
        assertEquals(42, enemy.health)
        enemy.queueFree()
        scenes.onUpdate(0f)
        listOf<() -> Unit>(
            { enemy.initial },
            { enemy.resource },
            { enemy.health },
            { enemy.health = 1 },
            { enemy.clamped = 1 },
            { enemy.nullable },
            { enemy.explicit },
            { enemy.alive }
        ).forEach { assertFailsWith<NodeDestroyedException>(block = it) }
    }

    @Test
    fun `inheritance preserves constructor order virtual defaults and distinct private slots`() {
        val trace = mutableListOf<String>()
        val node = Derived(6, trace)
        assertEquals(listOf("base:0", "child:12"), trace)
        assertEquals(6, node.seed)
        assertEquals(6, node.progress)
        assertEquals(2, node.baseValue())
        assertEquals(8, node.childValue())
        node.changeBase(9)
        assertEquals(9, node.baseValue())
        assertEquals(8, node.childValue())
    }

    @Test
    fun `all JVM primitive and reference defaults are available before subclass initialization`() {
        val trace = mutableListOf<List<Any?>>()
        val node = Primitives(trace)
        assertEquals(listOf(false, 0.toByte(), 0.toShort(), 0, 0L, 0f, 0.0, '\u0000', null, null), trace.single())
        assertEquals(listOf(true, 1.toByte(), 2.toShort(), 3, 4L, 5f, 6.0, 'x', 7, "ready"), node.values())
    }

    @Test
    fun `generic nullable values remain distinct from missing slots`() {
        val node = Generic<String?>("initial")
        assertEquals("initial", node.current)
        node.replace(null)
        assertNull(node.current)
        node.replace("next")
        assertEquals("next", node.current)
    }

    @Test
    fun `automatic payloads leave no instance fields and are cleared on destruction`() {
        val node = Enemy(100, Any())
        val instanceFields = Enemy::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(listOf("explicit\$delegate"), instanceFields.map { it.name })
        val state = node.state("test property release")
        assertTrue(state.properties.values.any { it === node.resource })
        scenes.currScene = node
        node.queueFree()
        scenes.onUpdate(0f)
        assertTrue(state.properties.isEmpty())
        assertFalse(node.isValid)
        assertEquals(0, scenes.retainedStateCount)
    }

    @Test
    fun `automatic Signal and Effect storage does not change explicit ownership`() {
        val shared = signal(null, 1)
        val observations = mutableListOf<Int>()
        val node = Reactive(shared, observations)
        val owned = node.owned
        val reaction = node.reaction
        try {
            assertEquals(listOf(1), observations)
            scenes.currScene = node
            node.queueFree()
            scenes.onUpdate(0f)
            assertFailsWith<CanopyException> { owned() }
            shared.update { 2 }
            assertEquals(listOf(1, 2), observations)
            assertFailsWith<NodeDestroyedException> { node.shared }
            assertFailsWith<NodeDestroyedException> { node.reaction }
        } finally {
            reaction.dispose()
            shared.dispose()
        }
    }

    @Test
    fun `initializer failures run once stop later initializers and release partial state`() {
        val trace = mutableListOf<String>()
        assertFailsWith<IllegalStateException> { Failing(trace) }
        assertEquals(listOf("first"), trace)
        // The compiler-generated construction boundary releases partially initialized payloads.
        assertEquals(0, scenes.retainedStateCount)
        trace.clear()
        assertFailsWith<IllegalStateException> { ExplicitFailing(trace) }
        assertEquals(listOf("first"), trace)
        assertEquals(0, scenes.retainedStateCount)
        ManagersRegistry.exit()
        assertNull(ManagersRegistry.getManagerOrNull(SceneManager::class))
    }
}
