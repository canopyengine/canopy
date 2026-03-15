package io.canopy.engine.core.flow.events

/**
 * Thread-local stack of dependency-tracking frames.
 *
 * When a [Computed] or [Effect] runs its block, it pushes a new frame, runs
 * the block, then pops the frame. Any [Signal] that is read via [Signal.invoke]
 * during that block calls [register], which adds it to the innermost frame.
 *
 * The stack (rather than a single slot) handles nesting: if a computed block
 * reads another computed value, each has its own frame on the stack so
 * dependencies never bleed between scopes.
 *
 * Reads via [Signal.value] are intentionally NOT tracked — only the `()` call
 * syntax opts into dependency registration.
 */
internal object TrackingContext {

    private val stack: ThreadLocal<ArrayDeque<MutableSet<Signal<*>>>> =
        ThreadLocal.withInitial { ArrayDeque() }

    /**
     * Opens a new tracking frame and returns it.
     * The caller must call [pop] when the computation block finishes.
     */
    fun push(): MutableSet<Signal<*>> {
        val frame = mutableSetOf<Signal<*>>()
        stack.get().addLast(frame)
        return frame
    }

    /** Closes the innermost tracking frame. */
    fun pop() {
        stack.get().removeLastOrNull()
    }

    /**
     * Adds [signal] to the innermost active frame, if any.
     * If no computation is active (stack is empty), this is a no-op.
     */
    fun register(signal: Signal<*>) {
        stack.get().lastOrNull()?.add(signal)
    }
}
