package io.canopy.engine.app

/**
 * Application state whose callbacks run on the serialized lifecycle thread.
 *
 * Each visit calls [onEnter], then [onActive] if that visit is still current. Leaving calls [onInactive],
 * then [onExit].
 * Returning to a registered screen starts another visit; initialization is not restricted to the first visit.
 * Only the current screen receives frame, physics and resize callbacks.
 * A screen does not own automatic scene teardown; replace the scene explicitly when appropriate.
 * Navigation may be requested from [onEnter] or [onActive], but not from [onInactive] or [onExit].
 */
abstract class Screen {

    /**
     * Called at the start of each visit, before [onActive].
     */
    open fun onEnter() {}

    /**
     * Called after [onEnter] when this screen becomes active.
     */
    open fun onActive() {}

    /**
     * Called when this screen stops being current, before [onExit].
     */
    open fun onInactive() {}

    /**
     * Called every frame.
     *
     * @param delta Time since last frame (in seconds)
     */
    open fun onUpdate(delta: Float) {}

    /** Called at each fixed-step physics tick while this screen is active. */
    open fun onPhysicsUpdate(delta: Float) {}

    /**
     * Called for host resize notifications while current, and after activation when the manager has
     * retained host dimensions from a previous notification. Units are backend-specific (terminal cells
     * or graphical pixels). Navigation from this callback is allowed; obsolete visits receive no replay.
     * A failed activation replay can be retried by starting the same current screen without re-entering it.
     */
    open fun onResize(width: Int, height: Int) {}

    /**
     * Called once when each visit ends, including navigation, active replacement and shutdown.
     */
    open fun onExit() {}
}
