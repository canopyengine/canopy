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
    init {
        owner?.requireValid("create reactive source")
    }
    fun bind(dispose: () -> Unit) {
        cancel = owner?.get()?.onDestroy(dispose)
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
        return true
    }
}
