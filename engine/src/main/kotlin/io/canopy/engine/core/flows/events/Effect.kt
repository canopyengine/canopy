package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/**
 * A reactive side effect that re-runs [block] whenever any of its signal
 * dependencies change.
 *
 * ## Dependency tracking
 *
 * Like [Computed], dependencies are discovered automatically. Any [Signal] (or
 * [Computed]) read via `signal()` / `computed()` (the `invoke` operator) inside
 * [block] becomes a reactive dependency. Dynamic dependencies are supported.
 *
 * ## Lifecycle
 *
 * The block runs immediately, or on first entry when created for a not-yet-entered Node.
 * Call [dispose] to permanently stop
 * the effect. After disposal, no further runs occur and all dependency subscriptions
 * are removed.
 *
 * Effects created in Node methods/callbacks infer that Node through the matching compiler.
 * They are retained until that Node exits, then disposed.
 * Ownership is captured at construction; later reruns do not open a node ownership scope. Use
 * the matching compiler for Node operations, or explicit owners in standalone helper code.
 *
 * **Important:** Outside a node ownership scope, hold a strong reference to the returned [Effect] for as long as you
 * need it to remain active. If the only reference is dropped, the effect and its
 * subscriptions become eligible for GC.
 *
 * If a dependency changes while the effect is running, one rerun is queued after
 * the current run. Multiple changes during the same run are coalesced; the rerun
 * observes the latest values.
 *
 * Example:
 * ```kotlin
 * val hp = signal(100)
 * val e = effect {
 *     if (hp() <= 0) println("Entity died")
 * }
 * e.dispose() // stops reacting
 * ```
 */
class Effect(block: () -> Unit) {

    private var action: (() -> Unit)? = block

    private val subscriptions = DependencySubscriptions()

    @Volatile private var disposed = false
    private var running = false
    private var rerunRequested = false

    private var cancelOwnership: (() -> Unit)? = null
    private var cancelActivation: (() -> Unit)? = null

    init {
        cancelOwnership = NodeLifetime.own(::dispose)
        try {
            val owner = NodeLifetime.current()
            if (owner != null && !owner.isInsideTree) {
                cancelActivation = owner.observeActivity({
                    cancelActivation?.invoke()
                    cancelActivation = null
                    if (!disposed) {
                        try {
                            run()
                        } catch (error: Throwable) {
                            dispose()
                            throw error
                        }
                    }
                }, {})
            } else {
                run()
            }
        } catch (error: Throwable) {
            dispose()
            throw error
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Stops the effect from reacting to future dependency changes.
     * All dependency subscriptions are removed. Safe to call multiple times.
     */
    fun dispose() {
        if (disposed) return
        disposed = true
        action = null
        cancelOwnership?.invoke()
        cancelOwnership = null
        cancelActivation?.invoke()
        cancelActivation = null
        subscriptions.clear()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun run() {
        if (disposed) return
        if (running) {
            rerunRequested = true
            return
        }

        do {
            rerunRequested = false
            running = true
            val frame = TrackingContext.push()
            try {
                NodeLifetime.withOwner(null) { action?.invoke() }
            } finally {
                TrackingContext.pop()
                running = false
                updateDependencies(frame)
            }
        } while (!disposed && rerunRequested)
    }

    private fun updateDependencies(newDeps: Set<Signal<*>>) {
        if (disposed) return
        subscriptions.reconcile(newDeps) { if (!disposed) run() }
    }
}

/* ------------------------------------------------------------------
 * Factory
 * ------------------------------------------------------------------ */

/**
 * Creates an [Effect] that runs [block] immediately (or on its owning Node's first entry) and re-runs it whenever
 * any [Signal] (or [Computed]) accessed via `signal()` / `computed()` inside
 * [block] changes.
 *
 * Returns the [Effect] handle. Call [Effect.dispose] to stop the effect.
 *
 * Example:
 * ```kotlin
 * val score = signal(0)
 * val e = effect { println("Score: ${score()}") }
 * score.update { 10 }  // prints "Score: 10"
 * e.dispose()
 * ```
 */
fun effect(block: () -> Unit): Effect = Effect(block)

/** Creates an effect retained until [owner] exits or is removed, then automatically disposes it. */
fun effect(owner: Node<*>?, block: () -> Unit): Effect = NodeLifetime.withOwner(owner) { Effect(block) }
