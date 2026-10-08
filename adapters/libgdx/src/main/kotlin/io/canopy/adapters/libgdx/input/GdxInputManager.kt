package io.canopy.adapters.libgdx.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.binds.toKey

/** Polls keyboard and mouse bindings through the active LibGDX input service. */
class GdxInputManager : InputManager() {

    override fun pollPressed(bind: InputBind): Boolean = when (bind.type) {
        InputBind.Type.Keyboard -> Gdx.input.isKeyPressed(bind.toKey().toGdxKey())
        InputBind.Type.Mouse -> Gdx.input.isButtonPressed(bind.toGdxMouse())
    }

    private fun Key.toGdxKey(): Int = when (this) {
        Key.A -> Input.Keys.A
        Key.B -> Input.Keys.B
        Key.C -> Input.Keys.C
        Key.D -> Input.Keys.D
        Key.E -> Input.Keys.E
        Key.F -> Input.Keys.F
        Key.G -> Input.Keys.G
        Key.H -> Input.Keys.H
        Key.I -> Input.Keys.I
        Key.J -> Input.Keys.J
        Key.K -> Input.Keys.K
        Key.L -> Input.Keys.L
        Key.M -> Input.Keys.M
        Key.N -> Input.Keys.N
        Key.O -> Input.Keys.O
        Key.P -> Input.Keys.P
        Key.Q -> Input.Keys.Q
        Key.R -> Input.Keys.R
        Key.S -> Input.Keys.S
        Key.T -> Input.Keys.T
        Key.U -> Input.Keys.U
        Key.V -> Input.Keys.V
        Key.W -> Input.Keys.W
        Key.X -> Input.Keys.X
        Key.Y -> Input.Keys.Y
        Key.Z -> Input.Keys.Z

        Key.NUM_0 -> Input.Keys.NUM_0
        Key.NUM_1 -> Input.Keys.NUM_1
        Key.NUM_2 -> Input.Keys.NUM_2
        Key.NUM_3 -> Input.Keys.NUM_3
        Key.NUM_4 -> Input.Keys.NUM_4
        Key.NUM_5 -> Input.Keys.NUM_5
        Key.NUM_6 -> Input.Keys.NUM_6
        Key.NUM_7 -> Input.Keys.NUM_7
        Key.NUM_8 -> Input.Keys.NUM_8
        Key.NUM_9 -> Input.Keys.NUM_9

        Key.LEFT -> Input.Keys.LEFT
        Key.RIGHT -> Input.Keys.RIGHT
        Key.UP -> Input.Keys.UP
        Key.DOWN -> Input.Keys.DOWN

        Key.SPACE -> Input.Keys.SPACE
        Key.ENTER -> Input.Keys.ENTER
        Key.ESCAPE -> Input.Keys.ESCAPE
        Key.TAB -> Input.Keys.TAB
        Key.BACKSPACE -> Input.Keys.BACKSPACE
        Key.INSERT -> Input.Keys.INSERT
        Key.DELETE -> Input.Keys.FORWARD_DEL
        Key.HOME -> Input.Keys.HOME
        Key.END -> Input.Keys.END
        Key.PAGE_UP -> Input.Keys.PAGE_UP
        Key.PAGE_DOWN -> Input.Keys.PAGE_DOWN

        Key.SHIFT_LEFT -> Input.Keys.SHIFT_LEFT
        Key.SHIFT_RIGHT -> Input.Keys.SHIFT_RIGHT
        Key.CTRL_LEFT -> Input.Keys.CONTROL_LEFT
        Key.CTRL_RIGHT -> Input.Keys.CONTROL_RIGHT
        Key.ALT_LEFT -> Input.Keys.ALT_LEFT
        Key.ALT_RIGHT -> Input.Keys.ALT_RIGHT
        // LibGDX cannot distinguish Meta sides: both retain the existing SYM mapping.
        Key.META_LEFT -> Input.Keys.SYM
        Key.META_RIGHT -> Input.Keys.SYM
        Key.CAPS_LOCK -> Input.Keys.CAPS_LOCK
        Key.NUM_LOCK -> Input.Keys.NUM
        Key.SCROLL_LOCK -> Input.Keys.SCROLL_LOCK
        Key.PRINT_SCREEN -> Input.Keys.PRINT_SCREEN
        Key.PAUSE -> Input.Keys.PAUSE

        Key.GRAVE -> Input.Keys.GRAVE
        Key.MINUS -> Input.Keys.MINUS
        Key.EQUALS -> Input.Keys.EQUALS
        Key.LEFT_BRACKET -> Input.Keys.LEFT_BRACKET
        Key.RIGHT_BRACKET -> Input.Keys.RIGHT_BRACKET
        Key.BACKSLASH -> Input.Keys.BACKSLASH
        Key.SEMICOLON -> Input.Keys.SEMICOLON
        Key.APOSTROPHE -> Input.Keys.APOSTROPHE
        Key.COMMA -> Input.Keys.COMMA
        Key.PERIOD -> Input.Keys.PERIOD
        Key.SLASH -> Input.Keys.SLASH

        Key.F1 -> Input.Keys.F1
        Key.F2 -> Input.Keys.F2
        Key.F3 -> Input.Keys.F3
        Key.F4 -> Input.Keys.F4
        Key.F5 -> Input.Keys.F5
        Key.F6 -> Input.Keys.F6
        Key.F7 -> Input.Keys.F7
        Key.F8 -> Input.Keys.F8
        Key.F9 -> Input.Keys.F9
        Key.F10 -> Input.Keys.F10
        Key.F11 -> Input.Keys.F11
        Key.F12 -> Input.Keys.F12

        Key.NUMPAD_0 -> Input.Keys.NUMPAD_0
        Key.NUMPAD_1 -> Input.Keys.NUMPAD_1
        Key.NUMPAD_2 -> Input.Keys.NUMPAD_2
        Key.NUMPAD_3 -> Input.Keys.NUMPAD_3
        Key.NUMPAD_4 -> Input.Keys.NUMPAD_4
        Key.NUMPAD_5 -> Input.Keys.NUMPAD_5
        Key.NUMPAD_6 -> Input.Keys.NUMPAD_6
        Key.NUMPAD_7 -> Input.Keys.NUMPAD_7
        Key.NUMPAD_8 -> Input.Keys.NUMPAD_8
        Key.NUMPAD_9 -> Input.Keys.NUMPAD_9
        Key.NUMPAD_ADD -> Input.Keys.NUMPAD_ADD
        Key.NUMPAD_SUBTRACT -> Input.Keys.NUMPAD_SUBTRACT
        Key.NUMPAD_MULTIPLY -> Input.Keys.NUMPAD_MULTIPLY
        Key.NUMPAD_DIVIDE -> Input.Keys.NUMPAD_DIVIDE
        Key.NUMPAD_DECIMAL -> Input.Keys.NUMPAD_DOT
        Key.NUMPAD_ENTER -> Input.Keys.NUMPAD_ENTER

        else -> Input.Keys.UNKNOWN
    }

    private fun InputBind.toGdxMouse(): Int = when (this) {
        InputBind.LEFT_MOUSE -> Input.Buttons.LEFT
        InputBind.RIGHT_MOUSE -> Input.Buttons.RIGHT
        InputBind.MIDDLE_MOUSE -> Input.Buttons.MIDDLE
        InputBind.BACK_MOUSE -> Input.Buttons.BACK
        InputBind.FORWARD_MOUSE -> Input.Buttons.FORWARD
        else -> Input.Buttons.LEFT
    }
}
