package io.canopy.engine.core.queries

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.flows.ContextKey
import io.canopy.engine.core.flows.fromContext
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager as directManager
import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class DependencyTests {
    private lateinit var scenes: SceneManager

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
    }

    @AfterEach
    fun cleanup() {
        ManagersRegistry.exit()
    }

    private class Target(name: String) : Node<Target>(name)
    private data class Rules(val score: Int)
    private interface Service : Manager
    private class FirstService : Service
    private class SecondService : Service
    private enum class Keys(override val key: String) : ContextKey { Score("score") }

    private class Consumer(name: String = "consumer") : Node<Consumer>(name) {
        val parentTarget by ancestor<Target>()
        val optionalParent by ancestorOrNull<Target>()
        val target by child<Target>()
        val optionalChild by childOrNull<Target>()
        val sceneTarget by tree<Target>()
        val optionalTree by treeOrNull<Target>()
        val targets by group<Target>("targets")
        val rules by context<Rules>()
        val optionalRules by contextOrNull<Rules>()
        val score by context<Int>(Keys.Score)
        val optionalScore by contextOrNull<Int>("score")
        val service by manager<Service>()
        val optionalService by managerOrNull<Service>()
        fun oldManager(): SceneManager = directManager<SceneManager>()
    }

    private class Controller(node: Consumer? = null) : Behavior<Consumer>(node) {
        val target by child<Target>()
        val optionalTarget by childOrNull<Target>()
        val parent by ancestor<Target>()
        val sceneTarget by tree<Target>()
        val targets by group<Target>("targets")
        val rules by context<Rules>()
        val service by manager<Service>()
    }

    @Test
    fun `hierarchy reads select nearest ancestors and transparent direct children`() {
        // Arrange
        val root = Target("root")
        val nearest = Target("nearest")
        val consumer = Consumer()
        val scope = Context("scope")
        val first = Target("first")
        val second = Target("second")
        root.addChild(nearest)
        nearest.addChild(consumer)
        consumer.addChild(scope)
        scope.addChild(first)
        consumer.addChild(second)

        // Act / Assert
        assertSame(nearest, consumer.parentTarget)
        assertSame(first, consumer.target)
        scope.removeChild(first)
        assertSame(second, consumer.target)
        root.reparent(nearest, first)
        assertSame(nearest, consumer.parentTarget)
        nearest.reparent(consumer, root)
        assertSame(root, consumer.parentTarget)
    }

    @Test
    fun `tree reads use entered owner hierarchy root and preorder including root`() {
        // Arrange
        val consumer = Consumer()
        val root = EmptyNode("root")
        val branch = EmptyNode("branch")
        val first = Target("deep-first")
        val second = Target("second")
        root.addChild(branch)
        branch.addChild(first)
        root.addChild(second)
        root.addChild(consumer)

        // Act / Assert
        assertNull(consumer.optionalTree)
        root.buildTree()
        assertSame(first, consumer.sceneTarget)
        root.removeChild(branch)
        assertSame(second, consumer.sceneTarget)
        root.removeChild(consumer)
        assertNull(consumer.optionalTree)
        second.addChild(consumer)
        second.buildTree()
        assertSame(second, consumer.sceneTarget)
    }

    @Test
    fun `group reads produce fresh typed snapshots and observe membership and detachment`() {
        // Arrange
        val root = EmptyNode("root")
        val consumer = Consumer()
        val first = Target("first")
        val second = Target("second")
        val other = EmptyNode("other")
        first.addGroup("targets")
        other.addGroup("targets")
        root.addChild(consumer)
        root.addChild(first)
        root.addChild(second)
        root.addChild(other)
        scenes.currScene = root
        val snapshot = consumer.targets

        // Act / Assert
        assertEquals(listOf(first), snapshot)
        second.addGroup("targets")
        assertEquals(listOf(first, second), consumer.targets)
        first.removeGroup("targets")
        assertEquals(listOf(first), snapshot)
        assertEquals(listOf(second), consumer.targets)
        root.removeChild(second)
        assertTrue(consumer.targets.isEmpty())
        root.removeChild(consumer)
        assertTrue(consumer.targets.isEmpty())
    }

    @Test
    fun `typed and keyed contexts are independent and observe nearest provider updates`() {
        // Arrange
        val outer = Context("outer")
        val inner = Context("inner")
        val consumer = Consumer()
        var value = Rules(1)
        outer.provide<Rules> { Rules(0) }
        outer.provide(Keys.Score) { 10 }
        inner.provide<Rules> { value }
        outer.addChild(inner)
        inner.addChild(consumer)

        // Act / Assert
        assertEquals(Rules(1), consumer.rules)
        value = Rules(2)
        assertEquals(Rules(2), consumer.rules)
        assertEquals(10, consumer.score)
        assertEquals(10, consumer.fromContext<Int>(Keys.Score))
        inner.provide<Rules> { null }
        assertNull(consumer.optionalRules)
        assertFailsWith<NoSuchElementException> { consumer.rules }
        inner.provide("score") { "wrong type" }
        assertFailsWith<IllegalStateException> { consumer.optionalScore }
        inner.reparent(consumer, Context("replacement"))
        assertNull(consumer.optionalScore)
    }

    @Test
    fun `keyed null providers shadow outer values and provider exceptions propagate`() {
        // Arrange
        val outer = Context("outer")
        val inner = Context("inner")
        val consumer = Consumer()
        outer.provide("score") { 10 }
        inner.provide<Int>("score") { null }
        outer.addChild(inner)
        inner.addChild(consumer)

        // Act / Assert
        assertNull(consumer.optionalScore)
        assertFailsWith<NoSuchElementException> { consumer.score }
        val failure = IllegalArgumentException("provider failed")
        inner.provide<Int>("score") { throw failure }
        assertSame(failure, assertFailsWith<IllegalArgumentException> { consumer.optionalScore })
        inner.provide<Rules> { throw failure }
        assertSame(failure, assertFailsWith<IllegalArgumentException> { consumer.optionalRules })
    }

    @Test
    fun `managers resolve assignable registrations again and preserve direct manager API`() {
        // Arrange
        val consumer = Consumer()
        val first = FirstService()
        val second = SecondService()

        // Act / Assert
        assertSame(scenes, consumer.oldManager())
        assertNull(consumer.optionalService)
        assertFailsWith<NoSuchElementException> { consumer.service }
        ManagersRegistry.register(first)
        assertSame(first, consumer.service)
        ManagersRegistry.unregister(Service::class)
        ManagersRegistry.register(second)
        assertSame(second, consumer.service)
    }

    @Test
    fun `missing required dependencies identify property and query and optional reads recover`() {
        // Arrange
        val consumer = Consumer()

        // Act / Assert
        assertNull(consumer.optionalParent)
        assertNull(consumer.optionalChild)
        assertNull(consumer.optionalRules)
        assertNull(consumer.optionalScore)
        val failure = assertFailsWith<NoSuchElementException> { consumer.target }
        assertTrue(failure.message!!.contains("target"))
        assertTrue(failure.message!!.contains("Child"))
        val child = Target("child")
        consumer.addChild(child)
        assertSame(child, consumer.optionalChild)
    }

    @Test
    fun `behaviors share every query scope and detached behaviors report missing dependencies`() {
        // Arrange
        val root = Target("root")
        val scope = Context("scope")
        val consumer = Consumer()
        val child = Target("child")
        val controller = Controller(consumer)
        val service = FirstService()
        child.addGroup("targets")
        scope.provide<Rules> { Rules(3) }
        ManagersRegistry.register(service)
        root.addChild(scope)
        scope.addChild(consumer)
        consumer.addChild(child)
        scenes.currScene = root

        // Act / Assert
        assertSame(child, controller.target)
        assertSame(root, controller.parent)
        assertSame(root, controller.sceneTarget)
        assertEquals(listOf(child), controller.targets)
        assertEquals(Rules(3), controller.rules)
        assertSame(service, controller.service)
        val detached = Controller()
        assertSame(service, detached.service)
        assertNull(detached.optionalTarget)
        assertTrue(detached.targets.isEmpty())
        assertFailsWith<NoSuchElementException> { detached.target }
    }

    @Test
    fun `node dependency reads reject a destroyed owner while global reads remain available`() {
        // Arrange
        val consumer = Consumer()
        val controller = Controller(consumer)
        consumer.addChild(Target("child"))
        scenes.currScene = consumer
        assertNotNull(consumer.optionalChild)

        // Act
        consumer.queueFree()
        scenes.onUpdate(0f)

        // Assert
        assertFailsWith<NodeDestroyedException> { consumer.optionalParent }
        assertFailsWith<NodeDestroyedException> { consumer.optionalChild }
        assertFailsWith<NodeDestroyedException> { consumer.optionalTree }
        assertFailsWith<NodeDestroyedException> { consumer.targets }
        assertFailsWith<NodeDestroyedException> { consumer.optionalRules }
        assertNull(consumer.optionalService)
        assertSame(scenes, consumer.oldManager())
        assertFailsWith<NodeDestroyedException> { controller.optionalTarget }
    }
}
