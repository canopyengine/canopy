package io.canopy.engine.core.flows.events

/**
 * Thread-local stack of dependency-tracking frames.
 *
 * When a [Computed] or [Effect] runs its block, it pushes a new frame, runs
 * the block, then pops the frame. Any [Signal] read via [Signal.value] or
 * [Signal.invoke] during that block calls [register], which adds it to the
 * innermost frame.
 *
 * The stack (rather than a single slot) handles nesting: if a computed block
 * reads another computed value, each has its own frame on the stack so
 * dependencies never bleed between scopes.
 *
 * To read a signal inside a reactive block without registering it as a
 * dependency, use [untrack].
 */
internal object TrackingContext {

    private val stack: ThreadLocal<ArrayDeque<MutableSet<Signal<*>>>> =
        ThreadLocal.withInitial { ArrayDeque() }

    /**
     * Opens a new tracking frame and returns it.
     * The caller must call [pop] when the computation block finishes.
     */
    internal fun push(): MutableSet<Signal<*>> {
        val frame = mutableSetOf<Signal<*>>()
        stack.get().addLast(frame)
        return frame
    }

    /** Closes the innermost tracking frame. */
    internal fun pop() {
        stack.get().removeLastOrNull()
    }

    /**
     * Adds [signal] to the innermost active frame, if any.
     * If no computation is active (stack is empty), this is a no-op.
     */
    internal fun register(signal: Signal<*>) {
        stack.get().lastOrNull()?.add(signal)
    }

    /**
     * Runs [block] with all tracking frames suspended, so signals read inside
     * are not registered as dependencies in the enclosing [Computed] or [Effect].
     *
     * The saved frames are restored after [block] returns (or throws).
     */
    internal fun <T> untrack(block: () -> T): T {
        val deque = stack.get()
        if (deque.isEmpty()) return block()
        val saved = ArrayDeque(deque)
        deque.clear()
        return try {
            block()
        } finally {
            deque.addAll(saved)
        }
    }
}

/* ------------------------------------------------------------------
 * Top-level untrack helper
 * ------------------------------------------------------------------ */

/**
 * Reads signals inside [block] without registering them as dependencies in the
 * enclosing [computed] or [effect] block.
 *
 * Use this when you need the current value of a signal for a calculation but
 * do not want changes to that signal to trigger recomputation.
 *
 * Example — only [multiplier] is a tracked dependency; [base] is read but
 * changes to it will NOT rerun the computed:
 * ```kotlin
 * val total = computed { untrack { base() } * multiplier() }
 * ```
 */
fun <T> untrack(block: () -> T): T = TrackingContext.untrack(block)
