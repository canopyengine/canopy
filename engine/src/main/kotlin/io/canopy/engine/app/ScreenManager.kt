package io.canopy.engine.app

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.Manager

class ScreenManager : Manager {

    /* ============================================================
     * Registry
     * ============================================================ */

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

    fun frame(delta: Float) {
        current?.onFrame(delta)
    }

    fun resize(width: Int, height: Int) {
        current?.onResize(width, height)
    }

    /* ============================================================
     * Teardown
     * ============================================================ */

    override fun teardown() {
        super.teardown()

        current?.onExit()
        current = null

        screens.values.forEach { it.dispose() }
        screens.clear()
    }
}
