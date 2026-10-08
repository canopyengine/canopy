package io.canopy.adapters.mordant.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.github.ajalt.mordant.input.KeyboardEvent
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent

class MordantKeyboardTranslationTests {
    @Test
    fun `reported letters digits punctuation editing navigation locks and function keys use canonical identities`() {
        val expected = mapOf(
            "a" to Key.A, "W" to Key.W, "z" to Key.Z,
            "0" to Key.NUM_0, "1" to Key.NUM_1, "9" to Key.NUM_9,
            " " to Key.SPACE, "Space" to Key.SPACE, "Spacebar" to Key.SPACE,
            "`" to Key.GRAVE, "-" to Key.MINUS, "=" to Key.EQUALS,
            "[" to Key.LEFT_BRACKET, "]" to Key.RIGHT_BRACKET, "\\" to Key.BACKSLASH,
            ";" to Key.SEMICOLON, "'" to Key.APOSTROPHE, "," to Key.COMMA, "." to Key.PERIOD, "/" to Key.SLASH,
            "ArrowUp" to Key.UP, "ArrowDown" to Key.DOWN, "ArrowLeft" to Key.LEFT, "ArrowRight" to Key.RIGHT,
            "Enter" to Key.ENTER, "Esc" to Key.ESCAPE, "Escape" to Key.ESCAPE, "Backspace" to Key.BACKSPACE,
            "Tab" to Key.TAB, "Insert" to Key.INSERT, "Delete" to Key.DELETE, "Home" to Key.HOME, "End" to Key.END,
            "PageUp" to Key.PAGE_UP, "PageDown" to Key.PAGE_DOWN,
            "CapsLock" to Key.CAPS_LOCK, "NumLock" to Key.NUM_LOCK, "ScrollLock" to Key.SCROLL_LOCK,
            "PrintScreen" to Key.PRINT_SCREEN, "Pause" to Key.PAUSE,
            "F1" to Key.F1, "F2" to Key.F2, "F3" to Key.F3, "F4" to Key.F4,
            "F5" to Key.F5, "F6" to Key.F6, "F7" to Key.F7, "F8" to Key.F8,
            "F9" to Key.F9, "F10" to Key.F10, "F11" to Key.F11, "F12" to Key.F12
        )
        expected.forEach { (reported, key) ->
            val input = MordantInputManager()
            input.enqueueMordantKeyEvent(KeyboardEvent(reported))
            input.processEvents()
            assertEquals(listOf(key), input.eventsThisFrame.filterIsInstance<KeyInputEvent>().map { it.key }, reported)
            if (key == Key.SPACE) {
                assertEquals(listOf(" "), input.eventsThisFrame.filterIsInstance<TextInputEvent>().map { it.text })
            }
        }
    }

    @Test
    fun `digit tab delete and punctuation events drive their existing saved action bindings`() {
        val input = MordantInputManager()
        input.mapActions(
            "digit" to listOf(InputBind.NUM_1),
            "tab" to listOf(InputBind.TAB),
            "delete" to listOf(InputBind.DELETE),
            "punctuation" to listOf(InputBind.SEMICOLON)
        )
        listOf("1", "Tab", "Delete", ";").forEach { input.enqueueMordantKeyEvent(KeyboardEvent(it)) }
        input.processEvents()
        listOf("digit", "tab", "delete", "punctuation").forEach { assertTrue(input.isActionJustPressed(it), it) }
        input.processEvents()
        listOf("digit", "tab", "delete", "punctuation").forEach { assertTrue(input.isActionJustReleased(it), it) }
    }

    @Test
    fun `modifier flags and unsided reports never invent physical modifier or numpad keys`() {
        val input = MordantInputManager()
        input.mapActions("left" to listOf(InputBind.CTRL_LEFT), "right" to listOf(InputBind.SHIFT_RIGHT))
        input.enqueueMordantKeyEvent(KeyboardEvent("Control"))
        input.enqueueMordantKeyEvent(KeyboardEvent("Alt"))
        input.enqueueMordantKeyEvent(KeyboardEvent("Shift"))
        input.enqueueMordantKeyEvent(KeyboardEvent("A", ctrl = true, alt = true, shift = true))
        input.processEvents()
        assertEquals(
            listOf(
                KeyInputEvent(Key.CTRL, state = InputState.JustPressed),
                KeyInputEvent(Key.ALT, state = InputState.JustPressed),
                KeyInputEvent(Key.SHIFT, state = InputState.JustPressed),
                KeyInputEvent(Key.A, ctrl = true, alt = true, shift = true, state = InputState.JustPressed)
            ),
            input.eventsThisFrame
        )
        assertTrue(input.isActionReleased("left"))
        assertTrue(input.isActionReleased("right"))
    }

    @Test
    fun `Unicode and shifted symbols remain exact text without physical layout guesses`() {
        val input = MordantInputManager()
        val texts = listOf("é", "😀", "K", "!", ":", "€")
        texts.forEach { input.enqueueMordantKeyEvent(KeyboardEvent(it, shift = true)) }
        input.enqueueMordantKeyEvent(KeyboardEvent("Z", shift = true))
        input.enqueueMordantKeyEvent(KeyboardEvent("Unidentified"))
        input.enqueueMordantKeyEvent(KeyboardEvent("\u001b"))
        input.enqueueMordantKeyEvent(KeyboardEvent("c", ctrl = true))
        input.processEvents()
        assertEquals(texts + "Z", input.eventsThisFrame.filterIsInstance<TextInputEvent>().map { it.text })
        assertEquals(
            listOf(KeyInputEvent(Key.Z, shift = true, state = InputState.JustPressed)),
            input.eventsThisFrame.filterIsInstance<KeyInputEvent>()
        )
    }
}
