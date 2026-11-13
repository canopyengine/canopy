package io.canopy.engine.app

/**
 * Host-reported state, independent of screen navigation and Node visibility.
 * Hosts report changes on their lifecycle thread, before dispatching a frame. Unsupported states retain defaults;
 * terminal and headless hosts do not infer window focus or minimization. Suspension means the OS stopped execution.
 */
data class HostState(
    val focused: Boolean = true,
    val visible: Boolean = true,
    val minimized: Boolean = false,
    val suspended: Boolean = false,
) {
    /** Whether game/UI input can be accepted. Losing eligibility discards queued input and held snapshots. */
    val acceptsInput: Boolean get() = focused && canPresent

    /** Whether presentation is useful. Unfocused visible windows may still render. */
    val canPresent: Boolean get() = visible && !minimized && !suspended
}

/**
 * Automatic gameplay pause policy, configured with App.lifecycle before startup.
 * Manual pause is independent: host restoration never clears a player's pause. Suspension always stops frames,
 * including Always/WhenPaused Nodes; these options only govern gameplay while the host is still executing.
 */
data class LifecyclePolicy(
    var pauseWhenUnfocused: Boolean = false,
    var pauseWhenMinimized: Boolean = true,
    var pauseWhenHidden: Boolean = true,
) {
    internal fun pauses(state: HostState): Boolean = state.suspended ||
        (pauseWhenUnfocused && !state.focused) ||
        (pauseWhenMinimized && state.minimized) ||
        (pauseWhenHidden && !state.visible)
}
