package io.canopy.engine.input

import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.KeyInputEvent

/**
 * Shared lifecycle-thread input routing for editors, overlays and controls. Higher priorities route first;
 * equal priorities preserve registration order. Consumed events never reach later routes or raw gameplay dispatch.
 * Exclusive capture suppresses gameplay for the whole input frame, even if a handler closes its editor.
 * Ctrl+C remains available to the host. This service does not pause simulation.
 */
class InputFocus : Manager {
    private val routes = mutableListOf<Route>()
    private var capturedThisFrame = false

    /** Whether an active route captures gameplay now or captured it earlier in the current input frame. */
    val blocksGameplay: Boolean
        get() = capturedThisFrame || routes.toList().any { it.active() && it.capturesGameplay?.invoke() == true }

    /**
     * Registers a route until its idempotent lease closes. An [owner] releases it on tree exit; hidden or
     * detached owners do not route input. Null ownership lasts until explicit close or service shutdown.
     * [beginFrame] resets per-frame route state before backend events are drained.
     * Return true from [handler], or consume the event, to stop propagation. Registration does not pause.
     */
    fun register(
        owner: Node<*>? = null,
        priority: Int = 0,
        capturesGameplay: () -> Boolean = { false },
        beginFrame: () -> Unit = {},
        handler: (InputEvent) -> Boolean,
    ): AutoCloseable {
        if (owner != null) check(owner.isInsideTree) { "Input focus owner must be entered" }
        val route = Route(owner, priority, capturesGameplay, beginFrame, handler)
        routes += route
        routes.sortByDescending { it.priority }
        route.remove = { routes.remove(route) }
        route.cancelCleanup = owner?.onRemoval { route.close() }
        return route
    }

    internal fun beginInputFrame() {
        routes.toList().forEach { if (it in routes) it.beginFrame?.invoke() }
        capturedThisFrame = routes.toList().any { it.active() && it.capturesGameplay?.invoke() == true }
    }

    internal fun route(event: InputEvent): Boolean {
        if (event.isHandled) return true
        if (event is KeyInputEvent && event.isCtrlC()) return false
        for (route in routes.toList()) {
            if (route !in routes || !route.active()) continue
            if (route.capturesGameplay?.invoke() == true) capturedThisFrame = true
            val consumed = route.handler?.invoke(event) == true || event.isHandled
            if (route in routes && route.active() && route.capturesGameplay?.invoke() == true) capturedThisFrame = true
            if (consumed) {
                event.consume()
                return true
            }
        }
        if (capturedThisFrame) {
            event.consume()
            return true
        }
        return false
    }

    override fun onExit() {
        routes.toList().forEach { it.close() }
        routes.clear()
        capturedThisFrame = false
    }

    private class Route(
        var owner: Node<*>?,
        val priority: Int,
        var capturesGameplay: (() -> Boolean)?,
        var beginFrame: (() -> Unit)?,
        var handler: ((InputEvent) -> Boolean)?,
    ) : AutoCloseable {
        var remove: (() -> Unit)? = null
        var cancelCleanup: (() -> Unit)? = null

        override fun close() {
            remove?.invoke()
            remove = null
            cancelCleanup?.invoke()
            cancelCleanup = null
            owner = null
            capturesGameplay = null
            beginFrame = null
            handler = null
        }
        fun active(): Boolean = handler != null &&
            owner.let { it == null || (it.isInsideTree && it.isVisibleInTree) }
    }
}
