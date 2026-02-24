package io.canopy.engine.app.core.screen

import io.canopy.engine.app.core.CanopyApp

class CanopyScreenRegistry(val app: CanopyApp<*>) {
    var setupCallback: CanopyScreenRegistry.() -> Unit = {}

    inline fun <reified T : CanopyScreen> screen(screen: T) {
        app.addScreen(screen)
    }

    inline fun <reified T : CanopyScreen> start() {
        app.setScreen<T>()
    }

    inline fun <reified T : CanopyScreen> start(newScreen: T) {
        screen<T>(newScreen)
        app.setScreen<T>()
    }

    fun setup() = setupCallback()
}
