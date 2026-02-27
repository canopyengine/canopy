package io.canopy.engine.core.managers

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import io.canopy.engine.logging.engine.EngineLogs

class InjectionManager : Manager {

    // Store provider functions (weakly)
    private val dependenciesMap = mutableMapOf<KClass<*>, () -> Any>()

    private val log = EngineLogs.subsystem("di")

    // ===============================
    //      DEPENDENCY INJECTION
    // ===============================
    fun <T : Any> registerInjectable(kClass: KClass<T>, injectable: () -> T) {
        val typeName = kClass.qualifiedName ?: kClass.simpleName ?: "UnknownType"

        require(kClass !in dependenciesMap) {
            "Injectable of type $typeName is already registered."
        }

        dependenciesMap[kClass] = injectable

        log.info(
            "event" to "di.register",
            "type" to typeName,
            "size" to dependenciesMap.size
        ) { "Registered injectable" }
    }

    inline operator fun <reified T : Any> plusAssign(noinline injectable: () -> T) =
        registerInjectable(T::class, injectable)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        val typeName = kClass.qualifiedName ?: kClass.simpleName ?: "UnknownType"

        // Debug level: injection happens a lot; info would spam
        log.debug(
            "event" to "di.inject",
            "type" to typeName
        ) { "Resolving injectable" }

        val ref = dependenciesMap[kClass]
            ?: run {
                log.error(
                    "event" to "di.missing",
                    "type" to typeName,
                    "registered" to dependenciesMap.size
                ) { "Injectable not registered" }
                throw IllegalStateException("Injectable of type $typeName wasn't registered.")
            }

        return ref() as? T
            ?: run {
                log.error(
                    "event" to "di.type_mismatch",
                    "type" to typeName
                ) { "Provider returned unexpected type" }
                throw IllegalStateException("Injectable of type $typeName was not injected (type mismatch).")
            }
    }

    override fun setup() {
        log.debug("event" to "di.setup") { "Setup" }
    }

    override fun teardown() {
        log.debug("event" to "di.teardown", "size" to dependenciesMap.size) { "Teardown" }
        dependenciesMap.clear()
    }
}

inline fun <reified T : Any> inject(): T = manager<InjectionManager>().inject(T::class)

inline fun <reified T : Any> lazyInject(): ReadOnlyProperty<Any?, T> = ReadOnlyProperty { _, _ -> inject<T>() }
