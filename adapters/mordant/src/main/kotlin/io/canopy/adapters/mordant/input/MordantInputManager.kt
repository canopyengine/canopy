package io.canopy.adapters.mordant.input

import com.github.ajalt.mordant.input.KeyboardEvent
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind

/**
 * Mordant-backed input receiver.
 *
 * Important limitation:
 * Mordant KeyboardEvent represents a single key press, not separate
 * key-down / key-up transitions, so true "held key" tracking is not available
 * from this API alone.
 */
class MordantInputManager : InputManager() {

    /**
     * Binds pressed since the last action update.
     *
     * Since Mordant only reports key presses, this is a one-tick pulse set,
     * not a persistent "currently held" set.
     */
    private val pressedThisTick = mutableSetOf<InputBind>()

    /**
     * Called by InputManager when recomputing action states.
     *
     * For Mordant, this means "was this bind pressed since the last update?"
     */
    override fun pollPressed(bind: InputBind): Boolean = bind in pressedThisTick

//    override fun receiveEvent(event: InputEvent): InputReceiver.Status<Unit> {
//        when (event) {
//            is KeyboardEvent -> {
//                if (event.isCtrlC) {
//                    return InputReceiver.Status.Finished
//                }
//
//                handleKeyboardEvent(event)
//
//                // Recompute action states from the presses we've accumulated.
//                updateActions()
//
//                // Consume the one-shot press pulses after actions are updated.
//                pressedThisTick.clear()
//            }
//
//            else -> {
//                // Ignore non-keyboard events for now.
//            }
//        }
//
//        return InputReceiver.Status.Continue
//    }

    private fun handleKeyboardEvent(event: KeyboardEvent) {
        val bind = event.toInputBind() ?: return
        pressedThisTick += bind
    }

    private fun KeyboardEvent.toInputBind(): InputBind? {
        val normalized = when (key) {
            "ArrowUp" -> "up"
            "ArrowDown" -> "down"
            "ArrowLeft" -> "left"
            "ArrowRight" -> "right"
            "Esc" -> "escape"
            " " -> "space"
            else -> key.lowercase()
        }

        return when (normalized) {
            "w" -> InputBind.W
            "a" -> InputBind.A
            "s" -> InputBind.S
            "d" -> InputBind.D
            "up" -> InputBind.UP
            "down" -> InputBind.DOWN
            "left" -> InputBind.LEFT
            "right" -> InputBind.RIGHT
            "enter" -> InputBind.ENTER
            "escape" -> InputBind.ESCAPE
            "space" -> InputBind.SPACE
            else -> if (normalized.length == 1) InputBind.from(normalized) else null
        }
    }
}
