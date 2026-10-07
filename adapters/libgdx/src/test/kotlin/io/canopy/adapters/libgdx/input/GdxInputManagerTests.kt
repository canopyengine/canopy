package io.canopy.adapters.libgdx.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.lang.reflect.Proxy
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import io.canopy.engine.input.binds.InputBind

class GdxInputManagerTests {
    @Test
    fun `all legacy bindings poll the explicit native key or mouse code without ordinal assumptions`() {
        // Arrange: expected mappings captured from the adapter before canonicalizing engine identities.
        val expected = mapOf(
            InputBind.A to ("isKeyPressed" to Input.Keys.A),
            InputBind.B to ("isKeyPressed" to Input.Keys.B),
            InputBind.C to ("isKeyPressed" to Input.Keys.C),
            InputBind.D to ("isKeyPressed" to Input.Keys.D),
            InputBind.E to ("isKeyPressed" to Input.Keys.E),
            InputBind.F to ("isKeyPressed" to Input.Keys.F),
            InputBind.G to ("isKeyPressed" to Input.Keys.G),
            InputBind.H to ("isKeyPressed" to Input.Keys.H),
            InputBind.I to ("isKeyPressed" to Input.Keys.I),
            InputBind.J to ("isKeyPressed" to Input.Keys.J),
            InputBind.K to ("isKeyPressed" to Input.Keys.K),
            InputBind.L to ("isKeyPressed" to Input.Keys.L),
            InputBind.M to ("isKeyPressed" to Input.Keys.M),
            InputBind.N to ("isKeyPressed" to Input.Keys.N),
            InputBind.O to ("isKeyPressed" to Input.Keys.O),
            InputBind.P to ("isKeyPressed" to Input.Keys.P),
            InputBind.Q to ("isKeyPressed" to Input.Keys.Q),
            InputBind.R to ("isKeyPressed" to Input.Keys.R),
            InputBind.S to ("isKeyPressed" to Input.Keys.S),
            InputBind.T to ("isKeyPressed" to Input.Keys.T),
            InputBind.U to ("isKeyPressed" to Input.Keys.U),
            InputBind.V to ("isKeyPressed" to Input.Keys.V),
            InputBind.W to ("isKeyPressed" to Input.Keys.W),
            InputBind.X to ("isKeyPressed" to Input.Keys.X),
            InputBind.Y to ("isKeyPressed" to Input.Keys.Y),
            InputBind.Z to ("isKeyPressed" to Input.Keys.Z),
            InputBind.NUM_0 to ("isKeyPressed" to Input.Keys.NUM_0),
            InputBind.NUM_1 to ("isKeyPressed" to Input.Keys.NUM_1),
            InputBind.NUM_2 to ("isKeyPressed" to Input.Keys.NUM_2),
            InputBind.NUM_3 to ("isKeyPressed" to Input.Keys.NUM_3),
            InputBind.NUM_4 to ("isKeyPressed" to Input.Keys.NUM_4),
            InputBind.NUM_5 to ("isKeyPressed" to Input.Keys.NUM_5),
            InputBind.NUM_6 to ("isKeyPressed" to Input.Keys.NUM_6),
            InputBind.NUM_7 to ("isKeyPressed" to Input.Keys.NUM_7),
            InputBind.NUM_8 to ("isKeyPressed" to Input.Keys.NUM_8),
            InputBind.NUM_9 to ("isKeyPressed" to Input.Keys.NUM_9),
            InputBind.LEFT to ("isKeyPressed" to Input.Keys.LEFT),
            InputBind.RIGHT to ("isKeyPressed" to Input.Keys.RIGHT),
            InputBind.UP to ("isKeyPressed" to Input.Keys.UP),
            InputBind.DOWN to ("isKeyPressed" to Input.Keys.DOWN),
            InputBind.SPACE to ("isKeyPressed" to Input.Keys.SPACE),
            InputBind.ENTER to ("isKeyPressed" to Input.Keys.ENTER),
            InputBind.ESCAPE to ("isKeyPressed" to Input.Keys.ESCAPE),
            InputBind.TAB to ("isKeyPressed" to Input.Keys.TAB),
            InputBind.BACKSPACE to ("isKeyPressed" to Input.Keys.BACKSPACE),
            InputBind.INSERT to ("isKeyPressed" to Input.Keys.INSERT),
            InputBind.DELETE to ("isKeyPressed" to Input.Keys.FORWARD_DEL),
            InputBind.HOME to ("isKeyPressed" to Input.Keys.HOME),
            InputBind.END to ("isKeyPressed" to Input.Keys.END),
            InputBind.PAGE_UP to ("isKeyPressed" to Input.Keys.PAGE_UP),
            InputBind.PAGE_DOWN to ("isKeyPressed" to Input.Keys.PAGE_DOWN),
            InputBind.SHIFT_LEFT to ("isKeyPressed" to Input.Keys.SHIFT_LEFT),
            InputBind.SHIFT_RIGHT to ("isKeyPressed" to Input.Keys.SHIFT_RIGHT),
            InputBind.CTRL_LEFT to ("isKeyPressed" to Input.Keys.CONTROL_LEFT),
            InputBind.CTRL_RIGHT to ("isKeyPressed" to Input.Keys.CONTROL_RIGHT),
            InputBind.ALT_LEFT to ("isKeyPressed" to Input.Keys.ALT_LEFT),
            InputBind.ALT_RIGHT to ("isKeyPressed" to Input.Keys.ALT_RIGHT),
            InputBind.META_LEFT to ("isKeyPressed" to Input.Keys.SYM),
            InputBind.META_RIGHT to ("isKeyPressed" to Input.Keys.SYM),
            InputBind.CAPS_LOCK to ("isKeyPressed" to Input.Keys.CAPS_LOCK),
            InputBind.NUM_LOCK to ("isKeyPressed" to Input.Keys.NUM),
            InputBind.SCROLL_LOCK to ("isKeyPressed" to Input.Keys.SCROLL_LOCK),
            InputBind.PRINT_SCREEN to ("isKeyPressed" to Input.Keys.PRINT_SCREEN),
            InputBind.PAUSE to ("isKeyPressed" to Input.Keys.PAUSE),
            InputBind.GRAVE to ("isKeyPressed" to Input.Keys.GRAVE),
            InputBind.MINUS to ("isKeyPressed" to Input.Keys.MINUS),
            InputBind.EQUALS to ("isKeyPressed" to Input.Keys.EQUALS),
            InputBind.LEFT_BRACKET to ("isKeyPressed" to Input.Keys.LEFT_BRACKET),
            InputBind.RIGHT_BRACKET to ("isKeyPressed" to Input.Keys.RIGHT_BRACKET),
            InputBind.BACKSLASH to ("isKeyPressed" to Input.Keys.BACKSLASH),
            InputBind.SEMICOLON to ("isKeyPressed" to Input.Keys.SEMICOLON),
            InputBind.APOSTROPHE to ("isKeyPressed" to Input.Keys.APOSTROPHE),
            InputBind.COMMA to ("isKeyPressed" to Input.Keys.COMMA),
            InputBind.PERIOD to ("isKeyPressed" to Input.Keys.PERIOD),
            InputBind.SLASH to ("isKeyPressed" to Input.Keys.SLASH),
            InputBind.F1 to ("isKeyPressed" to Input.Keys.F1),
            InputBind.F2 to ("isKeyPressed" to Input.Keys.F2),
            InputBind.F3 to ("isKeyPressed" to Input.Keys.F3),
            InputBind.F4 to ("isKeyPressed" to Input.Keys.F4),
            InputBind.F5 to ("isKeyPressed" to Input.Keys.F5),
            InputBind.F6 to ("isKeyPressed" to Input.Keys.F6),
            InputBind.F7 to ("isKeyPressed" to Input.Keys.F7),
            InputBind.F8 to ("isKeyPressed" to Input.Keys.F8),
            InputBind.F9 to ("isKeyPressed" to Input.Keys.F9),
            InputBind.F10 to ("isKeyPressed" to Input.Keys.F10),
            InputBind.F11 to ("isKeyPressed" to Input.Keys.F11),
            InputBind.F12 to ("isKeyPressed" to Input.Keys.F12),
            InputBind.NUMPAD_0 to ("isKeyPressed" to Input.Keys.NUMPAD_0),
            InputBind.NUMPAD_1 to ("isKeyPressed" to Input.Keys.NUMPAD_1),
            InputBind.NUMPAD_2 to ("isKeyPressed" to Input.Keys.NUMPAD_2),
            InputBind.NUMPAD_3 to ("isKeyPressed" to Input.Keys.NUMPAD_3),
            InputBind.NUMPAD_4 to ("isKeyPressed" to Input.Keys.NUMPAD_4),
            InputBind.NUMPAD_5 to ("isKeyPressed" to Input.Keys.NUMPAD_5),
            InputBind.NUMPAD_6 to ("isKeyPressed" to Input.Keys.NUMPAD_6),
            InputBind.NUMPAD_7 to ("isKeyPressed" to Input.Keys.NUMPAD_7),
            InputBind.NUMPAD_8 to ("isKeyPressed" to Input.Keys.NUMPAD_8),
            InputBind.NUMPAD_9 to ("isKeyPressed" to Input.Keys.NUMPAD_9),
            InputBind.NUMPAD_ADD to ("isKeyPressed" to Input.Keys.NUMPAD_ADD),
            InputBind.NUMPAD_SUBTRACT to ("isKeyPressed" to Input.Keys.NUMPAD_SUBTRACT),
            InputBind.NUMPAD_MULTIPLY to ("isKeyPressed" to Input.Keys.NUMPAD_MULTIPLY),
            InputBind.NUMPAD_DIVIDE to ("isKeyPressed" to Input.Keys.NUMPAD_DIVIDE),
            InputBind.NUMPAD_DECIMAL to ("isKeyPressed" to Input.Keys.NUMPAD_DOT),
            InputBind.NUMPAD_ENTER to ("isKeyPressed" to Input.Keys.NUMPAD_ENTER),
            InputBind.LEFT_MOUSE to ("isButtonPressed" to Input.Buttons.LEFT),
            InputBind.RIGHT_MOUSE to ("isButtonPressed" to Input.Buttons.RIGHT),
            InputBind.MIDDLE_MOUSE to ("isButtonPressed" to Input.Buttons.MIDDLE),
            InputBind.BACK_MOUSE to ("isButtonPressed" to Input.Buttons.BACK),
            InputBind.FORWARD_MOUSE to ("isButtonPressed" to Input.Buttons.FORWARD)
        )
        val original = Gdx.input
        val polled = mutableListOf<Pair<String, Int>>()
        Gdx.input =
            Proxy.newProxyInstance(Input::class.java.classLoader, arrayOf(Input::class.java)) { _, method, args ->
                check(method.name == "isKeyPressed" || method.name == "isButtonPressed")
                polled += method.name to (args[0] as Int)
                true
            } as Input
        try {
            val manager = GdxInputManager()
            // Act / Assert: every saved binding reaches exactly its native polling category and code.
            assertEquals(InputBind.entries.toSet(), expected.keys)
            expected.forEach { (bind, call) ->
                polled.clear()
                manager.mapActions("test" to listOf(bind))
                manager.processEvents()
                assertTrue(manager.isActionPressed("test"), bind.name)
                assertEquals(listOf(call), polled, bind.name)
            }
        } finally {
            Gdx.input = original
        }
    }
}
