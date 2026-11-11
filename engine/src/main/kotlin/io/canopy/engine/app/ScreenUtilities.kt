package io.canopy.engine.app

/**
 * Registers [screen] by its concrete type in this app's active [ScreenManager].
 *
 * Call on the serialized application lifecycle thread after managers are registered.
 * Registration does not start the screen or preload resources. Replacing the active instance
 * ends its visit and clears the current screen; registering the same instance is a no-op.
 * The receiver must own the running app scope. Standalone lifecycle scopes remain supported.
 *
 * @throws IllegalStateException if the manager is missing or registration occurs during screen teardown.
 */
fun App<*>.registerScreen(screen: Screen) = currentScreenManager().register(screen)

/**
 * Starts the registered screen of type [T] through this app's active [ScreenManager].
 *
 * Call on the serialized application lifecycle thread after managers are registered.
 * Leaves the previous visit before entering the target; starting the current instance is a no-op.
 * Does not construct an unregistered screen, replace the scene or add a visual transition.
 *
 * @throws IllegalStateException if the manager or target is missing, or navigation occurs during screen teardown.
 */
inline fun <reified T : Screen> App<*>.startScreen() = currentScreenManager().start(T::class)

/**
 * Removes the screen registration of type [T] from this app's active [ScreenManager].
 *
 * Call on the serialized application lifecycle thread after managers are registered.
 * Removing the active instance ends its visit and clears the current screen.
 * Removing an unregistered type is a no-op; removing another type leaves the active visit intact.
 *
 * @throws IllegalStateException if the manager is missing or removal occurs during screen teardown.
 */
inline fun <reified T : Screen> App<*>.removeScreen() = currentScreenManager().remove(T::class)
