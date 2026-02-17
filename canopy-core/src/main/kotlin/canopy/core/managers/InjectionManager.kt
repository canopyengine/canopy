package canopy.core.managers

import kotlin.collections.set
import kotlin.reflect.KClass
import canopy.core.logging.logger

class InjectionManager : Manager {
    private val dependenciesMap = mutableMapOf<KClass<*>, () -> Any>()

    private val logger = logger<InjectionManager>()

    // ===============================
    //      DEPENDENCY INJECTION
    // ===============================
    fun <T : Any> registerInjectable(kClass: KClass<T>, injectable: () -> T) {
        logger.info { "Injected ${kClass.simpleName}" }
        require(kClass !in dependenciesMap) { "The injectable of type ${kClass.simpleName} is already registered." }
        dependenciesMap[kClass] = injectable
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        logger.info { "Injecting ${kClass.simpleName}..." }
        val entry = dependenciesMap[kClass]
        requireNotNull(entry) { "The injectable of type ${kClass.simpleName} wasn't registered, so can't be injected" }
        return entry() as T
    }
}
