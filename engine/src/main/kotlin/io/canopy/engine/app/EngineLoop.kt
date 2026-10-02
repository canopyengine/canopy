package io.canopy.engine.app

/**
 * Coordinates the platform-independent application lifecycle.
 *
 * Platform drivers should forward lifecycle events to this class. Frame updates
 * run fixed-step physics updates first, followed by one variable-step update.
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
    private var entered = false
    private var exited = false
    private var physicsAccumulator = 0f

    var physicsStep: Float = physicsStep
        private set

    init {
        require(physicsStep.isFinite() && physicsStep > 0f) { "physicsStep must be finite and positive" }
        require(maxPhysicsStepsPerFrame > 0) { "maxPhysicsStepsPerFrame must be positive" }
    }

    fun enter() {
        if (entered || exited) return
        onEnter()
        entered = true
    }

    /** Runs due fixed-step physics ticks followed by one frame update. */
    fun update(delta: Float) {
        checkActive()
        require(delta.isFinite() && delta >= 0f) { "delta must be finite and non-negative" }

        val paused = isPaused()
        if (paused) {
            physicsAccumulator = 0f
            onUpdate(0f)
            return
        }

        physicsAccumulator += delta
        var steps = 0
        while (
            physicsAccumulator + PHYSICS_STEP_EPSILON >= physicsStep &&
            steps < maxPhysicsStepsPerFrame
        ) {
            physicsAccumulator = (physicsAccumulator - physicsStep).coerceAtLeast(0f)
            physicsUpdate(physicsStep)
            steps++
        }

        onUpdate(delta)
    }

    /** Dispatches one physics tick. Normally called by [update]. */
    fun physicsUpdate(delta: Float) {
        checkActive()
        require(delta.isFinite() && delta >= 0f) { "delta must be finite and non-negative" }
        onPhysicsUpdate(delta)
    }

    fun resize(width: Int, height: Int) {
        checkActive()
        onResize(width, height)
    }

    fun exit() {
        if (!entered || exited) return
        exited = true
        onExit()
    }

    internal fun configurePhysicsStep(physicsStep: Float) {
        require(physicsStep.isFinite() && physicsStep > 0f) { "physicsStep must be finite and positive" }
        this.physicsStep = physicsStep
        physicsAccumulator = 0f
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
