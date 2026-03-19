package io.canopy.engine.core.flow.events

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import io.canopy.engine.logging.EngineLogs

/**
 * Internal storage for weak listeners.
 *
 * Uses [CopyOnWriteArrayList] so that [forEach] can iterate safely without locks even while
 * connects/disconnects happen concurrently.
 */
internal class EventWeakListeners<T : Any>(private val kind: String) {
    val log = EngineLogs.subsystem("events")

    private val listeners = CopyOnWriteArrayList<WeakReference<T>>()

    fun add(listener: T) {
        cleanupDead()
        listeners += WeakReference(listener)

        if (log.isTraceEnabled()) {
            log.trace(
                "event" to "event.connect",
                "kind" to kind,
                "listeners" to listeners.size
            ) { "Add callback" }
        }
    }

    fun remove(listener: T) {
        // Remove matching listener and any dead references.
        listeners.removeIf { it.get() == null || it.get() === listener }

        if (log.isTraceEnabled()) {
            log.trace(
                "event" to "event.disconnect",
                "kind" to kind,
                "listeners" to listeners.size
            ) { "Remove callback" }
        }
    }

    /**
     * Iterates listeners and invokes [action] for each live listener.
     *
     * Dead listeners are cleaned up after iteration to keep the loop fast.
     */
    fun forEach(action: (T) -> Unit) {
        var removedDead = 0

        for (ref in listeners) {
            val l = ref.get()
            if (l == null) removedDead++ else action(l)
        }

        if (removedDead > 0) {
            listeners.removeIf { it.get() == null }
        }
    }

    fun clear() {
        listeners.clear()

        if (log.isTraceEnabled()) {
            log.trace(
                "event" to "event.clear",
                "kind" to kind
            ) { "Clear callbacks" }
        }
    }

    fun size(): Int {
        cleanupDead()
        return listeners.size
    }

    private fun cleanupDead() {
        listeners.removeIf { it.get() == null }
    }
}
