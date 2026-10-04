package io.canopy.engine.data.assets

import kotlin.test.*
import io.canopy.engine.core.exceptions.InvalidNodeDefinitionException
import io.canopy.engine.core.exceptions.NodeCleanupException
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.attachBehavior
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class AssetDelegateTests {
    private class Value(val path: String, private val dispose: () -> Unit = {}) : CanopyAsset {
        var closes = 0
        override fun close() {
            closes++
            dispose()
        }
    }
    private class Consumer(name: String = "consumer") : Node<Consumer>(name) {
        val value by asset<Value>("shared")
        val same by asset(assetKey<Value>("shared"))
        val other by asset<Value>("other")
        var exit by nodeProperty<((Consumer) -> Unit)?>(null)
        override fun onExitTree() {
            if (isValid) exit?.invoke(this)
        }
    }
    private class Controller(node: Consumer? = null) : Behavior<Consumer>(node) {
        val value by asset<Value>("shared")
    }
    private lateinit var scenes: SceneManager
    private lateinit var resources: ResourceManager
    private var loads = 0

    @BeforeEach
    fun setup() {
        ManagersRegistry.exit()
        scenes = SceneManager()
        resources = ResourceManager()
        resources.registerLoader<Value> { key ->
            loads++
            Value(key.path)
        }
        ManagersRegistry.register(scenes)
        ManagersRegistry.register(resources)
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `runtime Java validation accepts metadata delegates and rejects raw resource fields before allocation`() {
        // Arrange / Act / Assert
        assertFailsWith<InvalidNodeDefinitionException> { UnsafeJavaAssetNode() }
        assertEquals(0, scenes.retainedStateCount)
        val node = SafeJavaAssetNode(asset(assetKey<Value>("shared")))
        assertTrue(node.isValid)
        assertEquals(1, scenes.retainedStateCount)
        assertEquals(0, loads)
    }

    @Test
    fun `preloads node properties and behavior share one owner slot and two nodes share one loaded value`() {
        // Arrange
        val root = EmptyNode("root")
        val first = Consumer("first")
        val second = Consumer("second")
        val controller = Controller(first)
        first.attachBehavior { controller }
        root.addChild(first)
        root.addChild(second)
        scenes.currScene = root

        // Act
        first.resources { preload(assetKey<Value>("shared")) }
        controller.resources { preload(assetKey<Value>("shared")) }
        val value = first.value

        // Assert
        assertSame(value, first.same)
        assertSame(value, controller.value)
        assertSame(value, second.value)
        assertEquals(1, loads)
        root.removeChild(first)
        assertEquals(0, value.closes)
        root.removeChild(second)
        assertEquals(1, value.closes)
    }

    @Test
    fun `detached and exiting owners cannot acquire but prior values remain readable in normal exit hooks`() {
        // Arrange
        val root = EmptyNode("root")
        val consumer = Consumer()
        assertFailsWith<IllegalStateException> { consumer.value }
        root.addChild(consumer)
        scenes.currScene = root
        val value = consumer.value
        var exitReads = 0
        consumer.exit = {
            assertSame(value, it.value)
            assertSame(value, it.same)
            assertFailsWith<IllegalStateException> { it.other }
            assertEquals(0, value.closes)
            exitReads++
        }
        consumer.onRemoval {
            assertSame(value, consumer.value)
            assertFailsWith<IllegalStateException> { consumer.other }
            assertEquals(0, value.closes)
            exitReads++
        }

        // Act
        root.removeChild(consumer)

        // Assert
        assertEquals(2, exitReads)
        assertEquals(1, value.closes)
        assertFailsWith<IllegalStateException> { consumer.value }
        consumer.exit = null
        root.addChild(consumer)
        assertNotSame(value, consumer.value)
        assertEquals(2, loads)
    }

    @Test
    fun `entered reparenting retains ownership and exit reentry reacquires`() {
        // Arrange
        val root = EmptyNode("root")
        val first = EmptyNode("first")
        val second = EmptyNode("second")
        val consumer = Consumer()
        root.addChild(first)
        root.addChild(second)
        first.addChild(consumer)
        scenes.currScene = root
        val value = consumer.value

        // Act / Assert
        first.reparent(consumer, second)
        assertSame(value, consumer.value)
        assertEquals(1, loads)
        second.removeChild(consumer)
        assertEquals(1, value.closes)
        second.addChild(consumer)
        assertNotSame(value, consumer.value)
        assertEquals(2, loads)
    }

    @Test
    fun `destruction rejects node and behavior reads and releases shared owner exactly once`() {
        // Arrange
        val consumer = Consumer()
        val controller = Controller(consumer)
        scenes.currScene = consumer
        val value = consumer.value
        consumer.attachBehavior {
            object : Behavior<Consumer>(it) {
                override fun onExitTree() {
                    assertFailsWith<NodeDestroyedException> { controller.value }
                    assertFailsWith<NodeDestroyedException> { consumer.value }
                }
            }
        }

        // Act
        consumer.queueFree()
        scenes.onUpdate(0f)

        // Assert
        assertEquals(1, value.closes)
        assertFailsWith<NodeDestroyedException> { consumer.same }
        assertFailsWith<NodeDestroyedException> { controller.value }
        resources.onExit()
        assertEquals(1, value.closes)
    }

    @Test
    fun `loader removal of owner releases acquired handle instead of installing detached slot`() {
        // Arrange
        ManagersRegistry.unregister(ResourceManager::class)
        resources = ResourceManager()
        ManagersRegistry.register(resources)
        val root = EmptyNode("root")
        val consumer = Consumer()
        root.addChild(consumer)
        scenes.currScene = root
        val value = Value("removed")
        resources.registerLoader<Value> {
            root.removeChild(consumer)
            value
        }

        // Act / Assert
        assertFailsWith<IllegalStateException> { consumer.value }
        assertEquals(1, value.closes)
        assertFalse(consumer.isInsideTree)
        assertFailsWith<IllegalStateException> { consumer.value }
    }

    @Test
    fun `loader exit and reentry cannot install a handle from an earlier entry generation`() {
        // Arrange
        ManagersRegistry.unregister(ResourceManager::class)
        resources = ResourceManager()
        ManagersRegistry.register(resources)
        val root = EmptyNode("root")
        val consumer = Consumer()
        root.addChild(consumer)
        scenes.currScene = root
        val value = Value("reentered")
        resources.registerLoader<Value> {
            root.removeChild(consumer)
            root.addChild(consumer)
            value
        }

        // Act / Assert
        assertFailsWith<IllegalStateException> { consumer.value }
        assertTrue(consumer.isInsideTree)
        assertEquals(1, value.closes)
    }

    @Test
    fun `loader destruction rejects installation and preserves release failure as suppressed`() {
        // Arrange
        ManagersRegistry.unregister(ResourceManager::class)
        resources = ResourceManager()
        ManagersRegistry.register(resources)
        val consumer = Consumer()
        scenes.currScene = consumer
        val closeFailure = IllegalArgumentException("close")
        val value = Value("destroyed") { throw closeFailure }
        resources.registerLoader<Value> {
            consumer.queueFree()
            scenes.onUpdate(0f)
            value
        }

        // Act / Assert
        val failure = assertFailsWith<NodeDestroyedException> { consumer.value }
        assertEquals(listOf(closeFailure), failure.suppressed.toList())
        assertEquals(1, value.closes)
        assertFailsWith<NodeDestroyedException> { consumer.value }
    }

    @Test
    fun `exit failure still releases every owner slot and clears dedup map before reentry`() {
        // Arrange
        ManagersRegistry.unregister(ResourceManager::class)
        resources = ResourceManager()
        ManagersRegistry.register(resources)
        val values = mutableListOf<Value>()
        val closeFailure = IllegalArgumentException("close")
        resources.registerLoader<Value> { key ->
            Value(key.path) {
                if (key.path == "other") throw closeFailure
            }.also(values::add)
        }
        val root = EmptyNode("root")
        val consumer = Consumer()
        root.addChild(consumer)
        scenes.currScene = root
        consumer.value
        consumer.other
        val exitFailure = IllegalStateException("exit")
        consumer.exit = { throw exitFailure }

        // Act / Assert
        val failure = assertFailsWith<NodeCleanupException> { root.removeChild(consumer) }
        assertSame(exitFailure, failure.cause)
        assertTrue(failure.suppressed.any { it.cause === closeFailure })
        assertEquals(listOf(1, 1), values.map { it.closes })
        consumer.exit = null
        root.addChild(consumer)
        assertNotSame(values.first(), consumer.value)
        assertEquals(3, values.size)
    }

    @Test
    fun `later failed preload preserves earlier successful ownership and detached behaviors have no owner`() {
        // Arrange
        val consumer = Consumer()
        scenes.currScene = consumer
        val detached = Controller()

        // Act / Assert
        assertFailsWith<IllegalStateException> {
            consumer.resources {
                preload(assetKey<Value>("shared"))
                preload(assetKey<Unsupported>("missing"))
            }
        }
        val value = consumer.value
        assertEquals(1, loads)
        assertEquals(0, value.closes)
        assertFailsWith<IllegalStateException> { detached.value }
        assertFailsWith<IllegalStateException> { detached.resources { preload(assetKey<Value>("shared")) } }
        consumer.nodeExitTree()
        assertEquals(1, value.closes)
    }

    private class Unsupported : CanopyAsset {
        override fun close() = Unit
    }
}
