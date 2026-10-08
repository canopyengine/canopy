package io.canopy.platforms.terminal.app

import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptPresentation
import io.canopy.engine.commands.CommandPromptSnapshot

/** Keeps copied presentation text only; input reading and rendering are serialized by the terminal host. */
internal class TerminalCommandPresentation(
    private val lineMode: () -> Boolean,
    private val output: (String) -> Unit,
    private val ui: TerminalCommandUi,
) : CommandPromptPresentation {
    override fun bind(owner: CommandPrompt?) = ui.bind(owner)

    override val isLineInput: Boolean get() = lineMode()
    private var previousLines = emptyList<String>()
    private var previousDraft: String? = null
    private var previousPrefix: String? = null
    private var previousLineMode = false
    private var outputSequence = 0L
    var isVisible = false
        private set

    override fun render(snapshot: CommandPromptSnapshot) {
        val lines = snapshot.transcript
        val draft = snapshot.draft
        val prefix = snapshot.prefix
        val changed = !isVisible ||
            lines != previousLines ||
            draft != previousDraft ||
            prefix != previousPrefix ||
            previousLineMode != isLineInput ||
            outputSequence != snapshot.outputSequence
        // The shared surface must see unchanged snapshots too: the viewport may have resized.
        if (!isLineInput) {
            ui.render(snapshot)
            remember(snapshot)
            return
        }
        if (!changed) return
        val added = if (!isVisible || !previousLineMode) {
            lines.size
        } else {
            (snapshot.outputSequence - outputSequence).coerceIn(0, lines.size.toLong()).toInt()
        }
        val newLines = lines.takeLast(added)
        if (newLines.isNotEmpty()) output(newLines.joinToString("\n", postfix = "\n", transform = ::safe))
        // No cursor commands or frame output may overwrite the blocking readLine editor.
        if (!isVisible ||
            !previousLineMode ||
            newLines.isNotEmpty() ||
            prefix != previousPrefix
        ) {
            output(safe(prefix) + safe(draft))
        }
        remember(snapshot)
    }

    private fun remember(snapshot: CommandPromptSnapshot) {
        isVisible = true
        previousLines = snapshot.transcript
        previousDraft = snapshot.draft
        previousPrefix = snapshot.prefix
        previousLineMode = isLineInput
        outputSequence = snapshot.outputSequence
    }

    override fun hide() {
        if (!isVisible) return
        isVisible = false
        previousLines = emptyList()
        previousDraft = null
        previousPrefix = null
        ui.hide()
        if (isLineInput) output("\n")
    }

    private fun safe(text: String): String = text.filter { !it.isISOControl() || it == '\n' || it == '\t' }
}
