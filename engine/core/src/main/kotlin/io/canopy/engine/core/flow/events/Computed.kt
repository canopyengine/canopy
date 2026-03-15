package io.canopy.engine.core.flow.events

import io.canopy.engine.logging.EngineLogs

/**
 * A read-only reactive value derived from one or more [Signal]s.
 *
 * The derivation [block] is run eagerly on construction and re-run automatically
 * whenever any dependency (a signal read via `signal()` inside the block) changes.
 * The result is cached — [value] is only recomputed on dependency change, not on
 * every read.
 *
 * ## Dependency tracking
 *
 * Dependencies are discovered automatically. Any [Signal] read via `signal()` (the
 * `invoke` operator) inside [block] registers itself as a dependency for that run.
 * Dynamic dependencies are supported: if the block conditionally reads signals, only
 * the signals accessed in the most recent run are subscribed.
 *
 * ## Observation
 *
 * [Computed] exposes the same read surface as [Signal]:
 * - [value] — current derived value
 * - [flow] — Kotlin Flow (replay=1, distinctUntilChanged via the internal Signal)
 * - [connect] / [disconnect] — weak-reference callback listeners
 * - `computed()` — invoke operator, registers this computed's internal signal as a
 *   dependency in an outer [computed] or [effect] block
 *
 * ## Lifecycle
 *
 * A [Computed] stays active as long as it is reachable. To stop it reacting, drop all
 * references to it; its dependency subscriptions will be eligible for GC along with it.
 *
 * @param block The derivation function. Must be pure — avoid side effects here; use
 *              [effect] for side effects instead.
 */
class Computed<T>(private val block: () -> T) {

    private val log = EngineLogs.subsystem("computed")

    // Internal mutable signal that stores the current derived value and provides
    // all observation APIs (flow, connect, disconnect) for free.
    private var _signal: Signal<T>

    // The set of signals the last run depended on.
    private var dependencies: Set<Signal<*>> = emptySet()

    // Parallel list of disconnect handles, one per dependency, in insertion order.
    // Stored so we can disconnect removed dependencies after each recompute.
    private val disconnectHandlers: MutableMap<Signal<*>, EventDisconnectHandler> = mutableMapOf()

    @Volatile private var recomputing = false

    init {
        // Run the block once to compute the initial value and capture dependencies.
        // We can't use _signal.value = ... here because _signal isn't initialised yet.
        val initial = runBlock()
        _signal = Signal(initial)
    }

    // -------------------------------------------------------------------------
    // Public read surface
    // -------------------------------------------------------------------------

    /** Current derived value. */
    val value: T get() = _signal.value

    /** Flow of derived values (replay = 1, distinctUntilChanged). */
    val flow get() = _signal.flow

    /** Subscribes a listener to derived-value changes (weak reference). */
    infix fun connect(listener: (T) -> Unit) = _signal connect listener

    /** Unsubscribes a previously registered listener. */
    infix fun disconnect(listener: (T) -> Unit) = _signal disconnect listener

    /**
     * Reads the current derived value and registers this computed as a dependency
     * in the enclosing [computed] or [effect] block (if any).
     *
     * Use `computed()` (not `.value`) when you want computed chains to react to
     * each other automatically.
     */
    operator fun invoke(): T {
        TrackingContext.register(_signal)
        return _signal.value
    }

    // -------------------------------------------------------------------------
    // Internal recomputation
    // -------------------------------------------------------------------------

    /**
     * Re-runs [block] under dependency tracking, then updates [_signal] with the
     * new value. The internal [Signal] only fires listeners when the value changes,
     * so downstream observers are not notified when the derived value is unchanged.
     *
     * Protected against circular dependency chains via [recomputing] guard.
     */
    private fun recompute() {
        if (recomputing) {
            log.warn(
                "event" to "computed.circular",
            ) { "Circular computed dependency detected — skipping recomputation" }
            return
        }
        recomputing = true
        try {
            _signal.value = runBlock()
        } finally {
            recomputing = false
        }
    }

    /**
     * Runs [block] inside a [TrackingContext] frame, captures the accessed signals
     * as the new dependency set, diffs against the previous set, and updates
     * subscriptions accordingly.
     */
    private fun runBlock(): T {
        val frame = TrackingContext.push()
        return try {
            block()
        } finally {
            TrackingContext.pop()
            updateDependencies(frame)
        }
    }

    private fun updateDependencies(newDeps: Set<Signal<*>>) {
        val added = newDeps - dependencies
        val removed = dependencies - newDeps

        for (dep in removed) {
            disconnectHandlers.remove(dep)?.disconnect()
        }
        for (dep in added) {
            // The lambda captures `this` (the Computed) strongly — intentional.
            // The Computed owns the handler, so the lambda stays alive as long as
            // the Computed is alive, preventing premature GC of the weak listener.
            val handler = dep connect { _ -> recompute() }
            disconnectHandlers[dep] = handler
        }
        dependencies = newDeps
    }
}

/* ------------------------------------------------------------------
 * Factory
 * ------------------------------------------------------------------ */

/**
 * Creates a [Computed] whose value is derived by [block].
 *
 * Any [Signal] (or [Computed]) read via `signal()` / `computed()` inside [block]
 * becomes a reactive dependency — the computed value is updated automatically
 * whenever a dependency changes.
 *
 * Example:
 * ```kotlin
 * val hp = signal(100)
 * val isDead = computed { hp() <= 0 }
 * ```
 */
fun <T> computed(block: () -> T): Computed<T> = Computed(block)
