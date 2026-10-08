package io.canopy.adapters.mordant.input

import com.github.ajalt.mordant.input.isCtrlC
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.binds.toInputBind
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent

/**
 * Mordant-backed input manager.
 *
 * Model:
 * - Async coroutine enqueues KeyboardEvent
 * - Base InputManager drains queue via processEvents()
 * - We build a per-frame "pressed" snapshot
 *
 * Limitation:
 * - Mordant has no key up/down → only press events
 * - So input behaves like "JustPressed only"
 */
class MordantInputManager : InputManager() {

    /**
     * Frame-local pressed binds (cleared every frame).
     */
    private val pressedThisFrame = mutableSetOf<InputBind>()

    /**
     * Called by InputManager.processEvents() for each queued event.
     */
    override fun handleEvent(event: InputEvent) {
        when (event) {
            is KeyInputEvent -> {
                // Allow direct KeyInputEvent enqueueing for testing
                val bind = event.key.toInputBind() ?: return
                if (event.state == InputState.Pressed || event.state == InputState.JustPressed) {
                    pressedThisFrame += bind
                }
            }

            else -> {
                // Mordant only produces keyboard events; ignore other event types
            }
        }
    }

    /**
     * Called by InputManager during action recomputation.
     */
    override fun pollPressed(bind: InputBind): Boolean = bind in pressedThisFrame

    /**
     * Override to reset frame state BEFORE processing events.
     */
    override fun processEvents() {
        // Reset snapshot for this frame
        pressedThisFrame.clear()

        // Let base class:
        // 1. drain queued events → handleEvent()
        // 2. call updateActions()
        super.processEvents()
    }

    /**
     * Accepts Mordant's KeyboardEvent directly and enqueues as engine InputEvent(s).
     * Call this from TerminalApp's input loop.
     *
     * Emits:
     * - A [KeyInputEvent] for known canonical keys; unsided modifiers have no physical action binding
     * - A [TextInputEvent] for any printable character (for raw text input / command prompts)
     * Related key and text events are published as one atomic batch.
     */
    fun enqueueMordantKeyEvent(event: com.github.ajalt.mordant.input.KeyboardEvent) {
        if (event.isCtrlC) return

        val events = buildList<InputEvent>(2) {
            // Enqueue KeyInputEvent for mapped binds (action system)
            val key = event.toKey()
            if (key != null) {
                add(
                    KeyInputEvent(
                        key = key,
                        ctrl = event.ctrl,
                        alt = event.alt,
                        shift = event.shift,
                        state = InputState.JustPressed
                    )
                )
            }

            // Enqueue TextInputEvent for printable characters (text input system)
            // A printable character is a single character that is not a special key
            val text = when {
                key == Key.SPACE -> " "
                event.key.codePointCount(0, event.key.length) == 1 -> event.key
                else -> null
            }
            if (text != null && !event.ctrl && !event.alt && text.none(Char::isISOControl)) {
                add(TextInputEvent(text = text))
            }
        }
        enqueueBatch(events)
    }

    // Mordant reports web KeyboardEvent.key names, not native codes. Never infer a modifier side or numpad
    // identity from a character. Shifted symbols and non-ASCII text retain text events without guessing a layout.
    private fun com.github.ajalt.mordant.input.KeyboardEvent.toKey(): Key? {
        val character = if (key.length == 1 && key[0] in 'A'..'Z') key.lowercase() else key
        return namedKeys[key.lowercase()] ?: characterKeys[character]
    }

    private companion object {
        val namedKeys = mapOf(
            "arrowup" to Key.UP,
            "up" to Key.UP,
            "arrowdown" to Key.DOWN,
            "down" to Key.DOWN,
            "arrowleft" to Key.LEFT,
            "left" to Key.LEFT,
            "arrowright" to Key.RIGHT,
            "right" to Key.RIGHT,
            "enter" to Key.ENTER,
            "escape" to Key.ESCAPE,
            "esc" to Key.ESCAPE,
            "space" to Key.SPACE,
            "spacebar" to Key.SPACE,
            "backspace" to Key.BACKSPACE,
            "tab" to Key.TAB,
            "insert" to Key.INSERT,
            "delete" to Key.DELETE,
            "del" to Key.DELETE,
            "home" to Key.HOME,
            "end" to Key.END,
            "pageup" to Key.PAGE_UP,
            "pagedown" to Key.PAGE_DOWN,
            "control" to Key.CTRL,
            "ctrl" to Key.CTRL,
            "alt" to Key.ALT,
            "shift" to Key.SHIFT,
            "capslock" to Key.CAPS_LOCK,
            "numlock" to Key.NUM_LOCK,
            "scrolllock" to Key.SCROLL_LOCK,
            "printscreen" to Key.PRINT_SCREEN,
            "pause" to Key.PAUSE,
            "f1" to Key.F1,
            "f2" to Key.F2,
            "f3" to Key.F3,
            "f4" to Key.F4,
            "f5" to Key.F5,
            "f6" to Key.F6,
            "f7" to Key.F7,
            "f8" to Key.F8,
            "f9" to Key.F9,
            "f10" to Key.F10,
            "f11" to Key.F11,
            "f12" to Key.F12
        )
        val characterKeys = mapOf(
            "a" to Key.A,
            "b" to Key.B,
            "c" to Key.C,
            "d" to Key.D,
            "e" to Key.E,
            "f" to Key.F,
            "g" to Key.G,
            "h" to Key.H,
            "i" to Key.I,
            "j" to Key.J,
            "k" to Key.K,
            "l" to Key.L,
            "m" to Key.M,
            "n" to Key.N,
            "o" to Key.O,
            "p" to Key.P,
            "q" to Key.Q,
            "r" to Key.R,
            "s" to Key.S,
            "t" to Key.T,
            "u" to Key.U,
            "v" to Key.V,
            "w" to Key.W,
            "x" to Key.X,
            "y" to Key.Y,
            "z" to Key.Z,
            "0" to Key.NUM_0,
            "1" to Key.NUM_1,
            "2" to Key.NUM_2,
            "3" to Key.NUM_3,
            "4" to Key.NUM_4,
            "5" to Key.NUM_5,
            "6" to Key.NUM_6,
            "7" to Key.NUM_7,
            "8" to Key.NUM_8,
            "9" to Key.NUM_9,
            " " to Key.SPACE,
            "`" to Key.GRAVE,
            "-" to Key.MINUS,
            "=" to Key.EQUALS,
            "[" to Key.LEFT_BRACKET,
            "]" to Key.RIGHT_BRACKET,
            "\\" to Key.BACKSLASH,
            ";" to Key.SEMICOLON,
            "'" to Key.APOSTROPHE,
            "," to Key.COMMA,
            "." to Key.PERIOD,
            "/" to Key.SLASH
        )
    }
}
