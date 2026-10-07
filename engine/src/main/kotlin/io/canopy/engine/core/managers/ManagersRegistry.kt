package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext

/**
 * Global registry for engine managers.
 *
 * Registration, lookup, lifecycle dispatch, and teardown are expected to run
 * serially on the game thread. The registry's maps are not thread-safe.
 * Registration, removal, scope replacement and nested lifecycle dispatch are rejected during
 * lifecycle callbacks. Lookups remain available; mutation is allowed again after the pass finishes.
 */
object ManagersRegistry {

    private val log = EngineLogs.managers

    private val managers = linkedMapOf<KClass<out Manager>, Manager>()
    private val resolvedCache = mutableMapOf<KClass<out Manager>, Manager>()
    private var exiting = false
    private var dispatching = false

    /** Registers a unique manager on the engine thread; rejected during lifecycle dispatch and teardown. */
    fun <T : Manager> register(manager: T) {
        check(!exiting && !dispatching) { "Cannot register managers during lifecycle dispatch or teardown" }
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

    /** Removes a matching registration without calling onExit; rejected during lifecycle dispatch and teardown. */
    fun <T : Manager> unregister(klass: KClass<T>) {
        check(!exiting && !dispatching) { "Cannot unregister managers during lifecycle dispatch or teardown" }
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

    /** Resolves an assignable manager on the game thread; absence and ambiguity throw IllegalStateException. */
    fun <T : Manager> getManager(clazz: KClass<T>): T = getManagerOrNull(clazz)
        ?: throw IllegalStateException(
            """
            [MANAGERS REGISTRY]
            No ${clazz.simpleName} registered!
            To fix this: register it into the Managers Registry!
            """.trimIndent()
        )

    /** Resolves an assignable manager, returning null only for absence; ambiguity still throws. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Manager> getManagerOrNull(clazz: KClass<T>): T? {
        resolvedCache[clazz]?.let { return it as T }
        val resolved = resolveManagerOrNull(clazz) ?: return null
        resolvedCache[clazz] = resolved
        return resolved as T
    }

    /** Enters managers in registration order; callbacks cannot mutate or redispatch this registry. */
    fun enter() = dispatch {
        check(!exiting) { "Cannot enter managers during teardown" }
        log.info("event" to "managers.setup", "registered" to managers.size) {
            "Bootstrapping managers"
        }

        managers.values.forEach { manager ->
            val name = manager::class.simpleName ?: "UnknownManager"
            LogContext.with("manager" to name) {
                log.debug { "setup()" }
                manager.onEnter()
            }
        }

        log.info("event" to "managers.setup.done") { "Finished bootstrapping managers" }
    }

    /** Dispatches frames; while [paused], scenes receive real time and other managers receive zero. */
    fun update(delta: Float, paused: Boolean = false) = dispatch {
        check(!exiting) { "Cannot update managers during teardown" }
        LogContext.with("delta" to delta, "registered" to managers.size) {
            log.trace("event" to "managers.update") { "Updating managers" }
        }

        managers.values.forEach { manager ->
            val name = manager::class.simpleName ?: "UnknownManager"

            try {
                LogContext.with("manager" to name, "delta" to delta) {
                    manager.onUpdate(if (paused && manager !is SceneManager) 0f else delta)
                }
            } catch (t: Throwable) {
                log.error(
                    t = t,
                    "event" to "manager.update.error",
                    "manager" to name
                ) { "Manager update failed" }
                throw t
            }
        }
    }

    /** Dispatches physics; while [paused], only scene managers process eligible nodes. */
    fun physicsUpdate(delta: Float, paused: Boolean = false) = dispatch {
        check(!exiting) { "Cannot update managers during teardown" }
        LogContext.with("delta" to delta, "registered" to managers.size) {
            log.trace("event" to "managers.physics_update") { "Physics updating managers" }
        }

        managers.values.forEach { manager ->
            if (paused && manager !is SceneManager) return@forEach
            val name = manager::class.simpleName ?: "UnknownManager"
            try {
                LogContext.with("manager" to name, "delta" to delta) {
                    manager.onPhysicsUpdate(delta)
                }
            } catch (t: Throwable) {
                log.error(
                    t = t,
                    "event" to "manager.physics_update.error",
                    "manager" to name
                ) { "Manager physics update failed" }
                throw t
            }
        }
    }

    /** Dispatches dimensions in registration order; callbacks cannot mutate or redispatch this registry. */
    fun resize(width: Int, height: Int) = dispatch {
        check(!exiting) { "Cannot resize managers during teardown" }
        LogContext.with("width" to width, "height" to height, "registered" to managers.size) {
            log.info("event" to "managers.resize") { "Resizing managers" }
        }

        managers.values.forEach { manager ->
            val name = manager::class.simpleName ?: "UnknownManager"

            try {
                LogContext.with("manager" to name, "width" to width, "height" to height) {
                    manager.onResize(width, height)
                }
            } catch (t: Throwable) {
                log.error(
                    t = t,
                    "event" to "manager.resize.error",
                    "manager" to name
                ) { "Manager resize failed" }
                throw t
            }
        }
    }

    /**
     * Attempts every manager's shutdown in registration order on the game thread, then clears registry and cache.
     * The first failure is rethrown with later distinct failures suppressed. Nested shutdown is harmless;
     * registration, removal and other lifecycle dispatch are rejected until shutdown completes.
     * Lookup remains available during callbacks so managers can finish dependent cleanup.
     */
    fun exit() {
        if (exiting) return
        checkCanExit()
        exiting = true
        var failure: Throwable? = null
        try {
            log.info("event" to "managers.teardown", "registered" to managers.size) {
                "Tearing down managers"
            }
            managers.values.toList().forEach { manager ->
                try {
                    val name = manager::class.simpleName ?: "UnknownManager"
                    LogContext.with("manager" to name) {
                        log.debug { "teardown()" }
                        manager.onExit()
                    }
                } catch (error: Throwable) {
                    val first = failure
                    if (first == null) {
                        failure = error
                    } else if (first !== error) {
                        first.addSuppressed(error)
                    }
                }
            }
        } finally {
            managers.clear()
            invalidateCache()
            exiting = false
        }
        log.info("event" to "managers.teardown.done") { "Finished tearing down managers" }
        failure?.let { throw it }
    }

    /** Replaces the global scope and enters the new registrations; rejected during lifecycle dispatch and teardown. */
    fun withScope(block: ManagersRegistry.() -> Unit) {
        log.info("event" to "managers.scope") { "Creating scoped Managers registry..." }
        exit()
        block()
        enter()
        log.info("event" to "managers.scope.done") { "Finished creating scoped Managers registry" }
    }

    internal fun checkCanExit() {
        check(!dispatching && !exiting) { "Cannot exit managers during lifecycle dispatch or teardown" }
    }

    private inline fun dispatch(block: () -> Unit) {
        check(!exiting && !dispatching) { "Cannot redispatch managers during lifecycle dispatch or teardown" }
        dispatching = true
        try {
            block()
        } finally {
            dispatching = false
        }
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
        val visited = linkedSetOf<Class<*>>()

        fun visit(type: Class<*>) {
            if (!visited.add(type)) return
            type.superclass?.let(::visit)
            type.interfaces.forEach(::visit)
        }

        visit(java)

        return visited
            .map { it.kotlin }
            .filter { it.isConcreteManagerLookupType() }
            .map { it as KClass<out Manager> }
            .toSet()
    }

    private fun KClass<*>.isSubclassOfManager(): Boolean = Manager::class.java.isAssignableFrom(this.java)
    private fun KClass<*>.isConcreteManagerLookupType(): Boolean = isSubclassOfManager() && this != Manager::class
}

/** Immediately resolves the current global registration on the game thread; missing registrations throw. */
inline fun <reified T : Manager> manager(): T = ManagersRegistry.getManager(T::class)

/** Immediately resolves the current global registration; only absence returns null, and ambiguity throws. */
inline fun <reified T : Manager> managerOrNull(): T? = ManagersRegistry.getManagerOrNull(T::class)

/** Caches the first successful global lookup; subsequent registration changes are not observed. */
inline fun <reified T : Manager> lazyManager() = lazy { manager<T>() }
