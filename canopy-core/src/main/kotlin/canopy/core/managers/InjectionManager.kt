package canopy.core.managers

import kotlin.collections.set
import kotlin.reflect.KClass

class InjectionManager : Manager {
    private val dependenciesMap = mutableMapOf<KClass<*>, () -> Any>()

    // ===============================
    //      DEPENDENCY INJECTION
    // ===============================
    fun <T : Any> registerInjectable(
        kClass: KClass<T>,
        injectable: () -> T,
    ) {
        require(kClass !in dependenciesMap) { "The injectable of type ${kClass.simpleName} is already registered." }
        dependenciesMap[kClass] = injectable
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        val entry = dependenciesMap[kClass]
        requireNotNull(entry) { "The injectable of type ${kClass.simpleName} wasn't registered, so can't be injected" }
        return entry() as T
    }
}
