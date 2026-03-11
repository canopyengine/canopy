package io.canopy.engine.core.reactive

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import io.canopy.engine.logging.EngineLogs

/**
 * Simple event abstraction with weakly referenced listeners.
 *
 * Why weak references?
 * - Event subscriptions are easy to forget to unsubscribe.
 * - Weak listeners allow subscribers to be garbage-collected without leaks.
 *
 * Trade-offs:
 * - A listener can disappear if nothing else strongly references it.
 *   (This is desirable for many UI/game objects, but surprising if you're not expecting it.)
 *
 * Threading:
 * - Backed by [CopyOnWriteArrayList], which is safe to iterate while mutating.
 * - This favors read-heavy patterns (many emits, few connects/disconnects).
 *
 * This file provides 0..2 argument event types:
 * - [NoArgEvent]
 * - [OneArgEvent]
 * - [TwoArgsEvent]
 *
 * (Easy to extend if you need more arities.)
 */
sealed interface Event<T> {
    /** Removes all listeners. */
    fun clear()

    /** Number of currently tracked listeners (dead weak refs are cleaned up opportunistically). */
    fun size(): Int

    fun isEmpty(): Boolean = size() == 0

    /** Adds a listener. */
    infix fun connect(listener: T)

    /** Removes a listener. */
    infix fun disconnect(listener: T)
}

/**
 * Centralizes logging for event operations.
 * Kept separate so the event implementation stays lightweight.
 */
private object EventLogs {
    // You can also expose this as EngineLogs.events if you want a dedicated subsystem.
    val log = EngineLogs.subsystem("events")
}

/**
 * Internal storage for weak listeners.
 *
 * Uses [CopyOnWriteArrayList] so that [forEach] can iterate safely without locks even while
 * connects/disconnects happen concurrently.
 */
private class WeakListeners<T : Any>(private val kind: String) {

    private val listeners = CopyOnWriteArrayList<WeakReference<T>>()

    fun add(listener: T) {
        cleanupDead()
        listeners += WeakReference(listener)

        if (EventLogs.logEnabledTrace()) {
            EventLogs.logTrace(
                event = "event.connect",
                fields = mapOf("kind" to kind, "listeners" to listeners.size)
            )
        }
    }

    fun remove(listener: T) {
        // Remove matching listener and any dead references.
        listeners.removeIf { it.get() == null || it.get() === listener }

        if (EventLogs.logEnabledTrace()) {
            EventLogs.logTrace(
                event = "event.disconnect",
                fields = mapOf("kind" to kind, "listeners" to listeners.size)
            )
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

        if (EventLogs.logEnabledTrace()) {
            EventLogs.logTrace(event = "event.clear", fields = mapOf("kind" to kind))
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

/* ============================================================
 * Event arities
 * ============================================================ */

/** 0-argument event. */
class NoArgEvent : Event<() -> Unit> {
    private val callbacks = WeakListeners<() -> Unit>(kind = "0-arg")

    override infix fun connect(listener: () -> Unit) = callbacks.add(listener)
    override infix fun disconnect(listener: () -> Unit) = callbacks.remove(listener)

    fun emit() {
        if (EventLogs.logEnabledTrace()) {
            EventLogs.logTrace(
                event = "event.emit",
                fields = mapOf("kind" to "0-arg", "listeners" to callbacks.size())
            )
        }
        callbacks.forEach { it() }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

/** 1-argument event. */
class OneArgEvent<A> : Event<(A) -> Unit> {
    private val callbacks = WeakListeners<(A) -> Unit>(kind = "1-arg")

    override infix fun connect(listener: (A) -> Unit) = callbacks.add(listener)
    override infix fun disconnect(listener: (A) -> Unit) = callbacks.remove(listener)

    fun emit(a: A) {
        if (EventLogs.logEnabledTrace()) {
            EventLogs.logTrace(
                event = "event.emit",
                fields = mapOf("kind" to "1-arg", "listeners" to callbacks.size())
            )
        }
        callbacks.forEach { it(a) }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

/** 2-argument event. */
class TwoArgsEvent<A, B> : Event<(A, B) -> Unit> {
    private val callbacks = WeakListeners<(A, B) -> Unit>(kind = "2-arg")

    override infix fun connect(listener: (A, B) -> Unit) = callbacks.add(listener)
    override infix fun disconnect(listener: (A, B) -> Unit) = callbacks.remove(listener)

    fun emit(a: A, b: B) {
        if (EventLogs.logEnabledTrace()) {
            EventLogs.logTrace(
                event = "event.emit",
                fields = mapOf("kind" to "2-arg", "listeners" to callbacks.size())
            )
        }
        callbacks.forEach { it(a, b) }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

/* ============================================================
 * Factory functions
 * ============================================================ */

fun event() = NoArgEvent()
fun <A> event() = OneArgEvent<A>()
fun <A, B> event() = TwoArgsEvent<A, B>()

/* ============================================================
 * Internal log helpers
 * ============================================================ */

private fun EventLogs.logEnabledTrace() = log.isTraceEnabled()

private fun EventLogs.logTrace(event: String, fields: Map<String, Any?>) {
    // Note: right now we only emit the "event" field.
    // If you want the additional fields to appear in structured logs, pass them into the logger call.
    log.trace("event" to event) { event }
}
