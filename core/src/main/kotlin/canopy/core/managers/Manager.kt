package anchors.framework.managers

/**
 * Manager interface defining setup and teardown methods for system managers.
 * Each manager implementing this interface should provide its own setup and teardown logic.
 * Each manager represents a distinct singleton in the game architecture, responsible for a specific aspect of the game's functionality.
 */
interface Manager {
    // Logger instance for logging messages related to the Manager

    /**
     * Initializes the manager, setting up necessary resources or configurations.
     * This method is called when the manager is first created or activated.
     */
    fun setup() = Unit

    /**
     * Cleans up resources or configurations used by the manager.
     * This method is called when the manager is no longer needed or is being deactivated.
     */
    fun teardown() = Unit
}
