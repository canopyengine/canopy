package canopy.core.managers

import kotlin.collections.set
import kotlin.reflect.KClass

class InjectionManager : Manager {

    private val dependenciesMap = mutableMapOf<KClass<*>, () -> Any?>()

    // ===============================
    //      DEPENDENCY INJECTION
    // ===============================
    fun <T : Any> registerInjectable(
        kClass: KClass<T>,
        injectable: () -> T?,
    ) {
        require(kClass !in dependenciesMap) {}
        dependenciesMap[kClass] = injectable
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        val entry = dependenciesMap[kClass]
        requireNotNull(entry) { "" }
        return entry() as T
    }

}
