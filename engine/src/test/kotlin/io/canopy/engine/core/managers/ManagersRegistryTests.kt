package io.canopy.engine.core.managers

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.managers.manager

class ManagersRegistryTests {

    private open class BaseManager : Manager {
        var setupCalls = 0
        var teardownCalls = 0

        override fun setup() {
            setupCalls++
        }

        override fun teardown() {
            teardownCalls++
        }
    }

    private class ChildManager : BaseManager()
    private class OtherManager : Manager

    @AfterTest
    fun cleanup() {
        ManagersRegistry.teardown()
    }

    @Test
    fun `register rejects duplicate concrete manager`() {
        ManagersRegistry.register(OtherManager())

        assertFailsWith<IllegalArgumentException> {
            ManagersRegistry.register(OtherManager())
        }
    }

    @Test
    fun `register rejects managers that conflict by assignable lookup type`() {
        ManagersRegistry.register(ChildManager())

        assertFailsWith<IllegalArgumentException> {
            ManagersRegistry.register(BaseManager())
        }
    }

    @Test
    fun `lookup resolves by base type and unregister invalidates cache`() {
        val manager = ChildManager()
        ManagersRegistry.register(manager)

        assertSame(manager, ManagersRegistry.getManager(BaseManager::class))
        assertTrue(ManagersRegistry.has(BaseManager::class))

        ManagersRegistry.unregister(BaseManager::class)

        assertFalse(ManagersRegistry.has(BaseManager::class))
        assertFailsWith<IllegalStateException> {
            ManagersRegistry.getManager(BaseManager::class)
        }
    }

    @Test
    fun `setup and teardown call registered managers in registration order`() {
        val first = ChildManager()
        val second = OtherManager()
        val calls = mutableListOf<String>()
        val recordingSecond = object : Manager {
            override fun setup() {
                calls += "second.setup"
            }

            override fun teardown() {
                calls += "second.teardown"
            }
        }
        val recordingFirst = object : Manager {
            override fun setup() {
                calls += "first.setup"
            }

            override fun teardown() {
                calls += "first.teardown"
            }
        }

        ManagersRegistry.register(recordingFirst)
        ManagersRegistry.register(recordingSecond)

        ManagersRegistry.setup()
        ManagersRegistry.teardown()

        assertEquals(
            listOf("first.setup", "second.setup", "first.teardown", "second.teardown"),
            calls
        )
        assertEquals(0, first.setupCalls)
        assertTrue(second is OtherManager)
    }

    @Test
    fun `withScope clears previous registrations and sets up scoped managers`() {
        val scoped = ChildManager()
        ManagersRegistry.register(OtherManager())

        ManagersRegistry.withScope {
            register(scoped)
        }

        assertTrue(ManagersRegistry.has(ChildManager::class))
        assertFalse(ManagersRegistry.has(OtherManager::class))
        assertEquals(1, scoped.setupCalls)
    }

    @Test
    fun `manager and lazyManager helpers work correctly`() {
        val manager = ChildManager()
        ManagersRegistry.register(manager)

        val retrievedManager: ChildManager = manager()
        assertSame(manager, retrievedManager)

        val lazyRetrieved: ChildManager by lazyManager()
        assertSame(manager, lazyRetrieved)
    }

    @Test
    fun `has operator and contains work`() {
        val manager = ChildManager()
        ManagersRegistry.register(manager)

        assertTrue(ManagersRegistry.has(ChildManager::class))
        assertTrue(ChildManager::class in ManagersRegistry)

        assertFalse(ManagersRegistry.has(OtherManager::class))
        assertFalse(OtherManager::class in ManagersRegistry)
    }

    @Test
    fun `ambiguous manager resolution fails`() {
        // Registering anonymously implemented managers that share the same super type
        val m1 = object : BaseManager() {}
        val m2 = object : BaseManager() {}

        ManagersRegistry.register(m1)

        assertFailsWith<IllegalArgumentException> {
            ManagersRegistry.register(m2)
        }
    }
}
