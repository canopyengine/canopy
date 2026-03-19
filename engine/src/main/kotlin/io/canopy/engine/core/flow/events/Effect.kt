package io.canopy.engine.core.flow.events

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
 * **Important:** Hold a strong reference to the returned [Effect] for as long as you
 * need it to remain active. If the only reference is dropped, the effect and its
 * subscriptions become eligible for GC.
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

    init {
        run()
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
        disconnectHandlers.values.forEach { it.disconnect() }
        disconnectHandlers.clear()
        dependencies = emptySet()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun run() {
        if (disposed) return

        val frame = TrackingContext.push()
        try {
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
            val handler = dep connect { _ ->
                if (!disposed) run()
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
 * score.value = 10  // prints "Score: 10"
 * e.dispose()
 * ```
 */
fun effect(block: () -> Unit): Effect = Effect(block)
