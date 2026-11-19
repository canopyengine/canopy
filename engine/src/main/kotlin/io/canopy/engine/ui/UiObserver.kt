package io.canopy.engine.ui

import io.canopy.engine.core.flows.events.DependencySubscriptions
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.TrackingContext
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

internal class UiObserver(private val owner: Node<*>, private var action: (() -> Unit)?) {
    private val subscriptions = DependencySubscriptions()
    private var disposed = false
    private var suspended = false
    private var sourceDisconnected = false
    private var observedSources: Set<Signal<*>> = emptySet()
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
        if (sourceDisconnected && !owner.isInsideTree) return
        val frame = TrackingContext.pushRecording()
        var failed = false
        try {
            NodeLifetime.withOwner(owner) { TrackingContext.captureInput { action?.invoke() } }
        } catch (failure: Throwable) {
            failed = true
            if (sourceDisconnected && observedSources.any { !it.canObserve }) {
                val path = owner.path
                dispose()
                throw IllegalStateException(
                    "UI reactive dependency for $path is unavailable; observer disposed",
                    failure
                )
            }
            throw failure
        } finally {
            TrackingContext.pop()
            if (!failed && !disposed && !suspended && owner.isValid) {
                try {
                    subscriptions.reconcile(
                        frame,
                        { getManager<UiManager>().enqueue(this) },
                        ::dependencyDisconnected
                    )
                    observedSources = frame.toSet()
                    sourceDisconnected = false
                    if (frame.revisions.any { (source, revision) -> source.revision != revision }) {
                        getManager<UiManager>().enqueue(this)
                    }
                } catch (failure: Throwable) {
                    dispose()
                    throw failure
                }
            }
        }
    }
    private fun dependencyDisconnected() {
        if (disposed || suspended) return
        sourceDisconnected = true
        getManager<UiManager>().enqueue(this)
    }
    fun suspend() {
        if (disposed || suspended) return
        suspended = true
        cancelRemoval?.invoke()
        cancelRemoval = null
        subscriptions.clear()
        sourceDisconnected = false
        observedSources = emptySet()
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
        sourceDisconnected = false
        observedSources = emptySet()
        getManager<UiManager>().cancel(this)
    }
}
