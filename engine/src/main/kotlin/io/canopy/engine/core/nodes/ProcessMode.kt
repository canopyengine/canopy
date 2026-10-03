package io.canopy.engine.core.nodes

/** Determines when a node receives frame, physics, input and tree-system processing. */
enum class ProcessMode {
    /** Uses the nearest explicit ancestor mode; an inherited root is [Pausable]. */
    Inherit,

    /** Processes only while the application is running. */
    Pausable,

    /** Processes only while the application is paused, for example a pause menu. */
    WhenPaused,

    /** Processes regardless of application pause state. */
    Always,

    /** Skips processing regardless of application pause state. Explicit descendants may still process. */
    Disabled,
}
