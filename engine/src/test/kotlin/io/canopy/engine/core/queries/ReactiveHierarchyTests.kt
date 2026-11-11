package io.canopy.engine.core.queries

import kotlin.test.*
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.flows.events.*
import io.canopy.engine.core.flows.getContext
import io.canopy.engine.core.flows.getContextOrNull
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.nodeConstruction
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ReactiveHierarchyTests {
    private lateinit var scenes: SceneManager
    private class Tank(name: String, val food: Int) : Node<Tank>(name)
    private open class Fish(name: String) : Node<Fish>(name) {
        val tank by ancestor<Tank>()
        val food by computed { tank.food }
    }
    private class Goldfish : Fish("goldfish")
    private class Counter : Node<Counter>("counter") {
        val tank by ancestor<Tank>()
        val population by computed { tank.getChildrenOfType<Fish>().size }
        val members by group<Fish>("fish")
        val grouped by computed { members.size }
        val color by contextOrNull<String>("color")
        val displayedColor by computed { color }
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
    fun `computed hierarchy follows subclass membership active reparent and queued destruction`() {
        // Arrange
        val root = EmptyNode("root")
        val first = Tank("first", 7)
        val second = Tank("second", 9)
        val counter = Counter()
        first.addChild(counter)
        root.addChild(first)
        root.addChild(second)
        scenes.currScene = root
        assertEquals(0, counter.population)
        assertEquals(0, counter.grouped)
        val fish = Goldfish()

        // Act / Assert
        first.addChild(fish)
        assertEquals(1, counter.population)
        assertTrue(first.hasChild<Fish>())
        assertFalse(first.hasExactChildType(Fish::class))
        assertSame(fish, first.getChild<Fish>())
        assertEquals(7, fish.food)
        fish.addGroup("fish")
        assertEquals(1, counter.grouped)
        first.reparent(fish, second)
        assertEquals(9, fish.food)
        assertEquals(0, counter.population)
        second.reparent(fish, first)
        fish.queueFree()
        assertEquals(1, counter.population)
        scenes.onUpdate(0f)
        assertEquals(0, counter.population)
        assertEquals(0, counter.grouped)
    }

    @Test
    fun `transparent context replacement and reentrant mutation publish coherent complete trees`() {
        // Arrange
        val tank = Tank("tank", 1)
        val scope = Context("scope")
        val counter = Counter()
        scope.provide("color") { "blue" }
        scope.addChild(counter)
        tank.addChild(scope)
        scenes.currScene = tank
        assertEquals("blue", counter.displayedColor)
        val counts = mutableListOf<Int>()
        val reaction = effect {
            val fish = tank.getChildrenOfType<Fish>()
            assertTrue(fish.all { it.parent === scope && it.isInsideTree })
            counts += fish.size
            if (fish.size == 1) scope.addChild(Fish("second"))
        }

        // Act
        scope.addChild(Goldfish())

        // Assert
        assertEquals(listOf(0, 1, 2), counts)
        assertEquals(2, counter.population)
        reaction.dispose()
        scope.provide("color") { "green" }
        assertEquals("green", counter.displayedColor)
    }

    @Test
    fun `constructor rollback hides partial descendants and notification failures preserve the primary error`() {
        // Arrange
        val tank = Tank("tank", 1)
        scenes.currScene = tank
        val counts = mutableListOf<Int>()
        val reaction = effect { counts += tank.getChildrenOfType<Fish>().size }
        val failure = IllegalArgumentException("constructor failed")

        // Act
        val thrown = assertFailsWith<IllegalArgumentException> {
            nodeConstruction {
                tank.withChildConstruction {
                    Fish("unfinished")
                    throw failure
                }
            }
        }

        // Assert
        assertSame(failure, thrown)
        assertEquals(listOf(0, 0), counts)
        assertFalse(tank.hasChild<Fish>())
        reaction.dispose()
        val notification = IllegalStateException("observer failed")
        var observing = false
        val failingReaction = effect {
            tank.children
            if (observing) throw notification
        }
        observing = true
        val failed = assertFailsWith<IllegalArgumentException> {
            nodeConstruction {
                tank.withChildConstruction { Fish("another") }
                throw failure
            }
        }
        assertSame(failure, failed)
        assertTrue(failed.suppressed.any { it === notification })
        failingReaction.dispose()
    }

    @Test
    fun `immediate context helpers share live lookup type and null shadowing rules`() {
        // Arrange
        val outer = Context("outer")
        val inner = Context("inner")
        val node = EmptyNode("node")
        outer.provide("color") { "blue" }
        outer.provide<String> { "typed" }
        outer.addChild(inner)
        inner.addChild(node)

        // Act / Assert
        assertEquals("blue", node.getContext<String>("color"))
        assertEquals("typed", node.getContext<String>())
        assertNull(node.getContextOrNull<String>("absent"))
        assertFailsWith<IllegalStateException> { node.getContext<Int>("color") }
        inner.provide<String>("color") { null }
        assertNull(node.getContextOrNull<String>("color"))
        assertFailsWith<NoSuchElementException> { node.getContext<String>("color") }
    }
}
