package io.canopy.engine.core.signals

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import io.canopy.engine.logging.engine.EngineLogs

/**
 * Generic weak-reference signal-slot implementation.
 * Supports 0..2 arguments (easy to extend).
 */
sealed interface Signal<T> {
    fun clear()
    fun size(): Int
    fun isEmpty(): Boolean = size() == 0

    infix fun connect(listener: T)
    infix fun disconnect(listener: T)
}

private object SignalLogs {
    // Put this in your EngineLogs too if you want: val signals = subsystem("signals")
    val log = EngineLogs.subsystem("signals")
}

private class WeakListeners<T : Any>(private val name: String? = null, private val kind: String) {
    private val listeners = CopyOnWriteArrayList<WeakReference<T>>()

    fun add(listener: T) {
        cleanupDead()
        listeners += WeakReference(listener)

        if (SignalLogs.logEnabledTrace()) {
            SignalLogs.logTrace(
                event = "signal.connect",
                fields = mapOf(
                    "signal" to name,
                    "kind" to kind,
                    "listeners" to listeners.size
                )
            )
        }
    }

    fun remove(listener: T) {
        // remove matching or dead
        listeners.removeIf { it.get() == null || it.get() === listener }

        if (SignalLogs.logEnabledTrace()) {
            SignalLogs.logTrace(
                event = "signal.disconnect",
                fields = mapOf(
                    "signal" to name,
                    "kind" to kind,
                    "listeners" to listeners.size
                )
            )
        }
    }

    fun forEach(action: (T) -> Unit) {
        // Iterate snapshot; remove dead afterward
        var removedDead = 0
        for (ref in listeners) {
            val l = ref.get()
            if (l == null) {
                removedDead++
            } else {
                action(l)
            }
        }
        if (removedDead > 0) {
            listeners.removeIf { it.get() == null }
        }
    }

    fun clear() {
        listeners.clear()

        if (SignalLogs.logEnabledTrace()) {
            SignalLogs.logTrace(
                event = "signal.clear",
                fields = mapOf(
                    "signal" to name,
                    "kind" to kind
                )
            )
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

/** 0-argument signal */
class NoArgSignal(private val name: String? = null) : Signal<() -> Unit> {
    private val callbacks = WeakListeners<() -> Unit>(name = name, kind = "0-arg")

    override infix fun connect(listener: () -> Unit) = callbacks.add(listener)
    override infix fun disconnect(listener: () -> Unit) = callbacks.remove(listener)

    fun emit() {
        if (SignalLogs.logEnabledTrace()) {
            SignalLogs.logTrace(
                event = "signal.emit",
                fields = mapOf("signal" to name, "kind" to "0-arg", "listeners" to callbacks.size())
            )
        }
        callbacks.forEach { it() }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

/** 1-argument signal */
class OneArgSignal<A>(private val name: String? = null) : Signal<(A) -> Unit> {
    private val callbacks = WeakListeners<(A) -> Unit>(name = name, kind = "1-arg")

    override infix fun connect(listener: (A) -> Unit) = callbacks.add(listener)
    override infix fun disconnect(listener: (A) -> Unit) = callbacks.remove(listener)

    fun emit(a: A) {
        if (SignalLogs.logEnabledTrace()) {
            SignalLogs.logTrace(
                event = "signal.emit",
                fields = mapOf("signal" to name, "kind" to "1-arg", "listeners" to callbacks.size())
            )
        }
        callbacks.forEach { it(a) }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

/** 2-argument signal */
class TwoArgsSignal<A, B>(private val name: String? = null) : Signal<(A, B) -> Unit> {
    private val callbacks = WeakListeners<(A, B) -> Unit>(name = name, kind = "2-arg")

    override infix fun connect(listener: (A, B) -> Unit) = callbacks.add(listener)
    override infix fun disconnect(listener: (A, B) -> Unit) = callbacks.remove(listener)

    fun emit(a: A, b: B) {
        if (SignalLogs.logEnabledTrace()) {
            SignalLogs.logTrace(
                event = "signal.emit",
                fields = mapOf("signal" to name, "kind" to "2-arg", "listeners" to callbacks.size())
            )
        }
        callbacks.forEach { it(a, b) }
    }

    override fun clear() = callbacks.clear()
    override fun size(): Int = callbacks.size()
}

// ---------- Factory functions ----------

fun createSignal(name: String? = null) = NoArgSignal(name)

fun <A> createSignal(name: String? = null) = OneArgSignal<A>(name)

fun <A, B> createSignal(name: String? = null) = TwoArgsSignal<A, B>(name)

// ---------- Small internal log helpers ----------

private fun SignalLogs.logEnabledTrace() = log.isTraceEnabled()

private fun SignalLogs.logTrace(event: String, fields: Map<String, Any?>) {
    // Log at trace, but keep message constant; structured fields carry info.
    log.trace(fields = fields + ("event" to event)) { event }
}
