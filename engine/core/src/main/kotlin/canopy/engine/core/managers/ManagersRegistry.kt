package canopy.engine.core.managers

import kotlin.reflect.KClass
import canopy.engine.core.log.logger
import canopy.engine.core.managers.ManagersRegistry.managers

/**
 * ManagersPipeline is responsible for managing a collection of Manager instances.
 * It allows registering managers and setting them up collectively.
 * Managers must be registered during the application initialization phase to ensure proper setup.
 */
object ManagersRegistry {
    // Logger instance for logging messages related to the ManagersRegistry
    private val logger = logger<ManagersRegistry>()

    // Map to hold registered managers, keyed by their KClass type
    private val managers = mutableMapOf<KClass<out Manager>, Manager>()

    /**
     * Registers a manager instance.
     * This method should be called during the application initialization phase.
     * @param manager The manager instance to register.
     */
    fun <T : Manager> register(manager: T) {
        require(managers[manager::class] == null) {
            logger.info { "Manager ${manager::class.simpleName} is already registered" }
        }
        managers[manager::class] = manager
    }

    fun <T : Manager> has(clazz: KClass<T>) = clazz in managers

    /**
     * Retrieves a registered manager instance by its class type.
     * @param clazz The KClass of the manager to retrieve.
     * @return The manager instance of the specified type.
     * @throws NoSuchElementException if no manager of the specified type is registered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Manager> get(clazz: KClass<T>): T = managers[clazz] as? T
        ?: throw IllegalStateException(
            """

                [MANAGERS REGISTRY]
                No ${clazz.simpleName} registered!
                To fix this: register it into the Managers Registry!

            """.trimIndent()
        )

    /**
     * Sets up all registered managers by invoking their setup methods.
     * This method should be called after all managers have been registered.
     */
    fun setup() {
        logger.info { "Bootstrapping managers[registered: ${managers.size}]..." }
        managers.values.forEach { it.setup() }
    }

    /**
     * Tears down all registered managers by invoking their teardown methods.
     * This method should be called when the application is shutting down or when managers are no longer needed.
     */
    fun teardown() {
        logger.info { "Tearing down managers[registered: ${managers.size}]..." }
        managers.values.forEach { it.teardown() }
        managers.clear()
    }

    /**
     * Convenience method to use a specific set of managers within a scope.
     * It tears down all currently registered managers, registers the provided managers, and sets them up
     * This is useful for testing or scenarios where you want to temporarily switch to a different set of managers.
     * @param managers The managers to be used within the scope.
     */
    fun withScope(block: ManagersRegistry.() -> Unit) {
        teardown()
        block()
        setup()
    }
}
