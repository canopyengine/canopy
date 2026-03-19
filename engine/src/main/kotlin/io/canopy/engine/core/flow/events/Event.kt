package io.canopy.engine.core.flow.events

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
sealed class Event<T> {
    val log = EngineLogs.subsystem("events")

    /** Removes all listeners. */
    abstract fun clear()

    /** Number of currently tracked listeners (dead weak refs are cleaned up opportunistically). */
    abstract fun size(): Int

    fun isEmpty(): Boolean = size() == 0

    /** Adds a listener. */
    abstract infix fun connect(listener: T): EventDisconnectHandler

    /** Removes a listener. */
    abstract infix fun disconnect(listener: T)
}

/* ============================================================
 * Type of Events
 * ============================================================ */

/** 0-argument event. */
class NoArgEvent : Event<() -> Unit>() {
    private val callbacks = EventWeakListeners<() -> Unit>(kind = "0-arg")

    override infix fun connect(listener: () -> Unit): EventDisconnectHandler {
        callbacks.add(listener)
        return EventDisconnectHandler { disconnect(listener) }
    }
    override infix fun disconnect(listener: () -> Unit) = callbacks.remove(listener)

    fun emit() {
        if (log.isTraceEnabled()) {
            log.trace(
                "event" to "event.emit",
                "kind" to "0-arg",
                "listeners" to callbacks.size()
            ) { "Emit" }
        }
        callbacks.forEach { it() }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

/** 1-argument event. */
class OneArgEvent<A> : Event<(A) -> Unit>() {
    private val callbacks = EventWeakListeners<(A) -> Unit>(kind = "1-arg")

    override infix fun connect(listener: (A) -> Unit): EventDisconnectHandler {
        callbacks.add(listener)
        return EventDisconnectHandler { disconnect(listener) }
    }
    override infix fun disconnect(listener: (A) -> Unit) = callbacks.remove(listener)

    fun emit(a: A) {
        if (log.isTraceEnabled()) {
            log.trace(
                "event" to "event.emit",
                "kind" to "1-arg",
                "listeners" to callbacks.size()
            ) { "Emit" }
        }
        callbacks.forEach { it(a) }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

/** 2-argument event. */
class TwoArgsEvent<A, B> : Event<(A, B) -> Unit>() {
    private val callbacks = EventWeakListeners<(A, B) -> Unit>(kind = "2-arg")

    override infix fun connect(listener: (A, B) -> Unit): EventDisconnectHandler {
        callbacks.add(listener)
        return EventDisconnectHandler { disconnect(listener) }
    }
    override infix fun disconnect(listener: (A, B) -> Unit) = callbacks.remove(listener)

    fun emit(a: A, b: B) {
        if (log.isTraceEnabled()) {
            log.trace(
                "event" to "event.emit",
                "kind" to "2-arg",
                "listeners" to callbacks.size()
            ) { "Emit" }
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
