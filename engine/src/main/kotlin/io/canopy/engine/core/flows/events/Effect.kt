package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.logging.EngineLogs

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
 * The block runs immediately on construction. Call [dispose] to permanently stop
 * the effect. After disposal, no further runs occur and all dependency subscriptions
 * are removed.
 *
 * Effects created during managed node callbacks are retained until that node exits, then disposed.
 * Ownership is captured at construction; later reruns do not open a node ownership scope. Use
 * explicit owners for resources created during those reruns.
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
class Effect(private val block: () -> Unit) {

    private val log = EngineLogs.subsystem("effect")

    private var dependencies: Set<Signal<*>> = emptySet()
    private val disconnectHandlers: MutableMap<Signal<*>, EventDisconnectHandler> = mutableMapOf()

    @Volatile private var disposed = false
    private var running = false
    private var rerunRequested = false

    private var cancelOwnership: (() -> Unit)? = null

    init {
        cancelOwnership = NodeLifetime.own(::dispose)
        try {
            run()
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
        disposed = true
        cancelOwnership?.invoke()
        cancelOwnership = null
        disconnectHandlers.values.forEach { it.disconnect() }
        disconnectHandlers.clear()
        dependencies = emptySet()
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
                block()
            } finally {
                TrackingContext.pop()
                running = false
                updateDependencies(frame)
            }
        } while (!disposed && rerunRequested)
    }

    private fun updateDependencies(newDeps: Set<Signal<*>>) {
        if (disposed) return
        val added = newDeps - dependencies
        val removed = dependencies - newDeps

        for (dep in removed) {
            disconnectHandlers.remove(dep)?.disconnect()
        }
        for (dep in added) {
            val handler = NodeLifetime.withOwner(null) {
                dep connect { _ -> if (!disposed) run() }
            }
            disconnectHandlers[dep] = handler
        }
        dependencies = newDeps
    }
}

/* ------------------------------------------------------------------
 * Factory
 * ------------------------------------------------------------------ */

/**
 * Creates an [Effect] that runs [block] immediately and re-runs it whenever
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
fun effect(owner: Node<*>, block: () -> Unit): Effect = NodeLifetime.withOwner(owner) { Effect(block) }
