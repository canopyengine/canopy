package io.canopy.engine.app

/**
 * Coordinates the platform-independent application lifecycle.
 *
 * Platform drivers should forward lifecycle events to this class. Frame updates
 * run fixed-step physics updates first, followed by one variable-step update.
 * Nested entry, frame, resize and exit calls during entry or frame dispatch are rejected.
 * Repeated exit during teardown is harmless.
 */
class EngineLoop(
    private val onEnter: () -> Unit,
    private val onUpdate: (Float) -> Unit,
    private val onPhysicsUpdate: (Float) -> Unit,
    private val onResize: (Int, Int) -> Unit,
    private val onExit: () -> Unit,
    private val isPaused: () -> Boolean = { false },
    physicsStep: Float = DEFAULT_PHYSICS_STEP,
    private val maxPhysicsStepsPerFrame: Int = DEFAULT_MAX_PHYSICS_STEPS_PER_FRAME,
) {
    private var entering = false
    private var dispatching = false
    private var entered = false
    private var exited = false
    private var physicsAccumulator = 0f
    private var wasPaused = false

    internal var validateExit: () -> Unit = {}

    var physicsStep: Float = physicsStep
        private set

    init {
        require(physicsStep.isFinite() && physicsStep > 0f) { "physicsStep must be finite and positive" }
        require(maxPhysicsStepsPerFrame > 0) { "maxPhysicsStepsPerFrame must be positive" }
    }

    /** Failed entry cannot be retried; the entry callback owns rollback of partial initialization. */
    fun enter() {
        check(!entering && !dispatching) { "Cannot enter EngineLoop during lifecycle dispatch" }
        if (entered || exited) return
        entering = true
        try {
            onEnter()
            entered = true
        } catch (error: Throwable) {
            exited = true
            throw error
        } finally {
            entering = false
        }
    }

    /**
     * Runs fixed-step ticks and a frame update even while paused, with real deltas in seconds.
     * Callers decide which work remains eligible. Pause transitions discard the fractional physics
     * remainder so gameplay and paused processing do not share accumulated time.
     */
    fun update(delta: Float) = dispatch {
        checkActive()
        require(delta.isFinite() && delta >= 0f) { "delta must be finite and non-negative" }

        val paused = isPaused()
        if (paused != wasPaused) physicsAccumulator = 0f
        wasPaused = paused

        physicsAccumulator += delta
        var steps = 0
        while (
            physicsAccumulator + PHYSICS_STEP_EPSILON >= physicsStep &&
            steps < maxPhysicsStepsPerFrame
        ) {
            physicsAccumulator = (physicsAccumulator - physicsStep).coerceAtLeast(0f)
            onPhysicsUpdate(physicsStep)
            steps++
        }

        onUpdate(delta)
    }

    /** Dispatches one physics tick. Normally called by [update]. */
    fun physicsUpdate(delta: Float) = dispatch {
        checkActive()
        require(delta.isFinite() && delta >= 0f) { "delta must be finite and non-negative" }
        onPhysicsUpdate(delta)
    }

    fun resize(width: Int, height: Int) = dispatch {
        checkActive()
        onResize(width, height)
    }

    fun exit() {
        check(!entering && !dispatching) { "Cannot exit EngineLoop during lifecycle dispatch" }
        if (!entered || exited) return
        validateExit()
        exited = true
        onExit()
    }

    internal fun configurePhysicsStep(physicsStep: Float) {
        require(physicsStep.isFinite() && physicsStep > 0f) { "physicsStep must be finite and positive" }
        this.physicsStep = physicsStep
        physicsAccumulator = 0f
    }

    private inline fun dispatch(block: () -> Unit) {
        check(!entering && !dispatching) { "Cannot redispatch EngineLoop during lifecycle dispatch" }
        dispatching = true
        try {
            block()
        } finally {
            dispatching = false
        }
    }

    private fun checkActive() {
        check(entered && !exited) { "EngineLoop must be entered and not exited" }
    }

    companion object {
        const val DEFAULT_PHYSICS_STEP: Float = 1f / 60f
        private const val DEFAULT_MAX_PHYSICS_STEPS_PER_FRAME = 5
        private const val PHYSICS_STEP_EPSILON = 0.0000001f
    }
}
