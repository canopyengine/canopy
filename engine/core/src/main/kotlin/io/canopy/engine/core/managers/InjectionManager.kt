package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import java.lang.ref.WeakReference
import io.canopy.engine.core.log.logger

class InjectionManager : Manager {
    private val dependenciesMap = mutableMapOf<KClass<*>, WeakReference<() -> Any>>()

    private val logger = logger<InjectionManager>()

    // ===============================
    //      DEPENDENCY INJECTION
    // ===============================
    fun <T : Any> registerInjectable(kClass: KClass<T>, injectable: () -> T) {
        logger.info { "Injected ${kClass.simpleName}" }
        require(kClass !in dependenciesMap) { "The injectable of type ${kClass.simpleName} is already registered." }
        dependenciesMap[kClass] = WeakReference(injectable)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        logger.info { "Injecting ${kClass.simpleName}..." }
        val entry = dependenciesMap[kClass]
        requireNotNull(entry) {
            logger.error { "The injectable of type ${kClass.simpleName} wasn't registered, so can't be injected" }
        }
        return entry.get()?.invoke() as? T ?: error("The injectable of type ${kClass.simpleName} was not injected.")
    }
}
