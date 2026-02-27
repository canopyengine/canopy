package io.canopy.engine.app.core.screen

import kotlin.reflect.KClass
import io.canopy.engine.app.core.CanopyApp

class CanopyScreenRegistry(val app: CanopyApp<*>) {
    private var setupCallback: CanopyScreenRegistry.() -> Unit = {}

    inline fun <reified T : CanopyScreen> screen(screen: T) {
        app.addScreen(screen)
    }

    inline fun <reified T : CanopyScreen> start() {
        app.setScreen<T>()
    }

    inline fun <reified T : CanopyScreen> start(screen: T) {
        screen(screen)
        start<T>()
    }

    fun registerSetupCallback(callback: CanopyScreenRegistry.() -> Unit = {}) {
        setupCallback = callback
    }

    fun setup() = setupCallback()

    inline operator fun <reified T : CanopyScreen> T.unaryPlus() = screen<T>(this)
    inline operator fun <reified T : CanopyScreen> KClass<T>.unaryMinus() {
        app.removeScreen(this.java)
    }
}
