package io.canopy.engine.input

import com.badlogic.gdx.math.Vector2
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.engine.utils.UnstableApi
import ktx.log.logger

/**
 * Polling-based input system that converts raw device state (keys/mouse buttons)
 * into engine-level action events ([InputEvent]).
 *
 * Responsibilities:
 * - Poll all configured [InputBind]s each tick
 * - Maintain per-action state transitions (Pressed/JustPressed/JustReleased/Released)
 * - Dispatch input events into the scene tree (TODO: [dispatchEvents])
 * - Provide helpers for axis and vector inputs (WASD, arrows, etc.)
 *
 * Notes:
 * - This system currently emits:
 *   - JustPressed (one-shot)
 *   - Pressed (continuous, but only after the first tick as Pressed)
 *   - JustReleased (one-shot)
 *
 * - Mouse movement / pointer events are not generated here yet.
 */
@UnstableApi
class InputSystem(vararg pairs: Pair<String, List<InputBind>>) :
    TreeSystem(UpdatePhase.PhysicsPre, 10),
    Manager {

    private val logger = logger<InputSystem>()

    /** Runtime mapping of action -> physical binds (e.g. "jump" -> [SPACE]). */
    private val mapper = InputMapper()

    /** Last computed state for each action (drives transition detection). */
    private val actionsState = mutableMapOf<String, InputState>()

    init {
        // Register the initial action bindings at startup.
        // (InputMapper also registers its persistence module in its init.)
        pairs.forEach { (action, binds) ->
            mapper.mapAction(action, binds)
        }
    }

    // ------------------------------------------------------------------------
    // Polling / state update
    // ------------------------------------------------------------------------

    override fun afterProcess(delta: Float) {
        /**
         * Poll each action, compute a state transition, and emit the corresponding event(s).
         *
         * We do not emit a continuous Pressed event on the same tick as JustPressed,
         * to avoid duplicate events for a single press.
         */
        mapper.mappings.forEach { (action, binds) ->
            val isPressed = binds.any { it.isBeingPressed() }
            val prevState = actionsState[action] ?: InputState.Released

            val nextState = getNextState(
                action = action,
                rawState = if (isPressed) InputState.Pressed else InputState.Released
            )

            when (nextState) {
                InputState.JustPressed -> {
                    dispatchEvents(listOf(ButtonInputEvent(action, InputState.JustPressed)), delta)
                }

                InputState.Pressed -> {
                    // Only emit Pressed if we were already pressed last tick (continuous hold).
                    // This prevents "tap spam" where a single key press generates both
                    // JustPressed + Pressed in the same frame.
                    if (prevState == InputState.Pressed) {
                        dispatchEvents(listOf(ButtonInputEvent(action, InputState.Pressed)), delta)
                    }
                }

                InputState.JustReleased -> {
                    dispatchEvents(listOf(ButtonInputEvent(action, InputState.JustReleased)), delta)
                }

                else -> Unit
            }
        }
    }

    // ------------------------------------------------------------------------
    // State machine
    // ------------------------------------------------------------------------

    /**
     * Computes the next [InputState] for an action based on:
     * - the previous computed state stored in [actionsState]
     * - the current raw device state (pressed vs released)
     *
     * Transition summary:
     * - Released -> Pressed      => JustPressed
     * - Pressed  -> Pressed      => Pressed
     * - Pressed  -> Released     => JustReleased
     * - Released -> Released     => Released
     */
    private fun getNextState(action: String, rawState: InputState): InputState {
        val prev = actionsState[action] ?: InputState.Released

        val prevWasPressed = (prev == InputState.Pressed || prev == InputState.JustPressed)
        val prevWasReleased = (prev == InputState.Released || prev == InputState.JustReleased)

        val next =
            when (rawState) {
                InputState.Pressed ->
                    if (prevWasReleased) InputState.JustPressed else InputState.Pressed

                InputState.Released ->
                    if (prevWasPressed) InputState.JustReleased else InputState.Released

                // Raw state should only be Pressed/Released in this system.
                else -> InputState.Released
            }

        actionsState[action] = next
        return next
    }

    // ------------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------------

    /**
     * Dispatches events into the scene tree.
     *
     * Expected behavior (once implemented):
     * - deliver events to an input root / focused node
     * - stop propagation if event.isHandled becomes true
     * - optionally include mouse/pointer position for click events
     */
    private fun dispatchEvents(events: List<InputEvent>, delta: Float) {
        // TODO: propagate to nodes (e.g., via SceneManager root traversal + InputListener)
        // logger.trace { "Dispatching ${events.size} input events" }
    }

    // ------------------------------------------------------------------------
    // Query helpers (useful for gameplay code)
    // ------------------------------------------------------------------------

    private fun getState(action: String): InputState = actionsState[action] ?: InputState.Released

    /**
     * Returns a digital axis value in [-1, 0, 1] based on two opposing actions.
     *
     * Example:
     * - negativeAction = "move_left"
     * - positiveAction = "move_right"
     */
    fun getAxis(negativeAction: String, positiveAction: String): Float {
        val neg = getState(negativeAction)
        val pos = getState(positiveAction)

        return when {
            pos == InputState.Pressed && neg == InputState.Released -> 1f
            neg == InputState.Pressed && pos == InputState.Released -> -1f
            else -> 0f
        }
    }

    /**
     * Returns a 2D input vector (digital) from four directional actions.
     *
     * Example (WASD):
     * - negativeX = "move_left",  positiveX = "move_right"
     * - negativeY = "move_down",  positiveY = "move_up"
     */
    fun getInputVector(negativeX: String, positiveX: String, negativeY: String, positiveY: String): Vector2 = Vector2(
        getAxis(negativeX, positiveX),
        getAxis(negativeY, positiveY)
    )
}
