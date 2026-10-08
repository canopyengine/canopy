package io.canopy.engine.core.managers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ManagerTypeDiscoveryTests {
    private interface Shared : Manager
    private interface Left : Shared
    private interface Right : Shared
    private open class Base : Shared
    private class Existing :
        Base(),
        Left,
        Right
    private class ClassFirst :
        Base(),
        Left,
        Right

    private class Derived : Base()
    private class Unrelated : Manager

    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `conflict diagnostics follow superclass first order without repeated diamond types`() {
        val existing = Existing()
        ManagersRegistry.register(existing)
        val classFirst = assertFailsWith<IllegalArgumentException> { ManagersRegistry.register(ClassFirst()) }
        assertEquals(
            "Manager ClassFirst conflicts with an existing registration for: Base, Shared, Left, Right",
            classFirst.message
        )
        assertSame(existing, ManagersRegistry.getManager(Shared::class))
        assertSame(existing, ManagersRegistry.getManager(Base::class))
    }

    @Test
    fun `subclass conflicts leave cached lookups intact and bare Manager does not block unrelated services`() {
        val base = Base()
        ManagersRegistry.register(base)
        assertSame(base, ManagersRegistry.getManager(Shared::class))
        assertFailsWith<IllegalArgumentException> { ManagersRegistry.register(Derived()) }
        assertSame(base, ManagersRegistry.getManager(Shared::class))
        ManagersRegistry.register(Unrelated())
        assertFailsWith<IllegalStateException> { ManagersRegistry.getManager(Manager::class) }
        ManagersRegistry.unregister(Base::class)
        val derived = Derived()
        ManagersRegistry.register(derived)
        assertSame(derived, ManagersRegistry.getManager(Shared::class))
    }
}
