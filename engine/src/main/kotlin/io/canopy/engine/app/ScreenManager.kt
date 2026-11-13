package io.canopy.engine.app

import kotlin.reflect.KClass
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import io.canopy.engine.core.CleanupFailures
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node

/**
 * Navigates between ordinary Node roots, retaining one instance per requested class in this App.
 * Requests commit after updates and before painting. The latest request wins; activation requests wait for the
 * next boundary. All operations belong to the lifecycle thread. No scene or controller registry is required.
 */
class ScreenManager : Manager {
    private lateinit var scenes: SceneManager
    private class Entry(val factory: () -> Node<*>) {
        var root: Node<*>? = null
        var cancelDestruction: (() -> Unit)? = null
    }
    private class Request(val type: KClass<out Node<*>>, val factory: (() -> Node<*>)?, val reload: Boolean)
    private val entries = linkedMapOf<KClass<out Node<*>>, Entry>()
    private var pending: Request? = null
    private var activeType: KClass<out Node<*>>? = null
    private var lifecycleThread: Thread? = null
    private var closing = false
    private var leaving = false
    private var committing = false

    /** Current screen root, or null before first navigation and after destruction/shutdown. */
    val current: Node<*>? get() = activeType?.let { entries[it]?.root }
        ?.takeIf { it.isValid && scenes.currScene === it }

    /**
     * Requests [type], reusing its cached instance or constructing it at the next frame boundary.
     * [factory] is used only for creation and remembered for reload/unload. A cached instance ignores new arguments.
     * Without a factory, the class needs a public no-argument constructor. The current screen is a no-op.
     */
    fun <T : Node<*>> navigateTo(type: KClass<T>, factory: (() -> T)? = null) {
        checkOperation()
        pending = Request(type, factory, false)
    }

    /** Opens or resumes [T] without a registration table. */
    inline fun <reified T : Node<*>> navigateTo(noinline factory: (() -> T)? = null) = navigateTo(T::class, factory)

    /** Releases inactive [T] while preserving its factory. */
    inline fun <reified T : Node<*>> unloadScreen() = unloadScreen(T::class)

    /** Requests a fresh current screen using the factory that created it. Shared manager/context data is unchanged. */
    fun reloadScreen() {
        checkOperation()
        val type = activeType?.takeIf { current != null } ?: error("There is no current screen to reload")
        pending = Request(type, null, true)
    }

    /** Destroys an inactive instance but remembers its factory. Never constructs a screen. */
    fun <T : Node<*>> unloadScreen(type: KClass<T>) {
        checkOperation()
        check(!committing) { "Cannot unload screens during a transition" }
        check(type != activeType || current == null) {
            "Navigate away or use reloadScreen() before unloading the current screen"
        }
        entries[type]?.let { discard(it) }
    }

    override fun onEnter() {
        check(lifecycleThread == null) { "ScreenManager is already entered" }
        scenes = getManager<SceneManager>()
        lifecycleThread = Thread.currentThread()
        closing = false
    }

    /** Commits at most one request. Requests from destination entry are retained for the next boundary. */
    internal fun commit() {
        checkOperation()
        if (committing) return
        val request = pending ?: return
        pending = null
        val previousEntry = entries[request.type]
        val cached = previousEntry?.root?.takeIf { it.isValid }
        if (!request.reload && activeType == request.type && current === cached && cached != null) return
        committing = true
        var destination: Entry? = null
        var created = false
        var activating = false
        try {
            val entry = if (!request.reload && cached != null) {
                checkNotNull(previousEntry)
            } else {
                val factory = request.factory ?: previousEntry?.factory ?: defaultFactory(request.type)
                Entry(factory).also { fresh ->
                    fresh.root = Node.constructRoot {
                        factory().also { root ->
                            check(root.isValid && !root.isInsideTree && root.parent == null) {
                                "A screen factory must return a new detached Node root"
                            }
                            check(root.owningManager() === scenes && request.type.isInstance(root)) {
                                "Screen factory returned a root of the wrong type or App"
                            }
                            check(
                                entries.values.none {
                                    it.root === root
                                }
                            ) { "A screen factory must create a fresh root" }
                        }
                    }
                }
            }
            destination = entry
            created = entry !== previousEntry
            val root = checkNotNull(entry.root)
            detachCurrent()
            activeType = null
            scenes.screenControlled = false
            if (created) {
                if (previousEntry != null) discard(previousEntry)
                entries[request.type] = entry
                entry.cancelDestruction = root.onDestroy {
                    if (entry.root === root) {
                        entry.root = null
                        entry.cancelDestruction = null
                        if (activeType == request.type) {
                            activeType = null
                            scenes.screenControlled = false
                        }
                    }
                }
            }
            activeType = request.type
            scenes.screenControlled = true
            activating = true
            scenes.replaceScreenRoot(root)
        } catch (failure: Throwable) {
            pending = null
            val cleanup = CleanupFailures(failure)
            destination?.let { entry ->
                if (created || activating) {
                    if (entry.root === scenes.currScene) cleanup.attempt { detachCurrent() }
                    cleanup.attempt { discard(entry) }
                }
            }
            if (scenes.currScene == null) {
                activeType = null
                scenes.screenControlled = false
            }
            cleanup.rethrow()
        } finally {
            committing = false
        }
    }

    private fun detachCurrent() {
        val previous = leaving
        leaving = true
        try {
            scenes.replaceScreenRoot(null)
        } finally {
            leaving = previous
        }
    }

    private fun defaultFactory(type: KClass<out Node<*>>): () -> Node<*> {
        val constructor = try {
            check(Modifier.isPublic(type.java.modifiers) && !Modifier.isAbstract(type.java.modifiers)) {
                "Automatic screen construction requires a public concrete class"
            }
            type.java.getConstructor()
        } catch (failure: Exception) {
            throw IllegalStateException(
                "Cannot create screen ${type.qualifiedName}. Use a public no-argument constructor or " +
                    "navigateTo<${type.simpleName}> { ${type.simpleName}(...) }.",
                failure
            )
        }
        return {
            try {
                constructor.newInstance()
            } catch (failure: InvocationTargetException) {
                throw (failure.cause ?: failure)
            }
        }
    }

    private fun discard(entry: Entry) {
        val root = entry.root ?: return
        entry.root = null
        entry.cancelDestruction?.invoke()
        entry.cancelDestruction = null
        val previous = leaving
        leaving = true
        try {
            scenes.destroyRetainedRoot(root)
        } finally {
            leaving = previous
        }
    }

    private fun checkOperation() {
        check(lifecycleThread === Thread.currentThread() && !closing) {
            "Screen navigation requires the running App's lifecycle thread"
        }
        check(!leaving) { "Cannot navigate during screen exit or destruction" }
    }

    /** Releases all active/inactive roots exactly once, even if cleanup fails. */
    override fun onExit() {
        if (lifecycleThread == null || closing) return
        closing = true
        pending = null
        val failures = CleanupFailures()
        failures.attempt { scenes.replaceScreenRoot(null) }
        activeType = null
        scenes.screenControlled = false
        entries.values.toList().forEach { entry -> failures.attempt { discard(entry) } }
        entries.clear()
        lifecycleThread = null
        failures.rethrow()
    }
}
