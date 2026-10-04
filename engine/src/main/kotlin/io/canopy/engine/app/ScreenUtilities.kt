package io.canopy.engine.app

import io.canopy.engine.core.managers.manager

/**
 * Registers [screen] by its concrete type in the current global [ScreenManager].
 *
 * Call on the serialized application lifecycle thread after managers are registered.
 * Registration does not start the screen or preload resources. Replacing the active instance
 * ends its visit and clears the current screen; registering the same instance is a no-op.
 * The receiver does not create a separate screen registry for this application.
 *
 * @throws IllegalStateException if the manager is missing or registration occurs during screen teardown.
 */
fun App<*>.registerScreen(screen: Screen) = manager<ScreenManager>().register(screen)

/**
 * Starts the registered screen of type [T] through the current global [ScreenManager].
 *
 * Call on the serialized application lifecycle thread after managers are registered.
 * Leaves the previous visit before entering the target; starting the current instance is a no-op.
 * Does not construct an unregistered screen, replace the scene or add a visual transition.
 *
 * @throws IllegalStateException if the manager or target is missing, or navigation occurs during screen teardown.
 */
inline fun <reified T : Screen> App<*>.startScreen() = manager<ScreenManager>().start(T::class)

/**
 * Removes the screen registration of type [T] from the current global [ScreenManager].
 *
 * Call on the serialized application lifecycle thread after managers are registered.
 * Removing the active instance ends its visit and clears the current screen.
 * Removing an unregistered type is a no-op; removing another type leaves the active visit intact.
 *
 * @throws IllegalStateException if the manager is missing or removal occurs during screen teardown.
 */
inline fun <reified T : Screen> App<*>.removeScreen() = manager<ScreenManager>().remove(T::class)
