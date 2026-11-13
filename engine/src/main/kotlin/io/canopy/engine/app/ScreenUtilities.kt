package io.canopy.engine.app

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node

/** Selects the lazy initial screen for this App. Call before startup; later configuration replaces the earlier choice. */
fun <T : Node<*>> App<*>.initialScreen(type: KClass<T>, factory: (() -> T)? = null) {
    configureInitialScreen { navigateTo(type, factory) }
}

/** Selects [T] without constructing it before startup. Constructor arguments can be supplied through [factory]. */
inline fun <reified T : Node<*>> App<*>.initialScreen(noinline factory: (() -> T)? = null) =
    initialScreen(T::class, factory)

/** Requests screen navigation on the owning App's lifecycle thread. The latest request commits before painting. */
fun <T : Node<*>> App<*>.navigateTo(type: KClass<T>, factory: (() -> T)? = null) =
    currentScreenManager().navigateTo(type, factory)

/** Opens or resumes [T]. Factories initialize new instances; they do not update cached constructor arguments. */
inline fun <reified T : Node<*>> App<*>.navigateTo(noinline factory: (() -> T)? = null) = navigateTo(T::class, factory)

/** Recreates the current screen at the next boundary without resetting App-owned shared data. */
fun App<*>.reloadScreen() = currentScreenManager().reloadScreen()

/** Releases an inactive cached instance, remembering its factory for a later visit. */
fun <T : Node<*>> App<*>.unloadScreen(type: KClass<T>) = currentScreenManager().unloadScreen(type)

/** Releases inactive [T]; use [reloadScreen] for the active screen. */
inline fun <reified T : Node<*>> App<*>.unloadScreen() = unloadScreen(T::class)

private fun Node<*>.navigation(): ScreenManager {
    requireValid("screen navigation")
    check(isInsideTree && owningManager() === getManager<SceneManager>()) {
        "Navigate from an active Node in the running App"
    }
    return getManager<ScreenManager>()
}

/** Requests navigation from an active Node; it remains valid for the rest of the current callback. */
fun <T : Node<*>> Node<*>.navigateTo(type: KClass<T>, factory: (() -> T)? = null) =
    navigation().navigateTo(type, factory)

/** Opens or resumes [T] through this Node's App-owned ScreenManager. */
inline fun <reified T : Node<*>> Node<*>.navigateTo(noinline factory: (() -> T)? = null) = navigateTo(T::class, factory)

/** Requests a fresh current screen through this active Node's App. */
fun Node<*>.reloadScreen() = navigation().reloadScreen()

/** Releases another, inactive cached screen. */
fun <T : Node<*>> Node<*>.unloadScreen(type: KClass<T>) = navigation().unloadScreen(type)

/** Releases another, inactive [T] screen. */
inline fun <reified T : Node<*>> Node<*>.unloadScreen() = unloadScreen(T::class)
