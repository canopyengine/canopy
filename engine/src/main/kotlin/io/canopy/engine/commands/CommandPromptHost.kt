package io.canopy.engine.commands

import io.canopy.engine.app.App
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.input.InputFocus
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
    /** Whether submitted lines replace raw key editing and use exact `:console` to toggle editor activation. */
    val isLineInput: Boolean get() = false

    /** Binds the entered editor owner; null releases platform-owned UI without disposing the editor. */
    fun bind(owner: CommandPrompt?) {}

    /** Presents a copied snapshot of an open prompt on the lifecycle thread. */
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
    private var focusLease: AutoCloseable? = null
    private var presented = false
    private var capturedThisFrame = false
    private var skipLineEnter = false
    private var toggleText: String? = null

    internal val blocksGameplay: Boolean
        get() = capturedThisFrame || interactive()?.isOpen == true

    internal fun attach(prompt: CommandPrompt) {
        check(entered == null || entered === prompt) { "Only one command prompt may enter a host" }
        if (entered === prompt) return
        val focus = manager<InputFocus>()
        focusLease = focus.register(
            owner = prompt,
            priority = Int.MAX_VALUE,
            capturesGameplay = { interactive()?.isOpen == true },
            beginFrame = { beginInputFrame() }
        ) { route(it) }
        entered = prompt
        try {
            presentation.bind(prompt)
        } catch (failure: Throwable) {
            entered = null
            focusLease?.close()
            focusLease = null
            try {
                presentation.bind(null)
            } catch (cleanup: Throwable) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    internal fun detach(prompt: CommandPrompt) {
        if (entered !== prompt) return
        entered = null
        focusLease?.close()
        focusLease = null
        skipLineEnter = false
        releasePresentation()
    }

    override fun onEnter() {
        manager<InputFocus>()
    }

    internal fun beginInputFrame() {
        capturedThisFrame = interactive()?.isOpen == true
        toggleText = null
    }

    internal fun route(event: InputEvent): Boolean {
        val prompt = interactive() ?: return capturedThisFrame.also { if (it) event.consume() }
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
            prompt.toggleKey?.let { event.key.name.removeSuffix("_KEY") == it.name.removeSuffix("_KEY") } == true &&
            event.state == InputState.JustPressed
        ) {
            prompt.toggle()
            capturedThisFrame = true
            toggleText = when {
                event.key == Key.SPACE -> " "
                event.key.name.removeSuffix("_KEY").length == 1 -> event.key.name.removeSuffix("_KEY")
                else -> null
            }
            event.consume()
            return true
        }
        // Once a frame captured focus, later events cannot leak after a handler closes the editor.
        if (!prompt.isOpen) {
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
        val prompt = interactive()
        if (prompt?.isOpen == true) {
            presented = true
            presentation.render(prompt.presentationSnapshot())
        } else {
            hidePresentation()
        }
    }

    override fun onExit() {
        entered = null
        focusLease?.close()
        focusLease = null
        skipLineEnter = false
        capturedThisFrame = false
        releasePresentation()
    }

    private fun releasePresentation() {
        var failure: Throwable? = null
        try {
            hidePresentation()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            presentation.bind(null)
        } catch (error: Throwable) {
            val first = failure
            if (first == null) {
                failure = error
            } else if (first !== error) {
                first.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun interactive(): CommandPrompt? = current()?.takeIf { it.isVisibleInTree }

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
