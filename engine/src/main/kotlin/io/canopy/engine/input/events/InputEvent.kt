package io.canopy.engine.input.events

import io.canopy.engine.input.binds.Key
import io.canopy.engine.math.Vector2

/**
 * Backend-independent raw or mapped input event dispatched to nodes.
 */
sealed class InputEvent(open val action: String, open val state: InputState) {
    /**
     * Whether or not this event was handled, for propagation concerns
     */
    internal var isHandled = false

    /**
     * Marks the event handled. Node input traversal stops remaining descendants, siblings and behavior callbacks.
     * Host focus routing also consumes events before gameplay mapping. Do not reuse consumed events for dispatch.
     */
    fun consume() {
        isHandled = true
    }

    fun isActionPressed(action: String) = this.action == action && isPressedEvent()

    fun isActionJustPressed(action: String) = this.action == action && state == InputState.JustPressed

    fun isActionReleased(action: String) = this.action == action && isReleasedEvent()

    fun isActionJustReleased(action: String) = this.action == action && state == InputState.JustReleased

    fun isAnyActionPressed() = state == InputState.Pressed

    fun isAnyActionJustPressed() = state == InputState.JustPressed

    fun isAnyActionReleased() = state == InputState.Released

    fun isAnyActionJustReleased() = state == InputState.JustReleased

    protected fun isPressedEvent() = state in listOf(InputState.Pressed, InputState.JustPressed)

    protected fun isReleasedEvent() = state in listOf(InputState.Released, InputState.JustReleased)
}

/** Raw keyboard event with modifier flags and the key name as its action. */
data class KeyInputEvent(
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    override val state: InputState,
) : InputEvent(key.name, state) {

    fun isCtrlPressed() = ctrl
    fun isAltPressed() = alt
    fun isShiftPressed() = shift

    fun isCtrlCombo(target: Key): Boolean = key == target && ctrl && isPressedEvent()

    fun isCtrlC(): Boolean = isCtrlCombo(Key.C_KEY)
}

/** Held, released, transition, or non-button state. Transition states describe the latest input update. */
enum class InputState { Pressed, Released, JustPressed, JustReleased, Other }

/** Mapped action event carrying its current button state. */
class ButtonInputEvent(action: String, state: InputState) : InputEvent(action, state)

/** Mouse button action with its backend-provided screen position. */
class MouseButtonEvent(val screenPos: Vector2, action: String, state: InputState) : InputEvent(action, state)

/** Pointer movement with a backend-provided screen position and Other state. */
class MouseMoveEvent(val screenPos: Vector2, action: String) : InputEvent(action, InputState.Other)

/** Text submitted by a backend, dispatched as the text_input action with JustPressed state. */
data class TextInputEvent(val text: String) : InputEvent("text_input", InputState.JustPressed)
