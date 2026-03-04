package io.canopy.engine.app.core.screen

import kotlin.reflect.KClass
import io.canopy.engine.app.core.CanopyApp

/**
 * DSL-style registry for managing screens during app bootstrap.
 *
 * This exists to provide a clean, declarative way for apps to register and
 * select screens, typically from:
 *
 * ```
 * screens {
 *   +MainMenuScreen()
 *   +GameScreen()
 *   start<MainMenuScreen>()
 * }
 * ```
 *
 * The registry collects a setup callback (the DSL block) and executes it during
 * engine startup via [setup]. This ensures screens are registered at the right
 * time (after managers are ready, before the first screen is shown).
 */
class CanopyScreenRegistry(val app: CanopyApp<*>) {

    /**
     * Captures the user's DSL block registered through `CanopyApp.screens { ... }`.
     * It is executed once during app startup.
     */
    private var setupCallback: CanopyScreenRegistry.() -> Unit = {}

    /**
     * Registers a screen instance with the underlying app.
     *
     * Note:
     * The screen's [CanopyScreen.setup] runs when it is shown for the first time,
     * not at registration time.
     */
    inline fun <reified T : CanopyScreen> screen(screen: T) {
        app.addScreen(screen)
    }

    /**
     * Sets the current screen by type.
     *
     * Example:
     * ```
     * start<MainMenuScreen>()
     * ```
     */
    inline fun <reified T : CanopyScreen> start() {
        app.setScreen<T>()
    }

    /**
     * Convenience: registers a screen instance and immediately starts it.
     *
     * Example:
     * ```
     * start(MainMenuScreen())
     * ```
     */
    inline fun <reified T : CanopyScreen> start(screen: T) {
        screen(screen)
        start<T>()
    }

    /**
     * Called by [CanopyApp] to register the DSL block that will later be executed by [setup].
     */
    fun registerSetupCallback(callback: CanopyScreenRegistry.() -> Unit = {}) {
        setupCallback = callback
    }

    /**
     * Executes the previously registered DSL block.
     *
     * This is invoked during app startup (see CanopyApp.create()).
     */
    fun setup() = setupCallback()

    /* ------------------------------------------------------------
     * DSL operators
     * ------------------------------------------------------------ */

    /**
     * Adds a screen to the app registry:
     *
     * `+MainMenuScreen()`
     */
    operator fun CanopyScreen.unaryPlus() {
        screen(this)
    }

    /**
     * Removes a screen type from the app registry:
     *
     * `-MainMenuScreen::class`
     */
    operator fun <T : CanopyScreen> KClass<T>.unaryMinus() {
        app.removeScreen(this.java)
    }
}
