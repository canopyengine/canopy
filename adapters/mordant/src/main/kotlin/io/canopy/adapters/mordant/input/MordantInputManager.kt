package io.canopy.adapters.mordant.input

import com.github.ajalt.mordant.input.isCtrlC
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.toInputBind
import io.canopy.engine.input.binds.toKey
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
        // 1. drain eventQueue → handleEvent()
        // 2. call updateActions()
        super.processEvents()
    }

    /**
     * Accepts Mordant's KeyboardEvent directly and enqueues as engine InputEvent(s).
     * Call this from TerminalApp's input loop.
     *
     * Emits:
     * - A [KeyInputEvent] for any key that maps to a known [InputBind] (for action-based input)
     * - A [TextInputEvent] for any printable character (for raw text input / command prompts)
     */
    fun enqueueMordantKeyEvent(event: com.github.ajalt.mordant.input.KeyboardEvent): Unit = synchronized(eventQueue) {
        if (event.isCtrlC) return@synchronized

        // Enqueue KeyInputEvent for mapped binds (action system)
        val bind = event.toInputBind()
        if (bind != null) {
            enqueue(
                KeyInputEvent(
                    key = bind.toKey(),
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
            event.key == "space" || event.key == " " -> " "
            event.key.codePointCount(0, event.key.length) == 1 -> event.key
            else -> null
        }
        if (text != null && !event.ctrl && !event.alt && text.none(Char::isISOControl)) {
            enqueue(TextInputEvent(text = text))
        }
    }

    /**
     * Key normalization → InputBind mapping
     */
    private fun com.github.ajalt.mordant.input.KeyboardEvent.toInputBind(): InputBind? {
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

            "backspace" -> InputBind.BACKSPACE

            "space" -> InputBind.SPACE

            else -> {
                if (normalized.length == 1) {
                    runCatching { InputBind.valueOf(normalized.uppercase()) }.getOrNull()
                } else {
                    null
                }
            }
        }
    }
}
