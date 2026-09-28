package io.canopy.engine.input.events

import io.canopy.engine.input.binds.Key
import io.canopy.engine.math.Vector2

/**
 * Represents an input event detected by GDX and mapped by the Input Dispatcher
 */
sealed class InputEvent(open val action: String, open val state: InputState) {
    /**
     * Whether or not this event was handled, for propagation concerns
     */
    internal var isHandled = false

    /**
     * Helper method
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

enum class InputState { Pressed, Released, JustPressed, JustReleased, Other }

class ButtonInputEvent(action: String, state: InputState) : InputEvent(action, state)

class MouseButtonEvent(val screenPos: Vector2, action: String, state: InputState) : InputEvent(action, state)

class MouseMoveEvent(val screenPos: Vector2, action: String) : InputEvent(action, InputState.Other)

data class TextInputEvent(val text: String) : InputEvent("text_input", InputState.JustPressed)
