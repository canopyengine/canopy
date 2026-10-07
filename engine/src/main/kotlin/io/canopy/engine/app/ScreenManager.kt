package io.canopy.engine.app

import kotlin.reflect.KClass
import io.canopy.engine.core.CleanupFailures
import io.canopy.engine.core.managers.Manager

/**
 * Registers screens by concrete type and forwards frame callbacks to the current screen. Use on the lifecycle
 * thread. Navigation and registration are rejected inside onInactive/onExit to prevent nested teardown.
 */
class ScreenManager : Manager {

    /* ============================================================
     * Registry
     * ============================================================ */
    internal val screenRegistry = ScreenRegistry()

    private val screens = linkedMapOf<KClass<out Screen>, Screen>()
    private var leaving = false
    private var visitVersion = 0L

    /* ============================================================
     * State
     * ============================================================ */

    /** The active screen, or null before navigation and after teardown. */
    var current: Screen? = null
        private set

    /* ============================================================
     * Registration
     * ============================================================ */

    /**
     * Registers a screen by concrete type. Replacing the active instance ends its visit and clears [current].
     * Registering the same instance is a no-op; the replacement is started explicitly.
     */
    fun register(screen: Screen) {
        check(!leaving) { "Cannot register screens from onInactive or onExit" }
        val previous = screens[screen::class]
        if (previous === screen) return
        if (previous != null && current === previous) leaveCurrent()
        screens[screen::class] = screen
    }

    /** Removes a registration and exits it if it is the current screen. */
    fun <T : Screen> remove(type: KClass<T>) {
        check(!leaving) { "Cannot remove screens from onInactive or onExit" }
        val removed = screens.remove(type)

        if (removed != null && current === removed) leaveCurrent()
    }

    /* ============================================================
     * Navigation
     * ============================================================ */

    /**
     * Ends the current visit, then calls the target's [Screen.onEnter] and [Screen.onActive].
     * Starting the current instance is a no-op; an unregistered type fails before any callbacks.
     */
    fun <T : Screen> start(type: KClass<T>) {
        check(!leaving) { "Cannot navigate from onInactive or onExit" }
        val next = screens[type]
            ?: error("Screen not registered: ${type.qualifiedName}")

        if (current === next) return

        leaveCurrent()
        current = next
        val visit = ++visitVersion
        next.onEnter()
        // onEnter may navigate to another screen; do not activate a screen that already left.
        if (current === next && visitVersion == visit) next.onActive()
    }

    /* ============================================================
     * Frame lifecycle
     * ============================================================ */

    override fun onEnter() {
        screenManagerBuilder()
    }

    override fun onUpdate(delta: Float) {
        current?.onUpdate(delta)
    }

    override fun onPhysicsUpdate(delta: Float) {
        current?.onPhysicsUpdate(delta)
    }

    override fun onResize(width: Int, height: Int) {
        current?.onResize(width, height)
    }

    /* ============================================================
     * Teardown
     * ============================================================ */

    /** Ends only the active visit and clears registrations. Repeated teardown is a no-op. */
    override fun onExit() {
        try {
            leaveCurrent()
        } finally {
            screens.clear()
        }
    }

    private fun leaveCurrent() {
        val previous = current ?: return
        // Clear first so repeated teardown cannot exit the same visit again.
        current = null
        visitVersion++
        leaving = true
        val failures = CleanupFailures()
        try {
            failures.attempt { previous.onInactive() }
            failures.attempt { previous.onExit() }
        } finally {
            leaving = false
        }
        failures.rethrow()
    }

    companion object {
        internal var screenManagerBuilder: ScreenManager.() -> Unit = {}
    }
}

/** Installs the screen registration block used by ScreenManager during application initialization. */
fun App<*>.screens(handler: ScreenRegistry.() -> Unit) {
    ScreenManager.screenManagerBuilder = { screenRegistry.apply(handler) }
}
