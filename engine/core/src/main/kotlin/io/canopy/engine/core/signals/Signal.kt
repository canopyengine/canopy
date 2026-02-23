package io.canopy.engine.core.signals

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Generic weak-reference signal-slot implementation.
 * Supports 0..3 arguments.
 */
sealed interface Signal<T> {
    fun clear()

    infix fun connect(listener: T)

    infix fun disconnect(listener: T)
}

private class WeakListeners<T : Any> {
    val listeners = CopyOnWriteArrayList<WeakReference<T>>()

    fun add(listener: T) {
        listeners += WeakReference(listener)
    }

    fun remove(listener: T) {
        listeners.removeIf { it.get() == listener || it.get() == null }
    }

    fun forEach(action: (T) -> Unit) {
        listeners.forEach {
            val listener = it.get()
            if (listener == null) {
                listeners.remove(it)
            } else {
                action(listener)
            }
        }
    }

    fun clear() = listeners.clear()
}

/** 0-argument signal */
class NoArgSignal : Signal<() -> Unit> {
    private val callbacks = WeakListeners<() -> Unit>()

    override infix fun connect(listener: () -> Unit) = callbacks.add(listener)

    override infix fun disconnect(listener: () -> Unit) = callbacks.remove(listener)

    fun emit() = callbacks.forEach { it() }

    override fun clear() = callbacks.clear()
}

/** 1-argument signal */
class OneArgSignal<A> : Signal<(A) -> Unit> {
    private val callbacks = WeakListeners<(A) -> Unit>()

    override infix fun connect(listener: (A) -> Unit) = callbacks.add(listener)

    override infix fun disconnect(listener: (A) -> Unit) = callbacks.remove(listener)

    fun emit(a: A) = callbacks.forEach { it(a) }

    override fun clear() = callbacks.clear()
}

/** 2-argument signal */
class TwoArgsSignal<A, B> : Signal<(A, B) -> Unit> {
    private val callbacks = WeakListeners<(A, B) -> Unit>()

    override infix fun connect(listener: (A, B) -> Unit) = callbacks.add(listener)

    override infix fun disconnect(listener: (A, B) -> Unit) = callbacks.remove(listener)

    fun emit(a: A, b: B) = callbacks.forEach { it(a, b) }

    override fun clear() = callbacks.clear()
}

// Factory functions

fun createSignal() = NoArgSignal()

fun <A> createSignal() = OneArgSignal<A>()

fun <A, B> createSignal() = TwoArgsSignal<A, B>()
