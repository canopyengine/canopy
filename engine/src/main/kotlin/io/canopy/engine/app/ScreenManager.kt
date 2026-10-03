package io.canopy.engine.app

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.Manager

/**
 * Registers screens by concrete type and forwards frame callbacks to the current screen. Use on the lifecycle
 * thread.
 */
class ScreenManager : Manager {

    /* ============================================================
     * Registry
     * ============================================================ */
    internal val screenRegistry = ScreenRegistry()

    private val screens = linkedMapOf<KClass<out Screen>, Screen>()

    /* ============================================================
     * State
     * ============================================================ */

    /** The active screen, or null before navigation and after teardown. */
    var current: Screen? = null
        private set

    /* ============================================================
     * Registration
     * ============================================================ */

    /** Registers a screen, replacing any previous registration for its concrete type. */
    fun register(screen: Screen) {
        screens[screen::class] = screen
    }

    /** Removes a registration and exits it if it is the current screen. */
    fun <T : Screen> remove(type: KClass<T>) {
        val removed = screens.remove(type)

        if (current === removed) {
            current?.onExit()
            current = null
        }
    }

    /* ============================================================
     * Navigation
     * ============================================================ */

    /** Exits the current screen and enters the registered target; fails if the type is unregistered. */
    fun <T : Screen> start(type: KClass<T>) {
        val next = screens[type]
            ?: error("Screen not registered: ${type.qualifiedName}")

        if (current === next) return

        current?.onExit()
        current = next
        current?.onEnter()
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

    override fun onExit() {
        current?.onExit()
        current = null

        screens.values.forEach { it.onExit() }
        screens.clear()
    }

    companion object {
        internal var screenManagerBuilder: ScreenManager.() -> Unit = {}
    }
}

/** Installs the screen registration block used by ScreenManager during application initialization. */
fun App<*>.screens(handler: ScreenRegistry.() -> Unit) {
    ScreenManager.screenManagerBuilder = { screenRegistry.apply(handler) }
}
