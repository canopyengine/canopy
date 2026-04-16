package io.canopy.engine.app

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.Manager

class ScreenManager : Manager {

    /* ============================================================
     * Registry
     * ============================================================ */
    internal val screenRegistry = ScreenRegistry()

    private val screens = linkedMapOf<KClass<out Screen>, Screen>()

    /* ============================================================
     * State
     * ============================================================ */

    var current: Screen? = null
        private set

    /* ============================================================
     * Registration
     * ============================================================ */

    fun register(screen: Screen) {
        screens[screen::class] = screen
    }

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

fun App<*>.screens(handler: ScreenRegistry.() -> Unit) {
    ScreenManager.screenManagerBuilder = { screenRegistry.apply(handler) }
}
