package io.canopy.engine.data.assets

import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import org.junit.jupiter.api.AfterEach

class ResourceManagerTests {
    private open class Value(val name: String, private val dispose: () -> Unit = {}) : CanopyAsset {
        var closes = 0
        override fun close() {
            closes++
            dispose()
        }
    }
    private class Other : CanopyAsset {
        override fun close() = Unit
    }
    private class Derived : Value("derived")

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `keys copy options and compare exact type path source and parameters`() {
        // Arrange
        val options = mutableMapOf("format" to "one")
        val key = assetKey<Value>("./file", parameters = options)
        options["format"] = "two"

        // Act / Assert
        assertEquals(mapOf("format" to "one"), key.parameters)
        assertFailsWith<UnsupportedOperationException> { (key.parameters as MutableMap)["format"] = "three" }
        val equal = assetKey<Value>("./file", parameters = mapOf("format" to "one"))
        assertEquals(key, equal)
        assertEquals(key.hashCode(), equal.hashCode())
        assertNotEquals(key, assetKey<Value>("file", parameters = key.parameters))
        assertNotEquals(key, assetKey<Value>("./file", FileSource.Local, key.parameters))
        assertNotEquals(key, assetKey<Value>("./file", parameters = options))
        assertNotEquals<Any>(key, assetKey<Other>("./file", parameters = key.parameters))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `keys reject non string and null parameters from erased JVM maps`() {
        // Arrange
        val invalidOptions = listOf(
            mapOf<Any?, Any?>(Any() to "value"),
            mapOf<Any?, Any?>("option" to { Unit }),
            mapOf<Any?, Any?>(null to "value"),
            mapOf<Any?, Any?>("option" to null)
        )

        // Act / Assert
        invalidOptions.forEach { options ->
            val failure = assertFailsWith<IllegalArgumentException> {
                assetKey<Value>("file", parameters = options as Map<String, String>)
            }
            assertTrue("String keys and values" in failure.message.orEmpty())
        }
    }

    @Test
    fun `leases share equal keys and last close immediately disposes once`() {
        // Arrange
        val manager = ResourceManager()
        var loads = 0
        manager.registerLoader<Value> { Value("load-${++loads}") }
        val first = manager.acquire(assetKey<Value>("file"))
        val second = manager.acquire(assetKey<Value>("file"))
        val value = first.value

        // Act / Assert
        assertSame(value, second.value)
        assertEquals(1, loads)
        first.close()
        first.close()
        assertFailsWith<IllegalStateException> { first.value }
        assertEquals(0, value.closes)
        assertSame(value, second.value)
        second.close()
        assertEquals(1, value.closes)
        manager.acquire(assetKey<Value>("file")).use { assertNotSame(value, it.value) }
        assertEquals(2, loads)
        manager.onExit()
    }

    @Test
    fun `loader selection is exact and duplicate or missing registrations fail`() {
        // Arrange
        val manager = ResourceManager()
        manager.registerLoader(Value::class) { Value(it.path) }

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { manager.registerLoader<Value> { Value("duplicate") } }
        assertFailsWith<IllegalStateException> { manager.acquire(assetKey<Derived>("file")) }
        manager.registerLoader<Other> { Other() }
        manager.acquire(assetKey<Other>("file")).close()
        manager.onExit()
    }

    @Test
    fun `different key identity and managers use isolated cache entries`() {
        // Arrange
        val first = ResourceManager()
        val second = ResourceManager()
        listOf(first, second).forEach { it.registerLoader<Value> { key -> Value(key.toString()) } }
        val base = first.acquire(assetKey<Value>("file"))
        val path = first.acquire(assetKey<Value>("./file"))
        val source = first.acquire(assetKey<Value>("file", FileSource.External))
        val options = first.acquire(assetKey<Value>("file", parameters = mapOf("mode" to "custom")))
        val isolated = second.acquire(assetKey<Value>("file"))

        // Act / Assert
        assertEquals(5, listOf(base.value, path.value, source.value, options.value, isolated.value).toSet().size)
        first.onExit()
        assertNotNull(isolated.value)
        second.onExit()
    }

    @Test
    fun `failed recursive and invalid option loads are not cached and can retry`() {
        // Arrange
        val manager = ResourceManager()
        val key = assetKey<Value>("file")
        var attempts = 0
        manager.registerLoader<Value> {
            attempts++
            require(it.parameters.isEmpty()) { "unsupported options" }
            if (attempts == 1) manager.acquire(key)
            Value("success")
        }

        // Act / Assert
        assertFailsWith<IllegalStateException> { manager.acquire(key) }
        assertFailsWith<IllegalArgumentException> {
            manager.acquire(assetKey<Value>("file", parameters = mapOf("unsupported" to "true")))
        }
        manager.acquire(key).use { assertEquals("success", it.value.name) }
        assertEquals(3, attempts)
        manager.onExit()
    }

    @Test
    fun `idle expiry starts after last release and reacquisition cancels deadline`() {
        // Arrange
        var now = 0L
        val manager = ResourceManager(10.nanoseconds)
        manager.clock = { now }
        var loads = 0
        manager.registerLoader<Value> { Value("load-${++loads}") }
        val key = assetKey<Value>("file")
        val first = manager.acquire(key)
        val original = first.value
        now = 100
        manager.onUpdate(0f)
        assertEquals(0, original.closes)
        first.close()
        now = 109
        val second = manager.acquire(key)

        // Act / Assert
        assertSame(original, second.value)
        now = 200
        manager.onUpdate(0f)
        assertEquals(0, original.closes)
        second.close()
        now = 210
        manager.onUpdate(0f)
        assertEquals(1, original.closes)
        manager.acquire(key).use { assertNotSame(original, it.value) }
        assertEquals(2, loads)
        manager.onExit()
    }

    @Test
    fun `acquisition at expiry reloads before sweep and paused registry frames use real time`() {
        // Arrange
        var now = 0L
        val manager = ResourceManager(10.nanoseconds)
        manager.clock = { now }
        manager.registerLoader<Value> { Value("value") }
        ManagersRegistry.register(manager)
        val key = assetKey<Value>("file")
        val first = manager.acquire(key)
        val old = first.value
        first.close()
        now = 10

        // Act / Assert
        val second = manager.acquire(key)
        assertNotSame(old, second.value)
        assertEquals(1, old.closes)
        val replacement = second.value
        second.close()
        now = 20
        ManagersRegistry.update(0f, paused = true)
        assertEquals(1, replacement.closes)
    }

    @Test
    fun `negative and infinite idle timeouts are rejected`() {
        assertFailsWith<IllegalArgumentException> { ResourceManager((-1).nanoseconds) }
        assertFailsWith<IllegalArgumentException> { ResourceManager(Duration.INFINITE) }
    }

    @Test
    fun `first manager entry preserves startup leases and shutdown reentry invalidates old generation`() {
        // Arrange
        val manager = ResourceManager()
        manager.registerLoader<Value> { Value("value") }
        lateinit var startup: AssetLease<Value>
        val earlier = object : Manager {
            override fun onEnter() {
                startup = manager.acquire(assetKey("file"))
            }
        }
        ManagersRegistry.register(earlier)
        ManagersRegistry.register(manager)

        // Act / Assert
        ManagersRegistry.enter()
        val original = startup.value
        assertSame(original, startup.value)
        manager.onEnter()
        assertEquals(0, original.closes)
        manager.onExit()
        assertEquals(1, original.closes)
        assertFailsWith<IllegalStateException> { startup.value }
        assertFailsWith<IllegalStateException> { manager.acquire(assetKey<Value>("file")) }
        manager.onEnter()
        manager.acquire(assetKey<Value>("file")).use { assertNotSame(original, it.value) }
        startup.close()
        assertFailsWith<IllegalStateException> { startup.value }
    }

    @Test
    fun `shutdown invalidates all active leases before reverse load order cleanup and aggregates failures`() {
        // Arrange
        val manager = ResourceManager()
        val calls = mutableListOf<String>()
        val firstFailure = IllegalArgumentException("third")
        val laterFailure = IllegalStateException("first")
        lateinit var firstLease: AssetLease<Value>
        manager.registerLoader<Value> { key ->
            Value(key.path) {
                calls += key.path
                assertFailsWith<IllegalStateException> { firstLease.value }
                assertFailsWith<IllegalStateException> { manager.acquire(key) }
                assertFailsWith<IllegalStateException> { manager.registerLoader<Other> { Other() } }
                assertFailsWith<IllegalStateException> { manager.onEnter() }
                manager.onExit()
                when (key.path) {
                    "third" -> throw firstFailure
                    "first" -> throw laterFailure
                }
            }
        }
        firstLease = manager.acquire(assetKey("first"))
        val second = manager.acquire(assetKey<Value>("second"))
        val third = manager.acquire(assetKey<Value>("third"))

        // Act / Assert
        val failure = assertFailsWith<IllegalArgumentException> { manager.onExit() }
        assertSame(firstFailure, failure)
        assertEquals(listOf(laterFailure), failure.suppressed.toList())
        assertEquals(listOf("third", "second", "first"), calls)
        listOf(firstLease, second, third).forEach { lease ->
            assertFailsWith<IllegalStateException> { lease.value }
            lease.close()
            lease.close()
        }
        manager.onExit()
        assertEquals(3, calls.size)
    }

    @Test
    fun `final release failure leaves closed lease and cleared cache and disposal guards reject reacquisition`() {
        // Arrange
        val manager = ResourceManager()
        val key = assetKey<Value>("file")
        val failure = IllegalArgumentException("close")
        var loads = 0
        manager.registerLoader<Value> {
            loads++
            Value("value") {
                assertFailsWith<IllegalStateException> { manager.acquire(key) }
                assertFailsWith<IllegalStateException> { manager.onEnter() }
                assertFailsWith<IllegalStateException> { manager.onExit() }
                throw failure
            }
        }
        val lease = manager.acquire(key)
        val value = lease.value

        // Act / Assert
        assertSame(failure, assertFailsWith<IllegalArgumentException> { lease.close() })
        lease.close()
        assertEquals(1, value.closes)
        assertFailsWith<IllegalStateException> { lease.value }
        val replacement = manager.acquire(key)
        assertEquals(2, loads)
        assertFailsWith<IllegalArgumentException> { replacement.close() }
        manager.onExit()
    }

    @Test
    fun `sweep attempts all expired values after failures and safely releases another snapshot entry`() {
        // Arrange
        var now = 0L
        val manager = ResourceManager(10.nanoseconds)
        manager.clock = { now }
        val failure = IllegalArgumentException("first")
        var calls = 0
        lateinit var second: AssetLease<Value>
        manager.registerLoader<Value> { key ->
            Value(key.path) {
                calls++
                if (key.path == "first") {
                    second.close()
                    assertFailsWith<IllegalStateException> { manager.acquire(assetKey<Value>("second")) }
                    throw failure
                }
            }
        }
        manager.acquire(assetKey<Value>("first")).close()
        second = manager.acquire(assetKey("second"))
        val value = second.value
        now = 10

        // Act / Assert
        assertSame(failure, assertFailsWith<IllegalArgumentException> { manager.onUpdate(0f) })
        assertEquals(1, calls)
        now = 20
        manager.onUpdate(0f)
        assertEquals(1, value.closes)
        assertEquals(2, calls)
        manager.onExit()
    }

    @Test
    fun `shutdown during load disposes its result and cannot overwrite a new generation`() {
        // Arrange
        val manager = ResourceManager()
        lateinit var next: AssetLease<Other>
        val old = Value("invalidated")
        manager.registerLoader<Other> { Other() }
        manager.registerLoader<Value> {
            manager.onExit()
            manager.onEnter()
            next = manager.acquire(assetKey("next"))
            old
        }

        // Act / Assert
        assertFailsWith<IllegalStateException> { manager.acquire(assetKey<Value>("old")) }
        assertEquals(1, old.closes)
        assertNotNull(next.value)
        next.close()
        manager.onExit()
    }

    @Test
    fun `earlier registry shutdown failure still reaches resource cleanup`() {
        // Arrange
        val manager = ResourceManager()
        manager.registerLoader<Value> { Value("value") }
        val first = IllegalArgumentException("earlier")
        ManagersRegistry.register(object : Manager {
            override fun onExit(): Unit = throw first
        })
        ManagersRegistry.register(manager)
        val lease = manager.acquire(assetKey<Value>("file"))
        val value = lease.value

        // Act / Assert
        assertSame(first, assertFailsWith<IllegalArgumentException> { ManagersRegistry.exit() })
        assertEquals(1, value.closes)
        assertFailsWith<IllegalStateException> { lease.value }
        lease.close()
    }
}
