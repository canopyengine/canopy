package io.canopy.platforms.terminal.app

import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import io.canopy.engine.commands.CommandPromptSnapshot
import io.canopy.engine.core.managers.Manager

/** One lifecycle-thread owner composes world and command rows onto the same bounded terminal surface. */
internal class TerminalSurface(
    private val terminal: Terminal,
    private val viewport: () -> Size,
    private val output: (String) -> Unit,
    private val lineMode: () -> Boolean,
) : Manager {
    private var world: List<String>? = null
    private var ui = emptyList<TerminalUiSpan>()
    private var prompt: CommandPromptSnapshot? = null
    private val screen = TerminalCellScreen(terminal)
    private var submitted = false
    private var batching = false
    private var sessionOpen = false
    private var sessionManaged = false
    private var shutdownHook: Thread? = null

    @Synchronized
    fun openSession() {
        if (sessionOpen) return
        val hook = Thread({
            try {
                closeSession()
            } catch (_: Throwable) {
                // At VM shutdown there is no remaining engine lifecycle to report terminal I/O failures.
            }
        }, "canopy-terminal-cleanup")
        Runtime.getRuntime().addShutdownHook(hook)
        shutdownHook = hook
        sessionManaged = true
        sessionOpen = true
        output("\u001b[?1049h\u001b[?25l")
        screen.clear()
    }

    @Synchronized
    fun closeSession() {
        if (!sessionOpen) return
        output("\u001b[0m\u001b[?25h\u001b[?1049l")
        sessionOpen = false
        shutdownHook?.let { hook ->
            try {
                Runtime.getRuntime().removeShutdownHook(hook)
            } catch (_: IllegalStateException) {
                // Shutdown hooks cannot be removed once VM shutdown has started.
            }
        }
        shutdownHook = null
        screen.clear()
    }

    fun beginFrame() {
        batching = true
    }

    fun endFrame() {
        batching = false
        if (submitted) redraw()
    }

    override fun onUpdate(delta: Float) {
        // Manager dispatch continues while paused and even when no prompt or world callback renders.
        if (world != null || ui.isNotEmpty() || prompt != null || submitted) redraw()
    }

    override fun onExit() {
        world = null
        ui = emptyList()
        prompt = null
        screen.clear()
        submitted = false
        batching = false
        closeSession()
    }

    fun renderWorld(lines: List<String>) {
        world = lines.toList()
        redraw()
    }

    fun renderUi(spans: List<TerminalUiSpan>) {
        ui = spans.toList()
        redraw()
    }

    fun renderPrompt(snapshot: CommandPromptSnapshot) {
        prompt = snapshot
        redraw()
    }

    fun hidePrompt(redraw: Boolean = true) {
        prompt = null
        // Clearing ownership before writing lets later world frames recover from an output failure.
        if (redraw) redraw()
    }

    internal fun editorRow(snapshot: CommandPromptSnapshot, width: Int): String {
        if (width <= 0) return ""
        // Plain editor content scrolls at extended grapheme boundaries; the stored draft is never altered.
        val prefix = SGR.replace(safeTerminalText(snapshot.prefix), "").replace("\t", " ")
        val prefixWidth = Text(prefix, whitespace = Whitespace.PRE).measure(terminal, Int.MAX_VALUE).max
        val visiblePrefix = if (prefixWidth < width) prefix else ""
        val available = width - if (visiblePrefix.isEmpty()) 0 else prefixWidth
        val draft = SGR.replace(safeTerminalText(snapshot.draft), "").replace("\t", " ")
        val clusters = GRAPHEME.findAll(draft).map { it.value }.toList()
        var cells = 0
        val tail = mutableListOf<String>()
        for (cluster in clusters.asReversed()) {
            val size = Text(cluster, whitespace = Whitespace.PRE).measure(terminal, Int.MAX_VALUE).max
            if (cells + size > available) break
            tail.add(cluster)
            cells += size
        }
        return visiblePrefix + tail.asReversed().joinToString("")
    }

    private fun redraw() {
        submitted = true
        if (lineMode() || batching || sessionManaged && !sessionOpen) return
        val size = viewport()
        val height = size.height.coerceAtLeast(0)
        // Reserve the final column to avoid emulator-dependent autowrap and scrolling.
        val width = (size.width - 1).coerceAtLeast(0)
        screen.begin(width, height)
        world.orEmpty().flatMap { it.split('\n') }.take(height).forEachIndexed { y, row ->
            screen.paint(row, 0, y)
        }
        ui.forEach { screen.paint(it.text, it.x, it.y) }
        val cursor = if (prompt != null && height > 0 && width > 0) {
            Text(editorRow(prompt!!, width), whitespace = Whitespace.PRE).measure(terminal, Int.MAX_VALUE).max to
                height - 1
        } else {
            0 to 0
        }
        screen.flush(cursor) { frame ->
            val visibility = if (!sessionOpen) {
                ""
            } else if (prompt == null) {
                "\u001b[?25l"
            } else {
                "\u001b[?25h"
            }
            output("\u001b[?2026h" + visibility + frame + "\u001b[?2026l")
        }
    }
}

/** Preserve only SGR styling; cursor/OSC/control input cannot escape the composed row. */
internal fun safeTerminalText(text: String): String = buildString {
    var offset = 0
    while (offset < text.length) {
        val char = text[offset]
        if (char == '\u001b') {
            val match = SGR.find(text, offset)?.takeIf { it.range.first == offset }
            if (match != null) {
                append(match.value)
                offset += match.value.length
                continue
            }
        }
        if (char == '\u2028' || char == '\u2029') {
            append(' ')
        } else if (!char.isISOControl() || char == '\t') {
            append(char)
        }
        offset++
    }
}

private val SGR = Regex("\u001b\\[[0-9;:]*m")

private val GRAPHEME = Regex("\\X")
