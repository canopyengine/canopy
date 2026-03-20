package io.canopy.engine.input

import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.logging.logger
import io.canopy.engine.math.Vector2
import io.canopy.tooling.utils.UnstableApi

/**
 * Polling-based input system that converts raw device state into engine-level action events.
 *
 * Responsibilities:
 * - Poll all configured [InputBind]s each tick through [InputManager]
 * - Maintain per-action state transitions
 * - Dispatch input events into the scene tree
 * - Provide helpers for axis and vector inputs
 *
 * Notes:
 * - Action/bind mappings are owned by [InputManager.mapper]
 * - This system only interprets mapped raw input into action states/events
 */
@UnstableApi
class InputSystem(vararg pairs: Pair<String, List<InputBind>>) :
    TreeSystem(UpdatePhase.PhysicsPre, 10),
    Manager {

    private val logger = logger<InputSystem>()

    /** Last computed state for each action. */
    private val actionsState = mutableMapOf<String, InputState>()

    private val input: InputManager
        get() = manager()

    init {
        pairs.forEach { (action, binds) ->
            input.mapper.mapAction(action, binds)
        }
    }

    override fun afterProcess(delta: Float) {
        input.mapper.actions.forEach { (action, binds) ->
            val isPressed = binds.any(input::isPressed)
            val prevState = actionsState[action] ?: InputState.Released

            val nextState = getNextState(
                action = action,
                rawState = if (isPressed) InputState.Pressed else InputState.Released
            )

            when (nextState) {
                InputState.JustPressed -> {
                    dispatchEvents(
                        events = listOf(ButtonInputEvent(action, InputState.JustPressed)),
                        delta = delta
                    )
                }

                InputState.Pressed -> {
                    if (prevState == InputState.Pressed) {
                        dispatchEvents(
                            events = listOf(ButtonInputEvent(action, InputState.Pressed)),
                            delta = delta
                        )
                    }
                }

                InputState.JustReleased -> {
                    dispatchEvents(
                        events = listOf(ButtonInputEvent(action, InputState.JustReleased)),
                        delta = delta
                    )
                }

                InputState.Released -> Unit
                else -> Unit
            }
        }
    }

    /**
     * Computes the next [InputState] for an action based on:
     * - the previous computed state stored in [actionsState]
     * - the current raw device state (pressed vs released)
     */
    private fun getNextState(action: String, rawState: InputState): InputState {
        val prev = actionsState[action] ?: InputState.Released

        val prevWasPressed = prev == InputState.Pressed || prev == InputState.JustPressed
        val prevWasReleased = prev == InputState.Released || prev == InputState.JustReleased

        val next = when (rawState) {
            InputState.Pressed ->
                if (prevWasReleased) InputState.JustPressed else InputState.Pressed

            InputState.Released ->
                if (prevWasPressed) InputState.JustReleased else InputState.Released

            else -> InputState.Released
        }

        actionsState[action] = next
        return next
    }

    /**
     * Dispatches events into the scene tree.
     */
    private fun dispatchEvents(events: List<InputEvent>, delta: Float) {
        // TODO: propagate to nodes (e.g. via SceneManager root traversal + InputListener)
        // logger.trace { "Dispatching ${events.size} input events" }
    }

    private fun getState(action: String): InputState = actionsState[action] ?: InputState.Released

    /**
     * Returns a digital axis value in [-1, 0, 1] based on two opposing actions.
     */
    fun getAxis(negativeAction: String, positiveAction: String): Float {
        val neg = getState(negativeAction)
        val pos = getState(positiveAction)

        val negPressed = neg == InputState.Pressed || neg == InputState.JustPressed
        val posPressed = pos == InputState.Pressed || pos == InputState.JustPressed

        return when {
            posPressed && !negPressed -> 1f
            negPressed && !posPressed -> -1f
            else -> 0f
        }
    }

    /**
     * Returns a 2D digital input vector from four directional actions.
     */
    fun getInputVector(negativeX: String, positiveX: String, negativeY: String, positiveY: String): Vector2 = Vector2(
        getAxis(negativeX, positiveX),
        getAxis(negativeY, positiveY)
    )

    /**
     * Convenience helpers for runtime rebinding through the shared mapper.
     */
    fun mapAction(action: String, binds: List<InputBind>, replace: Boolean = true) {
        input.mapper.mapAction(action, binds, replace)
    }

    fun unmapAction(action: String) {
        input.mapper.unmapAction(action)
        actionsState.remove(action)
    }

    fun clearMappings() {
        input.mapper.clearMappings()
        actionsState.clear()
    }
}
