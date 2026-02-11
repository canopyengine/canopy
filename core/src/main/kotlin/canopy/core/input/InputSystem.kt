package anchors.framework.input

import anchors.framework.managers.Manager
import anchors.framework.nodes.core.GlobalNodeSystem
import anchors.framework.nodes.core.UpdatePhase
import com.badlogic.gdx.math.Vector2
import ktx.log.logger

class InputSystem(
    vararg pairs: Pair<String, List<InputBind>>,
) : GlobalNodeSystem(UpdatePhase.Input),
    Manager {
    private val logger = logger<InputSystem>()
    private val mapper = InputMapper()
    private val actionsState = mutableMapOf<String, InputState>()

    init {
        // Map all input actions at startup
        pairs.forEach { (action, binds) -> mapper.mapAction(action, binds) }
    }

    // ------------------------------------------------------------------------
    // Polling per frame
    // ------------------------------------------------------------------------
    override fun afterProcess(delta: Float) {
        mapper.mappings.forEach { (action, binds) ->
            val isPressed = binds.any { it.isBeingPressed() }
            val prevState = actionsState[action] ?: InputState.Released

            // Compute the next state
            val nextState = getInputState(action, if (isPressed) InputState.Pressed else InputState.Released)

            // Dispatch events
            when (nextState) {
                InputState.JustPressed -> {
                    dispatchEvents(
                        listOf(ButtonInputEvent(action, InputState.JustPressed)),
                        delta,
                    )
                }
                InputState.Pressed -> {
                    // Only dispatch if the action was already Pressed (avoid spam for taps)
                    if (prevState == InputState.Pressed) {
                        dispatchEvents(
                            listOf(ButtonInputEvent(action, InputState.Pressed)),
                            delta,
                        )
                    }
                }
                InputState.JustReleased -> {
                    dispatchEvents(
                        listOf(ButtonInputEvent(action, InputState.JustReleased)),
                        delta,
                    )
                }
                else -> Unit
            }
        }
    }

    // ------------------------------------------------------------------------
    // Compute next state based on previous state and raw input
    // ------------------------------------------------------------------------
    private fun getInputState(
        action: String,
        newState: InputState,
    ): InputState {
        val prev = actionsState[action] ?: InputState.Released

        val prevIsPressedEvent = prev in listOf(InputState.Pressed, InputState.JustPressed)
        val prevIsReleasedEvent = prev in listOf(InputState.Released, InputState.JustReleased)

        val next =
            when (newState) {
                InputState.Pressed if (prevIsReleasedEvent) -> InputState.JustPressed
                InputState.Pressed -> InputState.Pressed
                InputState.Released if (prevIsPressedEvent) -> InputState.JustReleased
                else -> InputState.Released
            }

        actionsState[action] = next
        return next
    }

    // ------------------------------------------------------------------------
    // Dispatch events to the SceneManager
    // ------------------------------------------------------------------------
    private fun dispatchEvents(
        events: List<InputEvent>,
        delta: Float,
    ) {
        events.forEach { sceneManager.onInput(it, delta) }
    }

    private fun getState(action: String) = actionsState[action] ?: InputState.Released

    fun getAxis(
        negativeAction: String,
        positiveAction: String,
    ): Float {
        val neg = getState(negativeAction)
        val pos = getState(positiveAction)

        val value =
            when {
                pos == InputState.Pressed && neg == InputState.Released -> 1f
                neg == InputState.Pressed && pos == InputState.Released -> -1f
                else -> 0f
            }
        return value
    }

    fun getInputVector(
        negativeX: String,
        positiveX: String,
        negativeY: String,
        positiveY: String,
    ): Vector2 =
        Vector2(
            getAxis(negativeX, positiveX),
            getAxis(negativeY, positiveY),
        )
}
