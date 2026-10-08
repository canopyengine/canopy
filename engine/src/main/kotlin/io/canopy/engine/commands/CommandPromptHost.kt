package io.canopy.engine.commands

import io.canopy.engine.app.App
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent

/**
 * Immutable platform presentation snapshot with no node ownership. [outputSequence] counts appended output entries;
 * trimming the bounded transcript does not advance it. [transcript] is an independent read-only copy.
 */
class CommandPromptSnapshot internal constructor(
    /** Configured editor prefix. */
    val prefix: String,
    /** Current editable draft. */
    val draft: String,
    /** Retained output entries, oldest first. */
    val transcript: List<String>,
    /** Monotonic output cursor for this reusable prompt instance. */
    val outputSequence: Long,
)

/** Platform integration for command presentation; snapshots retain copied text without node ownership. */
interface CommandPromptPresentation {
    /** Whether submitted lines replace raw key editing and use exact `:console` to toggle visibility. */
    val isLineInput: Boolean get() = false

    /** Presents a copied snapshot of a visible prompt on the lifecycle thread. */
    fun render(snapshot: CommandPromptSnapshot)

    /** Releases presentation ownership and restores the platform's normal output. Idempotent. */
    fun hide()
}

/**
 * Platform-installed command focus service. Supports one entered prompt per host and processes input before
 * gameplay mapping, including while gameplay is paused. Applications normally receive this from TerminalApp.
 * All access belongs to the serialized lifecycle thread.
 */
class CommandPromptHost(val app: App<*>, private val presentation: CommandPromptPresentation) : Manager {
    private var entered: CommandPrompt? = null
    private var presented = false
    private var capturedThisFrame = false
    private var skipLineEnter = false
    private var toggleText: String? = null

    internal val blocksGameplay: Boolean
        get() = capturedThisFrame || current()?.isVisible == true

    internal fun attach(prompt: CommandPrompt) {
        check(entered == null || entered === prompt) { "Only one command prompt may enter a host" }
        entered = prompt
    }

    internal fun detach(prompt: CommandPrompt) {
        if (entered !== prompt) return
        entered = null
        skipLineEnter = false
        hidePresentation()
    }

    internal fun beginInputFrame() {
        capturedThisFrame = current()?.isVisible == true
        toggleText = null
    }

    internal fun route(event: InputEvent): Boolean {
        val prompt = current() ?: return capturedThisFrame.also { if (it) event.consume() }
        if (event is KeyInputEvent && event.isCtrlC()) return false
        val suppressedText = toggleText
        toggleText = null
        if (event is TextInputEvent && event.text.equals(suppressedText, ignoreCase = true)) {
            event.consume()
            return true
        }
        if (presentation.isLineInput && event is TextInputEvent && event.text == ":console") {
            prompt.toggle()
            skipLineEnter = true
            capturedThisFrame = true
            event.consume()
            return true
        }
        if (skipLineEnter && event is KeyInputEvent && event.key == Key.ENTER) {
            skipLineEnter = false
            event.consume()
            return true
        }
        if (!presentation.isLineInput &&
            event is KeyInputEvent &&
            event.key == prompt.toggleKey &&
            event.state == InputState.JustPressed
        ) {
            prompt.toggle()
            capturedThisFrame = true
            toggleText = if (event.ctrl || event.alt) null else event.key.toggleCharacter()
            event.consume()
            return true
        }
        // Once a frame captured focus, later events cannot leak after a handler hides the editor.
        if (!prompt.isVisible) {
            if (!capturedThisFrame) return false
            event.consume()
            return true
        }
        capturedThisFrame = true
        when (event) {
            is TextInputEvent -> prompt.appendText(event.text)
            is KeyInputEvent -> if (event.state == InputState.JustPressed) {
                when (event.key) {
                    Key.ENTER -> prompt.submitDraft()
                    Key.BACKSPACE -> prompt.backspace()
                    else -> Unit
                }
            }
            else -> Unit
        }
        event.consume()
        return true
    }

    override fun onUpdate(delta: Float) {
        val prompt = current()
        if (prompt?.isVisible == true) {
            presented = true
            presentation.render(prompt.presentationSnapshot())
        } else {
            hidePresentation()
        }
    }

    override fun onExit() {
        entered = null
        skipLineEnter = false
        capturedThisFrame = false
        hidePresentation()
    }

    private fun current(): CommandPrompt? {
        val prompt = entered ?: return null
        if (!prompt.isValid || !prompt.isInsideTree) {
            detach(prompt)
            return null
        }
        return prompt
    }

    private fun hidePresentation() {
        if (!presented) return
        presented = false
        presentation.hide()
    }
}

// Only suppress the printable event paired with a toggle, never an unrelated later editor event.
private fun Key.toggleCharacter(): String? = when {
    name.length == 1 -> name
    name.startsWith("NUM_") && name.length == 5 -> name.takeLast(1)
    else -> when (this) {
        Key.SPACE -> " "
        Key.GRAVE -> "`"
        Key.MINUS -> "-"
        Key.EQUALS -> "="
        Key.LEFT_BRACKET -> "["
        Key.RIGHT_BRACKET -> "]"
        Key.BACKSLASH -> "\\"
        Key.SEMICOLON -> ";"
        Key.APOSTROPHE -> "'"
        Key.COMMA -> ","
        Key.PERIOD -> "."
        Key.SLASH -> "/"
        else -> null
    }
}
