package io.canopy.adapters.libgdx.input

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import io.canopy.engine.input.binds.InputBind
import io.mockk.every
import io.mockk.mockk

class GdxInputManagerMappingTests {

    private val originalInput = Gdx.input

    @AfterTest
    fun cleanup() {
        Gdx.input = originalInput
    }

    @Test
    fun `all keyboard binds map to a concrete gdx key`() {
        val expected = mapOf(
            InputBind.A to Input.Keys.A,
            InputBind.B to Input.Keys.B,
            InputBind.C to Input.Keys.C,
            InputBind.D to Input.Keys.D,
            InputBind.E to Input.Keys.E,
            InputBind.F to Input.Keys.F,
            InputBind.G to Input.Keys.G,
            InputBind.H to Input.Keys.H,
            InputBind.I to Input.Keys.I,
            InputBind.J to Input.Keys.J,
            InputBind.K to Input.Keys.K,
            InputBind.L to Input.Keys.L,
            InputBind.M to Input.Keys.M,
            InputBind.N to Input.Keys.N,
            InputBind.O to Input.Keys.O,
            InputBind.P to Input.Keys.P,
            InputBind.Q to Input.Keys.Q,
            InputBind.R to Input.Keys.R,
            InputBind.S to Input.Keys.S,
            InputBind.T to Input.Keys.T,
            InputBind.U to Input.Keys.U,
            InputBind.V to Input.Keys.V,
            InputBind.W to Input.Keys.W,
            InputBind.X to Input.Keys.X,
            InputBind.Y to Input.Keys.Y,
            InputBind.Z to Input.Keys.Z,
            InputBind.NUM_0 to Input.Keys.NUM_0,
            InputBind.NUM_1 to Input.Keys.NUM_1,
            InputBind.NUM_2 to Input.Keys.NUM_2,
            InputBind.NUM_3 to Input.Keys.NUM_3,
            InputBind.NUM_4 to Input.Keys.NUM_4,
            InputBind.NUM_5 to Input.Keys.NUM_5,
            InputBind.NUM_6 to Input.Keys.NUM_6,
            InputBind.NUM_7 to Input.Keys.NUM_7,
            InputBind.NUM_8 to Input.Keys.NUM_8,
            InputBind.NUM_9 to Input.Keys.NUM_9,
            InputBind.LEFT to Input.Keys.LEFT,
            InputBind.RIGHT to Input.Keys.RIGHT,
            InputBind.UP to Input.Keys.UP,
            InputBind.DOWN to Input.Keys.DOWN,
            InputBind.SPACE to Input.Keys.SPACE,
            InputBind.ENTER to Input.Keys.ENTER,
            InputBind.ESCAPE to Input.Keys.ESCAPE,
            InputBind.TAB to Input.Keys.TAB,
            InputBind.BACKSPACE to Input.Keys.BACKSPACE,
            InputBind.INSERT to Input.Keys.INSERT,
            InputBind.DELETE to Input.Keys.FORWARD_DEL,
            InputBind.HOME to Input.Keys.HOME,
            InputBind.END to Input.Keys.END,
            InputBind.PAGE_UP to Input.Keys.PAGE_UP,
            InputBind.PAGE_DOWN to Input.Keys.PAGE_DOWN,
            InputBind.SHIFT_LEFT to Input.Keys.SHIFT_LEFT,
            InputBind.SHIFT_RIGHT to Input.Keys.SHIFT_RIGHT,
            InputBind.CTRL_LEFT to Input.Keys.CONTROL_LEFT,
            InputBind.CTRL_RIGHT to Input.Keys.CONTROL_RIGHT,
            InputBind.ALT_LEFT to Input.Keys.ALT_LEFT,
            InputBind.ALT_RIGHT to Input.Keys.ALT_RIGHT,
            InputBind.META_LEFT to Input.Keys.SYM,
            InputBind.META_RIGHT to Input.Keys.SYM,
            InputBind.CAPS_LOCK to Input.Keys.CAPS_LOCK,
            InputBind.NUM_LOCK to Input.Keys.NUM,
            InputBind.SCROLL_LOCK to Input.Keys.SCROLL_LOCK,
            InputBind.PRINT_SCREEN to Input.Keys.PRINT_SCREEN,
            InputBind.PAUSE to Input.Keys.PAUSE,
            InputBind.GRAVE to Input.Keys.GRAVE,
            InputBind.MINUS to Input.Keys.MINUS,
            InputBind.EQUALS to Input.Keys.EQUALS,
            InputBind.LEFT_BRACKET to Input.Keys.LEFT_BRACKET,
            InputBind.RIGHT_BRACKET to Input.Keys.RIGHT_BRACKET,
            InputBind.BACKSLASH to Input.Keys.BACKSLASH,
            InputBind.SEMICOLON to Input.Keys.SEMICOLON,
            InputBind.APOSTROPHE to Input.Keys.APOSTROPHE,
            InputBind.COMMA to Input.Keys.COMMA,
            InputBind.PERIOD to Input.Keys.PERIOD,
            InputBind.SLASH to Input.Keys.SLASH,
            InputBind.F1 to Input.Keys.F1,
            InputBind.F2 to Input.Keys.F2,
            InputBind.F3 to Input.Keys.F3,
            InputBind.F4 to Input.Keys.F4,
            InputBind.F5 to Input.Keys.F5,
            InputBind.F6 to Input.Keys.F6,
            InputBind.F7 to Input.Keys.F7,
            InputBind.F8 to Input.Keys.F8,
            InputBind.F9 to Input.Keys.F9,
            InputBind.F10 to Input.Keys.F10,
            InputBind.F11 to Input.Keys.F11,
            InputBind.F12 to Input.Keys.F12,
            InputBind.NUMPAD_0 to Input.Keys.NUMPAD_0,
            InputBind.NUMPAD_1 to Input.Keys.NUMPAD_1,
            InputBind.NUMPAD_2 to Input.Keys.NUMPAD_2,
            InputBind.NUMPAD_3 to Input.Keys.NUMPAD_3,
            InputBind.NUMPAD_4 to Input.Keys.NUMPAD_4,
            InputBind.NUMPAD_5 to Input.Keys.NUMPAD_5,
            InputBind.NUMPAD_6 to Input.Keys.NUMPAD_6,
            InputBind.NUMPAD_7 to Input.Keys.NUMPAD_7,
            InputBind.NUMPAD_8 to Input.Keys.NUMPAD_8,
            InputBind.NUMPAD_9 to Input.Keys.NUMPAD_9,
            InputBind.NUMPAD_ADD to Input.Keys.NUMPAD_ADD,
            InputBind.NUMPAD_SUBTRACT to Input.Keys.NUMPAD_SUBTRACT,
            InputBind.NUMPAD_MULTIPLY to Input.Keys.NUMPAD_MULTIPLY,
            InputBind.NUMPAD_DIVIDE to Input.Keys.NUMPAD_DIVIDE,
            InputBind.NUMPAD_DECIMAL to Input.Keys.NUMPAD_DOT,
            InputBind.NUMPAD_ENTER to Input.Keys.NUMPAD_ENTER
        )

        val input = mockk<Input>()
        every { input.isKeyPressed(any()) } answers
            { firstArg<Int>() == Input.Keys.UNKNOWN || firstArg<Int>() in expected.values }
        every { input.isButtonPressed(any()) } returns false
        Gdx.input = input

        val manager = GdxInputManager()
        expected.keys.forEach { bind ->
            assertTrue(manager.isPressed(bind), "Expected $bind to map to a queried GDX key")
        }
    }

    @Test
    fun `all mouse binds map to a concrete gdx button`() {
        val expected = mapOf(
            InputBind.LEFT_MOUSE to Input.Buttons.LEFT,
            InputBind.RIGHT_MOUSE to Input.Buttons.RIGHT,
            InputBind.MIDDLE_MOUSE to Input.Buttons.MIDDLE,
            InputBind.BACK_MOUSE to Input.Buttons.BACK,
            InputBind.FORWARD_MOUSE to Input.Buttons.FORWARD
        )

        val input = mockk<Input>()
        every { input.isButtonPressed(any()) } answers { firstArg<Int>() in expected.values }
        every { input.isKeyPressed(any()) } returns false
        Gdx.input = input

        val manager = GdxInputManager()
        expected.keys.forEach { bind ->
            assertTrue(manager.isPressed(bind), "Expected $bind to map to a queried GDX button")
        }
    }
}
