package io.canopy.engine.core.flows.events

import java.lang.ref.WeakReference
import io.canopy.engine.core.exceptions.CanopyException
import io.canopy.engine.core.nodes.Node

/** Private lifetime guard shared by node-local sources. Never strongly retains its owner. */
internal class SourceLifetime(owner: Node<*>?) {
    private val owner = owner?.let(::WeakReference)
    var disposed = false
        private set
    private var cancel: (() -> Unit)? = null
    private var cancelActivity: (() -> Unit)? = null
    val active: Boolean get() = owner == null || owner.get()?.isInsideTree == true

    // Declarative construction may establish dormant bindings before the first entry.
    val canObserve: Boolean get() = active || owner?.get()?.let { it.isValid && !it.hasEnteredTree } == true
    init {
        owner?.requireValid("create reactive source")
    }
    fun bind(dispose: () -> Unit, suspend: () -> Unit = {}, resume: () -> Unit = {}) {
        cancel = owner?.get()?.onDestroy(dispose)
        var needsResume = !active
        cancelActivity = owner?.get()?.observeActivity({
            if (needsResume) {
                needsResume = false
                resume()
            }
        }, {
            needsResume = true
            suspend()
        })
    }
    fun check(operation: String) {
        owner?.get()?.requireValid(operation)
        if (disposed) throw CanopyException("Reactive source is disposed: $operation")
    }
    fun dispose(): Boolean {
        if (disposed) return false
        disposed = true
        cancel?.invoke()
        cancel = null
        cancelActivity?.invoke()
        cancelActivity = null
        return true
    }
}
