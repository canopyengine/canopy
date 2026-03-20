package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext

object ManagersRegistry {

    private val log = EngineLogs.managers

    private val managers = linkedMapOf<KClass<out Manager>, Manager>()
    private val resolvedCache = mutableMapOf<KClass<out Manager>, Manager>()

    fun <T : Manager> register(manager: T) {
        val concreteKey = manager::class

        require(concreteKey !in managers) {
            "Manager ${concreteKey.simpleName} is already registered"
        }

        val conflictingTypes = findConflictingAssignableTypes(manager)

        require(conflictingTypes.isEmpty()) {
            val conflicts = conflictingTypes.joinToString { it.simpleName ?: "<anonymous>" }
            "Manager ${concreteKey.simpleName} conflicts with an existing registration for: $conflicts"
        }

        managers[concreteKey] = manager
        invalidateCache()

        log.debug(
            "event" to "managers.register",
            "manager" to concreteKey.simpleName
        ) { "Registered manager" }
    }

    inline operator fun <reified T : Manager> T.unaryPlus() = register(this)

    fun <T : Manager> unregister(klass: KClass<T>) {
        val removed = resolveRegistrationKey(klass)?.let { managers.remove(it) }
        if (removed != null) invalidateCache()

        log.debug(
            "event" to "managers.unregister",
            "manager" to klass.simpleName,
            "removed" to (removed != null)
        ) { "Unregistered manager" }
    }

    inline operator fun <reified T : Manager> KClass<T>.unaryMinus() = unregister(this)

    fun <T : Manager> has(clazz: KClass<T>): Boolean =
        clazz.isSubclassOfManager() && resolveManagerOrNull(clazz) != null

    operator fun contains(clazz: KClass<out Manager>): Boolean = has(clazz)

    @Suppress("UNCHECKED_CAST")
    fun <T : Manager> getManager(clazz: KClass<T>): T {
        resolvedCache[clazz]?.let { return it as T }

        val resolved = resolveManagerOrNull(clazz)
            ?: throw IllegalStateException(
                """
                [MANAGERS REGISTRY]
                No ${clazz.simpleName} registered!
                To fix this: register it into the Managers Registry!
                """.trimIndent()
            )

        resolvedCache[clazz] = resolved
        return resolved as T
    }

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
        invalidateCache()

        log.info("event" to "managers.teardown.done") { "Finished tearing down managers" }
    }

    fun withScope(block: ManagersRegistry.() -> Unit) {
        log.info("event" to "managers.scope") { "Creating scoped Managers registry..." }
        teardown()
        block()
        setup()
        log.info("event" to "managers.scope.done") { "Finished creating scoped Managers registry" }
    }

    private fun invalidateCache() {
        resolvedCache.clear()
    }

    private fun <T : Manager> resolveManagerOrNull(clazz: KClass<T>): Manager? {
        managers[clazz]?.let { return it }

        val matches = managers.values.filter { clazz.isInstance(it) }

        return when (matches.size) {
            0 -> null

            1 -> matches.first()

            else -> throw IllegalStateException(
                buildString {
                    append("Multiple managers match ")
                    append(clazz.simpleName ?: clazz.toString())
                    append(": ")
                    append(matches.joinToString { it::class.simpleName ?: "<anonymous>" })
                }
            )
        }
    }

    private fun findConflictingAssignableTypes(candidate: Manager): List<KClass<out Manager>> {
        val candidateClass = candidate::class
        val existingManagers = managers.values.toList()

        if (existingManagers.isEmpty()) return emptyList()

        val candidateTypes = candidateClass.managerTypeClosure()

        return candidateTypes.filter { type ->
            existingManagers.any { type.isInstance(it) }
        }
    }

    private fun <T : Manager> resolveRegistrationKey(clazz: KClass<T>): KClass<out Manager>? {
        if (clazz in managers) return clazz

        val matches = managers.keys.filter { key ->
            clazz.isInstance(managers[key])
        }

        return when (matches.size) {
            0 -> null

            1 -> matches.first()

            else -> throw IllegalStateException(
                buildString {
                    append("Multiple registered managers match ")
                    append(clazz.simpleName ?: clazz.toString())
                    append(": ")
                    append(matches.joinToString { it.simpleName ?: "<anonymous>" })
                }
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun KClass<out Manager>.managerTypeClosure(): Set<KClass<out Manager>> {
        val visited = linkedSetOf<KClass<*>>()

        fun visit(type: KClass<*>) {
            if (!visited.add(type)) return
            type.superclasses.forEach(::visit)
        }

        visit(this)

        return visited
            .filter { it.isConcreteManagerLookupType() }
            .map { it as KClass<out Manager> }
            .toSet()
    }

    private fun KClass<*>.isSubclassOfManager(): Boolean = Manager::class.java.isAssignableFrom(this.java)

    private fun KClass<*>.isConcreteManagerLookupType(): Boolean = isSubclassOfManager() && this != Manager::class
}

inline fun <reified T : Manager> manager(): T = ManagersRegistry.getManager(T::class)

inline fun <reified T : Manager> lazyManager() = lazy { manager<T>() }
