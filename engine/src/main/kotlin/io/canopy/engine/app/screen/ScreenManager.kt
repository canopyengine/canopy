package io.canopy.engine.app.screen

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.Manager

class ScreenManager : Manager {

    private val screens = linkedMapOf<KClass<out Screen>, Screen>()

    var current: Screen? = null
        private set

    fun register(screen: Screen) {
        screens[screen::class] = screen
    }

    fun <T : Screen> remove(type: KClass<T>) {
        val removed = screens.remove(type)
        if (current == removed) {
            current?.onExit()
            current = null
        }
    }

    fun <T : Screen> start(type: KClass<T>) {
        val next = screens[type]
            ?: error("Screen not registered: ${type.qualifiedName}")

        if (current === next) return

        current?.onExit()
        current = next
        current?.onEnter()
    }

    fun frame(delta: Float) {
        current?.onFrame(delta)
    }

    fun resize(width: Int, height: Int) {
        current?.onResize(width, height)
    }

    override fun teardown() {
        super.teardown()
        current?.onExit()
        screens.values.forEach { it.dispose() }
        screens.clear()
        current = null
    }
}
