package io.canopy.engine.core.queries

import kotlin.test.*
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager as directManager
import io.canopy.engine.core.managers.managerOrNull as directManagerOrNull
import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

private interface GlobalService : Manager
private class FirstGlobalService : GlobalService
private class SecondGlobalService : GlobalService
private val topLevelService by manager<GlobalService>()
private val topLevelOptional by managerOrNull<GlobalService>()

class GlobalDependencyTests {
    private class Consumer {
        val service by manager<GlobalService>()
        val optional by managerOrNull<GlobalService>()
    }
    private object SharedConsumer {
        val service by manager<GlobalService>()
    }
    private class ConsumerNode : Node<ConsumerNode>("consumer") {
        val service by manager<GlobalService>()
        val optional by managerOrNull<GlobalService>()
    }
    private class Controller(node: ConsumerNode? = null) : Behavior<ConsumerNode>(node) {
        val service by manager<GlobalService>()
        val optional by managerOrNull<GlobalService>()
    }

    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `global delegates support objects top level local and ownerless behavior properties`() {
        // Arrange
        val consumer = Consumer()
        val controller = Controller()
        val service = FirstGlobalService()
        val local by manager<GlobalService>()

        // Act
        ManagersRegistry.register(service)

        // Assert
        assertSame(service, consumer.service)
        assertSame(service, consumer.optional)
        assertSame(service, SharedConsumer.service)
        assertSame(service, topLevelService)
        assertSame(service, topLevelOptional)
        assertSame(service, local)
        assertSame(service, controller.service)
        assertSame(service, controller.optional)
    }

    @Test
    fun `missing reads recover and every scope observes replacement and shutdown`() {
        // Arrange
        val consumer = Consumer()
        val controller = Controller()
        assertNull(consumer.optional)
        assertNull(topLevelOptional)
        assertNull(controller.optional)
        val error = assertFailsWith<NoSuchElementException> { consumer.service }
        assertTrue(error.message.orEmpty().contains("service"))
        assertTrue(error.message.orEmpty().contains("Manager"))
        assertTrue(error.message.orEmpty().contains("GlobalService"))

        // Act / Assert
        val first = FirstGlobalService()
        ManagersRegistry.register(first)
        assertSame(first, consumer.optional)
        ManagersRegistry.unregister(GlobalService::class)
        assertNull(consumer.optional)
        val replacement = SecondGlobalService()
        ManagersRegistry.register(replacement)
        assertSame(replacement, consumer.service)
        assertSame(replacement, SharedConsumer.service)
        assertSame(replacement, topLevelService)
        assertSame(replacement, controller.service)
        ManagersRegistry.exit()
        assertNull(consumer.optional)
        assertFailsWith<NoSuchElementException> { topLevelService }
    }

    @Test
    fun `global reads ignore node lifecycle including destruction`() {
        // Arrange
        val scenes = SceneManager()
        ManagersRegistry.register(scenes)
        val service = FirstGlobalService()
        ManagersRegistry.register(service)
        val node = ConsumerNode()
        val controller = Controller(node)
        assertSame(service, node.service)
        scenes.currScene = node

        // Act
        node.queueFree()
        scenes.onUpdate(0f)

        // Assert
        assertFalse(node.isValid)
        assertSame(service, node.service)
        assertSame(service, node.optional)
        assertSame(service, controller.service)
        ManagersRegistry.unregister(GlobalService::class)
        assertNull(node.optional)
        assertFailsWith<NoSuchElementException> { node.service }
    }

    @Test
    fun `direct lookups share assignable cache and return null only for absence`() {
        // Arrange
        assertNull(directManagerOrNull<GlobalService>())
        assertNull(ManagersRegistry.getManagerOrNull(GlobalService::class))
        assertFailsWith<IllegalStateException> { directManager<GlobalService>() }
        val first = FirstGlobalService()

        // Act / Assert
        ManagersRegistry.register(first)
        assertSame(first, directManagerOrNull<FirstGlobalService>())
        assertSame(first, directManagerOrNull<GlobalService>())
        assertSame(first, directManager<GlobalService>())
        ManagersRegistry.unregister(GlobalService::class)
        assertNull(directManagerOrNull<GlobalService>())
        val second = SecondGlobalService()
        ManagersRegistry.register(second)
        assertSame(second, directManagerOrNull<GlobalService>())
        ManagersRegistry.exit()
        assertNull(directManagerOrNull<GlobalService>())
    }

    @Test
    fun `optional lookups propagate ambiguity and required lookups retain error contracts`() {
        // Arrange: the broad Manager type can match unrelated registrations.
        ManagersRegistry.register(FirstGlobalService())
        ManagersRegistry.register(object : Manager {})
        val optional by managerOrNull<Manager>()
        val required by manager<Manager>()

        // Act / Assert
        assertFailsWith<IllegalStateException> { ManagersRegistry.getManagerOrNull(Manager::class) }
        assertFailsWith<IllegalStateException> { directManagerOrNull<Manager>() }
        assertFailsWith<IllegalStateException> { directManager<Manager>() }
        assertFailsWith<IllegalStateException> { optional }
        assertFailsWith<IllegalStateException> { required }
    }

    @Test
    fun `direct and delegated lookups remain available during shutdown callbacks`() {
        // Arrange
        var calls = 0
        val dependency by manager<GlobalService>()
        val optional by managerOrNull<GlobalService>()
        val service = object : GlobalService {
            override fun onExit() {
                calls++
                assertSame(this, dependency)
                assertSame(this, optional)
                assertSame(this, directManagerOrNull<GlobalService>())
                assertSame(this, directManager<GlobalService>())
            }
        }
        ManagersRegistry.register(service)

        // Act
        ManagersRegistry.exit()

        // Assert
        assertEquals(1, calls)
        assertNull(optional)
        assertNull(directManagerOrNull<GlobalService>())
        assertFailsWith<NoSuchElementException> { dependency }
    }
}
