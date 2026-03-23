package io.canopy.engine.core.flows.events

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
 * Dependencies are discovered automatically. Any [Signal] (or [Computed]) invoked via
 * `signal()` / `computed()` inside [block] registers itself as a dependency for that
 * run. Dynamic dependencies are supported: only the signals accessed in the most recent
 * run are subscribed.
 *
 * ## Observation
 *
 * [Computed] exposes a read surface that mirrors [Signal]:
 * - [value] — current derived value (also tracks as a dependency when read)
 * - `computed()` — invoke operator, equivalent to [value]
 * - [flow] — Kotlin Flow (replay=1, distinctUntilChanged)
 * - [connect] / [disconnect] — weak-reference callback listeners
 *
 * ## Lifecycle
 *
 * A [Computed] stays active as long as it is reachable. To stop it reacting, drop all
 * references to it; its dependency subscriptions become eligible for GC along with it.
 *
 * @param block The derivation function. Should be pure — avoid side effects here; use
 *              [effect] for side effects instead.
 */
class Computed<T>(private val block: () -> T) {

    private val log = EngineLogs.subsystem("computed")

    private val signal by lazy { Signal(runBlock()) }
    private var dependencies: Set<Signal<*>> = emptySet()
    private val disconnectHandlers: MutableMap<Signal<*>, EventDisconnectHandler> = mutableMapOf()

    @Volatile private var recomputing = false

    // -------------------------------------------------------------------------
    // Public read surface
    // -------------------------------------------------------------------------

    /** Flow of derived values (flowreplay = 1, distinctUntilChanged). */
    val flow get() = signal.flow

    /** Subscribes a listener to derived-value changes (weak reference). */
    infix fun connect(listener: (T) -> Unit) = signal connect listener

    /** Unsubscribes a previously registered listener. */
    infix fun disconnect(listener: (T) -> Unit) = signal disconnect listener

    /**
     * Reads the current derived value, registering this computed as a dependency in
     * the enclosing [computed] or [effect] block (if any). Equivalent to [signal()].
     */
    operator fun invoke(): T = signal()

    // -------------------------------------------------------------------------
    // Internal recomputation
    // -------------------------------------------------------------------------

    private fun recompute() {
        if (recomputing) {
            log.warn(
                "event" to "computed.circular"
            ) { "Circular computed dependency detected — skipping recomputation" }
            return
        }
        recomputing = true
        try {
            signal.update { runBlock() }
        } finally {
            recomputing = false
        }
    }

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
 * Any [Signal] (or [Computed]) invoked via `signal()` / `computed()` inside [block]
 * becomes a reactive dependency — the computed value updates automatically whenever a
 * dependency changes.
 *
 * Example:
 * ```kotlin
 * val hp = signal(100)
 * val isDead = computed { hp() <= 0 }
 * ```
 */
fun <T> computed(block: () -> T): Computed<T> = Computed(block)
