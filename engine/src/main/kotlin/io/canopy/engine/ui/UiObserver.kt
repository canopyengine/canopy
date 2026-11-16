package io.canopy.engine.ui

import io.canopy.engine.core.flows.events.DependencySubscriptions
import io.canopy.engine.core.flows.events.TrackingContext
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

internal class UiObserver(private val owner: Node<*>, private var action: (() -> Unit)?) {
    private val subscriptions = DependencySubscriptions()
    private var disposed = false
    private var suspended = false
    private var cancelRemoval: (() -> Unit)? = null
    private var cancelDestruction: (() -> Unit)? = null
    init {
        cancelRemoval = owner.onRemoval(::suspend)
        cancelDestruction = owner.onDestroy(::dispose)
        try {
            run()
        } catch (failure: Throwable) {
            dispose()
            throw failure
        }
    }
    fun run() {
        if (disposed || suspended || !owner.isValid) return
        val frame = TrackingContext.push()
        try {
            NodeLifetime.withOwner(owner) { action?.invoke() }
        } finally {
            TrackingContext.pop()
            if (!disposed && !suspended && owner.isValid) {
                subscriptions.reconcile(frame) { getManager<UiManager>().enqueue(this) }
            }
        }
    }
    fun suspend() {
        if (disposed || suspended) return
        suspended = true
        cancelRemoval?.invoke()
        cancelRemoval = null
        subscriptions.clear()
        getManager<UiManager>().cancel(this)
    }
    fun resume() {
        if (!disposed && suspended) {
            suspended = false
            cancelRemoval = owner.onRemoval(::suspend)
            // Presentation runs after tree entry, so descendant reactive state is active.
            getManager<UiManager>().enqueue(this)
        }
    }
    fun dispose() {
        if (disposed) return
        disposed = true
        cancelRemoval?.invoke()
        cancelRemoval = null
        cancelDestruction?.invoke()
        cancelDestruction = null
        action = null
        subscriptions.clear()
        getManager<UiManager>().cancel(this)
    }
}
