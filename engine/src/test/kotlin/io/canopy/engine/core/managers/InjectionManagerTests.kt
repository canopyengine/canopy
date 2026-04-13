package io.canopy.engine.core.managers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class InjectionManagerTests {

    private class Service

    @Test
    fun `inject returns provider value`() {
        val manager = InjectionManager()
        val service = Service()

        manager.registerInjectable(Service::class) { service }

        assertSame(service, manager.inject(Service::class))
    }

    @Test
    fun `registerInjectable rejects duplicate type`() {
        val manager = InjectionManager()
        manager.registerInjectable(Service::class) { Service() }

        assertFailsWith<IllegalArgumentException> {
            manager.registerInjectable(Service::class) { Service() }
        }
    }

    @Test
    fun `provider is invoked for each injection`() {
        val manager = InjectionManager()
        var calls = 0
        manager.registerInjectable(Service::class) {
            calls++
            Service()
        }

        val first = manager.inject(Service::class)
        val second = manager.inject(Service::class)

        assertEquals(2, calls)
        assertNotSame(first, second)
    }

    @Test
    fun `inject throws when type is missing`() {
        val manager = InjectionManager()

        assertFailsWith<IllegalStateException> {
            manager.inject(Service::class)
        }
    }

    @Test
    fun `teardown clears providers`() {
        val manager = InjectionManager()
        manager.registerInjectable(Service::class) { Service() }

        manager.teardown()

        assertFailsWith<IllegalStateException> {
            manager.inject(Service::class)
        }
    }
}
