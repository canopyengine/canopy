package io.canopy.adapters.libgdx.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind

class GdxInputManager : InputManager() {

    override fun pollPressed(bind: InputBind): Boolean = when (bind.type) {
        InputBind.Type.Keyboard -> Gdx.input.isKeyPressed(bind.toGdxKey())
        InputBind.Type.Mouse -> Gdx.input.isButtonPressed(bind.toGdxMouse())
    }

    private fun InputBind.toGdxKey(): Int = when (this) {
        InputBind.A -> Input.Keys.A
        InputBind.B -> Input.Keys.B
        InputBind.C -> Input.Keys.C
        InputBind.D -> Input.Keys.D
        InputBind.E -> Input.Keys.E
        InputBind.F -> Input.Keys.F
        InputBind.G -> Input.Keys.G
        InputBind.H -> Input.Keys.H
        InputBind.I -> Input.Keys.I
        InputBind.J -> Input.Keys.J
        InputBind.K -> Input.Keys.K
        InputBind.L -> Input.Keys.L
        InputBind.M -> Input.Keys.M
        InputBind.N -> Input.Keys.N
        InputBind.O -> Input.Keys.O
        InputBind.P -> Input.Keys.P
        InputBind.Q -> Input.Keys.Q
        InputBind.R -> Input.Keys.R
        InputBind.S -> Input.Keys.S
        InputBind.T -> Input.Keys.T
        InputBind.U -> Input.Keys.U
        InputBind.V -> Input.Keys.V
        InputBind.W -> Input.Keys.W
        InputBind.X -> Input.Keys.X
        InputBind.Y -> Input.Keys.Y
        InputBind.Z -> Input.Keys.Z

        InputBind.NUM_0 -> Input.Keys.NUM_0
        InputBind.NUM_1 -> Input.Keys.NUM_1
        InputBind.NUM_2 -> Input.Keys.NUM_2
        InputBind.NUM_3 -> Input.Keys.NUM_3
        InputBind.NUM_4 -> Input.Keys.NUM_4
        InputBind.NUM_5 -> Input.Keys.NUM_5
        InputBind.NUM_6 -> Input.Keys.NUM_6
        InputBind.NUM_7 -> Input.Keys.NUM_7
        InputBind.NUM_8 -> Input.Keys.NUM_8
        InputBind.NUM_9 -> Input.Keys.NUM_9

        InputBind.LEFT -> Input.Keys.LEFT
        InputBind.RIGHT -> Input.Keys.RIGHT
        InputBind.UP -> Input.Keys.UP
        InputBind.DOWN -> Input.Keys.DOWN

        InputBind.SPACE -> Input.Keys.SPACE
        InputBind.ENTER -> Input.Keys.ENTER
        InputBind.ESCAPE -> Input.Keys.ESCAPE
        InputBind.TAB -> Input.Keys.TAB
        InputBind.BACKSPACE -> Input.Keys.BACKSPACE
        InputBind.INSERT -> Input.Keys.INSERT
        InputBind.DELETE -> Input.Keys.FORWARD_DEL
        InputBind.HOME -> Input.Keys.HOME
        InputBind.END -> Input.Keys.END
        InputBind.PAGE_UP -> Input.Keys.PAGE_UP
        InputBind.PAGE_DOWN -> Input.Keys.PAGE_DOWN

        InputBind.SHIFT_LEFT -> Input.Keys.SHIFT_LEFT
        InputBind.SHIFT_RIGHT -> Input.Keys.SHIFT_RIGHT
        InputBind.CTRL_LEFT -> Input.Keys.CONTROL_LEFT
        InputBind.CTRL_RIGHT -> Input.Keys.CONTROL_RIGHT
        InputBind.ALT_LEFT -> Input.Keys.ALT_LEFT
        InputBind.ALT_RIGHT -> Input.Keys.ALT_RIGHT
        InputBind.META_LEFT -> Input.Keys.SYM
        InputBind.META_RIGHT -> Input.Keys.SYM
        InputBind.CAPS_LOCK -> Input.Keys.CAPS_LOCK
        InputBind.NUM_LOCK -> Input.Keys.NUM
        InputBind.SCROLL_LOCK -> Input.Keys.SCROLL_LOCK
        InputBind.PRINT_SCREEN -> Input.Keys.PRINT_SCREEN
        InputBind.PAUSE -> Input.Keys.PAUSE

        InputBind.GRAVE -> Input.Keys.GRAVE
        InputBind.MINUS -> Input.Keys.MINUS
        InputBind.EQUALS -> Input.Keys.EQUALS
        InputBind.LEFT_BRACKET -> Input.Keys.LEFT_BRACKET
        InputBind.RIGHT_BRACKET -> Input.Keys.RIGHT_BRACKET
        InputBind.BACKSLASH -> Input.Keys.BACKSLASH
        InputBind.SEMICOLON -> Input.Keys.SEMICOLON
        InputBind.APOSTROPHE -> Input.Keys.APOSTROPHE
        InputBind.COMMA -> Input.Keys.COMMA
        InputBind.PERIOD -> Input.Keys.PERIOD
        InputBind.SLASH -> Input.Keys.SLASH

        InputBind.F1 -> Input.Keys.F1
        InputBind.F2 -> Input.Keys.F2
        InputBind.F3 -> Input.Keys.F3
        InputBind.F4 -> Input.Keys.F4
        InputBind.F5 -> Input.Keys.F5
        InputBind.F6 -> Input.Keys.F6
        InputBind.F7 -> Input.Keys.F7
        InputBind.F8 -> Input.Keys.F8
        InputBind.F9 -> Input.Keys.F9
        InputBind.F10 -> Input.Keys.F10
        InputBind.F11 -> Input.Keys.F11
        InputBind.F12 -> Input.Keys.F12

        InputBind.NUMPAD_0 -> Input.Keys.NUMPAD_0
        InputBind.NUMPAD_1 -> Input.Keys.NUMPAD_1
        InputBind.NUMPAD_2 -> Input.Keys.NUMPAD_2
        InputBind.NUMPAD_3 -> Input.Keys.NUMPAD_3
        InputBind.NUMPAD_4 -> Input.Keys.NUMPAD_4
        InputBind.NUMPAD_5 -> Input.Keys.NUMPAD_5
        InputBind.NUMPAD_6 -> Input.Keys.NUMPAD_6
        InputBind.NUMPAD_7 -> Input.Keys.NUMPAD_7
        InputBind.NUMPAD_8 -> Input.Keys.NUMPAD_8
        InputBind.NUMPAD_9 -> Input.Keys.NUMPAD_9
        InputBind.NUMPAD_ADD -> Input.Keys.NUMPAD_ADD
        InputBind.NUMPAD_SUBTRACT -> Input.Keys.NUMPAD_SUBTRACT
        InputBind.NUMPAD_MULTIPLY -> Input.Keys.NUMPAD_MULTIPLY
        InputBind.NUMPAD_DIVIDE -> Input.Keys.NUMPAD_DIVIDE
        InputBind.NUMPAD_DECIMAL -> Input.Keys.NUMPAD_DOT
        InputBind.NUMPAD_ENTER -> Input.Keys.NUMPAD_ENTER

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
