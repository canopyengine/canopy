package io.canopy.engine.app

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.lazyManager

/**
 * DSL-style registry for managing application screens during bootstrap.
 *
 * Typical usage:
 *
 * ```kotlin
 * screens {
 *     +MainMenuScreen()
 *     +GameScreen()
 *     start<MainMenuScreen>()
 * }
 * ```
 *
 * The registry stores the user's DSL block and executes it during app startup.
 */
class ScreenRegistry internal constructor() {

    /* ============================================================
     * Managers
     * ============================================================ */

    val screenManager by lazyManager<ScreenManager>()

    /* ============================================================
     * Setup
     * ============================================================ */

    private var setupCallback: ScreenRegistry.() -> Unit = {}

    fun registerSetupCallback(callback: ScreenRegistry.() -> Unit = {}) {
        setupCallback = callback
    }

    fun setup() {
        setupCallback()
    }

    /* ============================================================
     * Registration
     * ============================================================ */

    /**
     * Registers a screen instance in the application screen store.
     */
    inline fun <reified T : Screen> screen(screen: T) {
        screenManager.register(screen)
    }

    /**
     * DSL shorthand:
     *
     * ```kotlin
     * +MainMenuScreen()
     * ```
     */
    operator fun Screen.unaryPlus() {
        screenManager.register(this)
    }

    /**
     * DSL shorthand:
     *
     * ```kotlin
     * -MainMenuScreen::class
     * ```
     */
    operator fun <T : Screen> KClass<T>.unaryMinus() {
        screenManager.remove(this)
    }

    /* ============================================================
     * Navigation
     * ============================================================ */

    /**
     * Starts a previously registered screen by type.
     */
    inline fun <reified T : Screen> start() {
        screenManager.start(T::class)
    }

    /**
     * Registers and immediately starts a screen instance.
     */
    inline fun <reified T : Screen> start(screen: T) {
        this.screen(screen)
        start<T>()
    }
}
