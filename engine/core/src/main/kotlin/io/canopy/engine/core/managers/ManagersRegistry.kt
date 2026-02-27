package io.canopy.engine.core.managers

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.engine.EngineLogs

/**
 * ManagersRegistry is responsible for managing a collection of Manager instances.
 */
object ManagersRegistry {

    // Engine subsystem logger (consistent + routable)
    private val log = EngineLogs.managers

    private val managers = mutableMapOf<KClass<out Manager>, Manager>()

    fun <T : Manager> register(manager: T) {
        val key = manager::class

        require(managers[key] == null) {
            // NOTE: require message should be cheap; we can just return a string
            "Manager ${key.simpleName} is already registered"
        }

        managers[key] = manager

        log.debug("manager" to key.simpleName) {
            "Registered manager"
        }
    }
    inline operator fun <reified T : Manager> T.unaryPlus() = register(this)

    fun <T : Manager> unregister(klass: KClass<T>) {
        log.debug("manager" to klass.simpleName) { "Unregistered manager" }
        managers.remove(klass)
    }
    inline operator fun <reified T : Manager> KClass<T>.unaryMinus() = unregister(this)

    fun <T : Manager> has(clazz: KClass<T>) = clazz in managers
    operator fun contains(clazz: KClass<*>) = clazz in managers

    @Suppress("UNCHECKED_CAST")
    fun <T : Manager> getManager(clazz: KClass<T>): T = managers[clazz] as? T
        ?: throw IllegalStateException(
            """
                [MANAGERS REGISTRY]
                No ${clazz.simpleName} registered!
                To fix this: register it into the Managers Registry!
            """.trimIndent()
        )

    fun setup() {
        log.info("registered" to managers.size) {
            "Bootstrapping managers"
        }

        managers.values.forEach { manager ->
            val name = manager::class.simpleName ?: "UnknownManager"
            LogContext.with("manager" to name) {
                log.debug { "setup()" }
                manager.setup()
            }
        }

        log.info { "Finished bootstrapping managers" }
    }

    fun teardown() {
        log.info("registered" to managers.size) {
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
        log.info { "Finished tearing down managers" }
    }

    fun withScope(block: ManagersRegistry.() -> Unit) {
        log.info { "Creating scoped Managers registry..." }
        teardown()
        block()
        setup()
        log.info { "Finished creating scoped Managers registry" }
    }
}

inline fun <reified T : Manager> manager(): T = ManagersRegistry.getManager(T::class)

inline fun <reified T : Manager> lazyManager() = lazy { manager<T>() }
