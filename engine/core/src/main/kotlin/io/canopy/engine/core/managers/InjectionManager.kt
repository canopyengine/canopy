package io.canopy.engine.core.managers

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
            fields = mapOf(
                "event" to "di.register",
                "type" to typeName,
                "size" to dependenciesMap.size
            )
        ) { "Registered injectable" }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        val typeName = kClass.qualifiedName ?: kClass.simpleName ?: "UnknownType"

        // Debug level: injection happens a lot; info would spam
        log.debug(
            fields = mapOf(
                "event" to "di.inject",
                "type" to typeName
            )
        ) { "Resolving injectable" }

        val ref = dependenciesMap[kClass]
            ?: run {
                log.error(
                    fields = mapOf(
                        "event" to "di.missing",
                        "type" to typeName,
                        "registered" to dependenciesMap.size
                    )
                ) { "Injectable not registered" }
                throw IllegalStateException("Injectable of type $typeName wasn't registered.")
            }

        return ref() as? T
            ?: run {
                log.error(
                    fields = mapOf(
                        "event" to "di.type_mismatch",
                        "type" to typeName
                    )
                ) { "Provider returned unexpected type" }
                throw IllegalStateException("Injectable of type $typeName was not injected (type mismatch).")
            }
    }

    override fun setup() {
        log.debug(fields = mapOf("event" to "di.setup")) { "Setup" }
    }

    override fun teardown() {
        log.debug(fields = mapOf("event" to "di.teardown", "size" to dependenciesMap.size)) { "Teardown" }
        dependenciesMap.clear()
    }
}
