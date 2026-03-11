package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import io.canopy.engine.logging.EngineLogs

/**
 * Simple dependency registry used by the engine.
 *
 * This is intentionally lightweight: it stores provider functions keyed by type.
 * It is closer to a "service locator" than a full DI container:
 * - No constructor injection
 * - No scopes
 * - No graph resolution
 *
 * Providers are invoked on demand when [inject] is called. A provider may return:
 * - a singleton instance (if it captures/stores one)
 * - a new instance per call (factory behavior)
 *
 * Thread-safety:
 * This implementation is not synchronized. It assumes registration happens during
 * startup (single-threaded) and lookups happen in a controlled environment.
 */
class InjectionManager : Manager {

    /**
     * Maps a type to a provider function.
     *
     * Note: despite the "weakly" comment, this is a strong reference map.
     * Providers may still choose to return weak references internally if desired.
     */
    private val providers = mutableMapOf<KClass<*>, () -> Any>()

    private val log = EngineLogs.subsystem("di")

    /* ============================================================
     * Registration
     * ============================================================ */

    /**
     * Registers a provider for the given type.
     *
     * @throws IllegalArgumentException if a provider for [kClass] is already registered
     */
    fun <T : Any> registerInjectable(kClass: KClass<T>, provider: () -> T) {
        val typeName = kClass.qualifiedName ?: kClass.simpleName ?: "UnknownType"

        require(kClass !in providers) {
            "Injectable of type $typeName is already registered."
        }

        providers[kClass] = provider

        log.info(
            "event" to "di.register",
            "type" to typeName,
            "size" to providers.size
        ) { "Registered injectable" }
    }

    /**
     * DSL helper:
     * ```
     * manager<InjectionManager>() += { MyService() }
     * ```
     */
    inline operator fun <reified T : Any> plusAssign(noinline provider: () -> T) =
        registerInjectable(T::class, provider)

    /* ============================================================
     * Resolution
     * ============================================================ */

    /**
     * Resolves an instance for the requested type by calling its provider.
     *
     * Logging:
     * Resolution is typically frequent, so successful lookups are logged at DEBUG.
     * Missing registrations and type mismatches are logged at ERROR.
     *
     * @throws IllegalStateException if the type is not registered or provider returns a wrong type
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        val typeName = kClass.qualifiedName ?: kClass.simpleName ?: "UnknownType"

        // Debug level: injection happens a lot; info would be noisy.
        log.debug(
            "event" to "di.inject",
            "type" to typeName
        ) { "Resolving injectable" }

        val provider = providers[kClass] ?: run {
            log.error(
                "event" to "di.missing",
                "type" to typeName,
                "registered" to providers.size
            ) { "Injectable not registered" }

            throw IllegalStateException("Injectable of type $typeName wasn't registered.")
        }

        return provider() as? T ?: run {
            log.error(
                "event" to "di.type_mismatch",
                "type" to typeName
            ) { "Provider returned unexpected type" }

            throw IllegalStateException("Injectable of type $typeName was not injected (type mismatch).")
        }
    }

    /* ============================================================
     * Manager lifecycle
     * ============================================================ */

    override fun setup() {
        log.debug("event" to "di.setup") { "Setup" }
    }

    override fun teardown() {
        log.debug("event" to "di.teardown", "size" to providers.size) { "Teardown" }
        providers.clear()
    }
}

/* ------------------------------------------------------------------
 * Convenience helpers
 * ------------------------------------------------------------------ */

/**
 * Resolves an instance from the global [InjectionManager].
 *
 * Example:
 * ```
 * val assets = inject<AssetsManager>()
 * ```
 */
inline fun <reified T : Any> inject(): T = manager<InjectionManager>().inject(T::class)

/**
 * Lazily resolves an instance on first access.
 *
 * Useful for screens/systems where construction happens before managers are ready.
 */
inline fun <reified T : Any> lazyInject() = lazy { inject<T>() }
