package io.canopy.engine.core.managers

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ManagersRegistryTeardownTests {
    private interface Service : Manager
    private class First(private val close: () -> Unit) : Service {
        override fun onExit() = close()
    }
    private class Second(private val close: () -> Unit) : Manager {
        override fun onExit() = close()
    }
    private class Third(private val close: () -> Unit) : Manager {
        override fun onExit() = close()
    }

    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `shutdown attempts registration order and clears cached lookups after failures`() {
        // Arrange
        val calls = mutableListOf<String>()
        val first = IllegalArgumentException("first")
        val later = IllegalStateException("later")
        ManagersRegistry.register(
            First {
                calls += "first"
                throw first
            }
        )
        ManagersRegistry.register(
            Second {
                calls += "second"
                throw later
            }
        )
        ManagersRegistry.register(Third { calls += "third" })
        assertNotNull(manager<Service>())

        // Act
        val failure = assertFailsWith<IllegalArgumentException> { ManagersRegistry.exit() }

        // Assert
        assertSame(first, failure)
        assertEquals(listOf(later), failure.suppressed.toList())
        assertEquals(listOf("first", "second", "third"), calls)
        assertFalse(ManagersRegistry.has(Service::class))
        assertFailsWith<IllegalStateException> { manager<Service>() }
        ManagersRegistry.exit()
        assertEquals(3, calls.size)
        val replacement = First {}
        ManagersRegistry.register(replacement)
        assertSame(replacement, manager<Service>())
    }

    @Test
    fun `same failure object is not suppressed onto itself and all managers still close`() {
        // Arrange
        val failure = IllegalStateException("shared")
        var calls = 0
        ManagersRegistry.register(
            First {
                calls++
                throw failure
            }
        )
        ManagersRegistry.register(
            Second {
                calls++
                throw failure
            }
        )

        // Act / Assert
        assertSame(failure, assertFailsWith<IllegalStateException> { ManagersRegistry.exit() })
        assertEquals(2, calls)
        assertTrue(failure.suppressed.isEmpty())
    }

    @Test
    fun `nested teardown is harmless and registry mutation and dispatch are rejected while lookup remains valid`() {
        // Arrange
        var calls = 0
        val service = First {
            calls++
            assertNotNull(manager<Service>())
            ManagersRegistry.exit()
            assertFailsWith<IllegalStateException> { ManagersRegistry.register(Third {}) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.unregister(Second::class) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.enter() }
            assertFailsWith<IllegalStateException> { ManagersRegistry.update(0f) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.physicsUpdate(0f) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.resize(1, 1) }
        }
        ManagersRegistry.register(service)
        ManagersRegistry.register(Second { calls++ })

        // Act
        ManagersRegistry.exit()
        ManagersRegistry.exit()

        // Assert
        assertEquals(2, calls)
        assertFalse(ManagersRegistry.has(Second::class))
    }
}
