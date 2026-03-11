package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext

/**
 * Global registry responsible for storing and managing engine [Manager] instances.
 *
 * The registry provides:
 * - Registration / lookup of managers by type
 * - A standard lifecycle: [setup] and [teardown]
 * - A small DSL via operator overloads (`+manager`, `-ManagerClass`)
 *
 * Typical boot flow:
 * 1) Register managers (usually during app startup)
 * 2) Call [setup] to initialize them
 * 3) Call [teardown] on shutdown to release resources
 *
 * Notes:
 * - This is a global singleton registry.
 * - Registration order matters if managers depend on each other (setup runs in insertion order).
 * - This implementation is not synchronized; it assumes boot happens on one thread.
 */
object ManagersRegistry {

    /** Engine subsystem logger (consistent + routable). */
    private val log = EngineLogs.managers

    /**
     * Registered managers keyed by their concrete KClass.
     *
     * Using a mutable map means insertion order is preserved (LinkedHashMap behavior),
     * so setup/teardown run deterministically in registration order.
     */
    private val managers = mutableMapOf<KClass<out Manager>, Manager>()

    /* ============================================================
     * Registration API
     * ============================================================ */

    /**
     * Registers a manager instance.
     *
     * @throws IllegalArgumentException if the manager type is already registered
     */
    fun <T : Manager> register(manager: T) {
        val key = manager::class

        require(managers[key] == null) {
            // Keep require message cheap (no heavy formatting).
            "Manager ${key.simpleName} is already registered"
        }

        managers[key] = manager

        log.debug("event" to "managers.register", "manager" to key.simpleName) {
            "Registered manager"
        }
    }

    /**
     * DSL helper:
     * `+AssetsManager()`
     */
    inline operator fun <reified T : Manager> T.unaryPlus() = register(this)

    /**
     * Unregisters a manager type (does not call teardown).
     *
     * Use this when you need to remove a manager from the registry,
     * typically in tests or dynamic setups.
     */
    fun <T : Manager> unregister(klass: KClass<T>) {
        log.debug("event" to "managers.unregister", "manager" to klass.simpleName) {
            "Unregistered manager"
        }
        managers.remove(klass)
    }

    /**
     * DSL helper:
     * `-AssetsManager::class`
     */
    inline operator fun <reified T : Manager> KClass<T>.unaryMinus() = unregister(this)

    /* ============================================================
     * Lookup / inspection API
     * ============================================================ */

    /** Returns true if a manager of the given type is registered. */
    fun <T : Manager> has(clazz: KClass<T>) = clazz in managers

    /** Kotlin-friendly containment check: `if (FooManager::class in ManagersRegistry) ...` */
    operator fun contains(clazz: KClass<*>) = clazz in managers

    /**
     * Retrieves the manager instance for [clazz].
     *
     * @throws IllegalStateException if the manager is not registered
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Manager> getManager(clazz: KClass<T>): T = managers[clazz] as? T
        ?: throw IllegalStateException(
            """
                [MANAGERS REGISTRY]
                No ${clazz.simpleName} registered!
                To fix this: register it into the Managers Registry!
            """.trimIndent()
        )

    /* ============================================================
     * Lifecycle API
     * ============================================================ */

    /**
     * Initializes all registered managers by calling [Manager.setup].
     *
     * Each manager setup runs with its name in MDC (`manager=<Name>`) to make logs easier to filter.
     */
    fun setup() {
        log.info("event" to "managers.setup", "registered" to managers.size) {
            "Bootstrapping managers"
        }

        managers.values.forEach { manager ->
            val name = manager::class.simpleName ?: "UnknownManager"
            LogContext.with("manager" to name) {
                log.debug { "setup()" }
                manager.setup()
            }
        }

        log.info("event" to "managers.setup.done") { "Finished bootstrapping managers" }
    }

    /**
     * Shuts down all registered managers by calling [Manager.teardown],
     * then clears the registry.
     *
     * Note: teardown runs in registration order (same as setup).
     * If you ever need reverse-order teardown for dependency reasons, this is the place to change it.
     */
    fun teardown() {
        log.info("event" to "managers.teardown", "registered" to managers.size) {
            "Tearing down managers"
        }

        managers.values.forEach { manager ->
            val name = manager::class.simpleName ?: "UnknownManager"
            LogContext.with("manager" to name) {
                log.debug { "teardown()" }
                manager.teardown()
            }
        }

        managers.clear()
        log.info("event" to "managers.teardown.done") { "Finished tearing down managers" }
    }

    /**
     * Runs [block] with a fresh manager set:
     * - Tears down the current registry
     * - Executes [block] (where callers usually register managers)
     * - Bootstraps the newly registered managers
     *
     * This is primarily useful for tests that need isolation between scenarios.
     */
    fun withScope(block: ManagersRegistry.() -> Unit) {
        log.info("event" to "managers.scope") { "Creating scoped Managers registry..." }
        teardown()
        block()
        setup()
        log.info("event" to "managers.scope.done") { "Finished creating scoped Managers registry" }
    }
}

/* ------------------------------------------------------------------
 * Convenience helpers
 * ------------------------------------------------------------------ */

/**
 * Retrieves a manager instance from [ManagersRegistry].
 *
 * Example:
 * `val assets = manager<AssetsManager>()`
 */
inline fun <reified T : Manager> manager(): T = ManagersRegistry.getManager(T::class)

/**
 * Lazy version of [manager], resolved on first access.
 */
inline fun <reified T : Manager> lazyManager() = lazy { manager<T>() }
