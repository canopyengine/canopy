package io.canopy.engine.core.managers

/**
 * Access to the active app's diagnostic mode, or the standalone lifecycle scope.
 *
 * Currently used to control the engine execution mode (Normal vs Debug).
 * Systems and tools can check this to enable additional diagnostics,
 * logging, or debug-only behaviors.
 */
object GameManager {

    /**
     * Current execution mode of the engine.
     *
     * Defaults to [ExecutionMode.Normal].
     */
    var executionMode: ExecutionMode
        get() = ManagersRegistry.executionMode
        set(value) {
            ManagersRegistry.executionMode = value
        }

    /**
     * Returns true if the engine is running in debug mode.
     */
    fun isDebugMode(): Boolean = executionMode == ExecutionMode.Debug
}

/**
 * Defines the runtime execution mode of the engine.
 */
enum class ExecutionMode {

    /** Standard runtime mode used by normal gameplay. */
    Normal,

    /**
     * Debug mode used for development and testing.
     *
     * Systems may enable additional logging, debugging visuals,
     * or validation checks when this mode is active.
     */
    Debug,
}
