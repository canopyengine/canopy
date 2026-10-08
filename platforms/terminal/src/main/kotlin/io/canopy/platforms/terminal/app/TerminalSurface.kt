package io.canopy.platforms.terminal.app

import kotlin.math.ceil
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
    private val panelRows: () -> Int,
    private val lineMode: () -> Boolean,
    private val panelFraction: () -> Double = { 1.0 },
) : Manager {
    private var world: List<String>? = null
    private var prompt: CommandPromptSnapshot? = null
    private var previous: String? = null
    private var previousSize: Pair<Int, Int>? = null

    override fun onUpdate(delta: Float) {
        // Manager dispatch continues while paused and even when no prompt or world callback renders.
        if (world != null || prompt != null || previous != null) redraw()
    }

    override fun onExit() {
        world = null
        prompt = null
        previous = null
        previousSize = null
    }

    fun renderWorld(lines: List<String>) {
        world = lines.toList()
        redraw()
    }

    fun renderPrompt(snapshot: CommandPromptSnapshot) {
        prompt = snapshot
        redraw()
    }

    fun hidePrompt() {
        prompt = null
        // Clearing ownership before writing lets later world frames recover from an output failure.
        previous = null
        redraw()
    }

    private fun editorRow(snapshot: CommandPromptSnapshot, width: Int): String {
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

    private fun clipRow(row: String, width: Int): String = buildString {
        // Mordant's nowrap renderer expands tabs and parses styling. Clip its spans ourselves: its wrapping
        // renderer wraps whitespace-delimited words even with TRUNCATE, escaping a physical terminal row.
        val line = Text(safeTerminalText(row), whitespace = Whitespace.PRE)
            .render(terminal, Int.MAX_VALUE).lines.firstOrNull() ?: return@buildString
        var cells = 0
        for (span in line) {
            val kept = StringBuilder()
            for (match in GRAPHEME.findAll(span.text)) {
                val cluster = match.value
                val clusterWidth = Text(cluster, whitespace = Whitespace.PRE).measure(terminal, Int.MAX_VALUE).max
                if (cells + clusterWidth > width) {
                    if (kept.isNotEmpty()) append(span.style(kept.toString()))
                    return@buildString
                }
                kept.append(cluster)
                cells += clusterWidth
            }
            if (kept.isNotEmpty()) append(span.style(kept.toString()))
        }
    }

    private fun redraw() {
        if (lineMode()) return
        val size = viewport()
        val height = size.height.coerceAtLeast(0)
        // Reserve the last cell: writing it can wrap or scroll on terminals with differing autowrap behavior.
        val width = size.width.coerceAtLeast(1) - 1
        val snapshot = prompt
        val rows = MutableList(height) { "" }
        val panelHeight = if (snapshot == null) {
            0
        } else {
            ceil(height * panelFraction()).toInt()
                .coerceIn(1, minOf(panelRows(), maxOf(1, height - 1)))
        }
        val worldHeight = (height - panelHeight).coerceAtLeast(0)
        world.orEmpty().flatMap { it.split('\n') }.take(worldHeight).forEachIndexed { index, row -> rows[index] = row }
        if (snapshot != null && height > 0) {
            val first = height - panelHeight
            val transcript = snapshot.transcript.flatMap { it.split('\n') }.takeLast(panelHeight - 1)
            transcript.forEachIndexed { index, row -> rows[first + index] = row }
            rows[height - 1] = editorRow(snapshot, width)
        }
        val frame = buildString {
            append("\u001b[2J\u001b[H")
            if (width > 0) {
                rows.forEachIndexed { index, row ->
                    val clipped = clipRow(row, width)
                    if (clipped.isNotEmpty()) {
                        append("\u001b[").append(index + 1).append(";1H").append(clipped)
                    }
                }
            }
            if (snapshot != null && height > 0 && size.width > 0) {
                val editorWidth = Text(
                    editorRow(snapshot, width),
                    whitespace = Whitespace.PRE
                ).measure(terminal, Int.MAX_VALUE).max
                append("\u001b[").append(height).append(';').append(editorWidth + 1).append('H')
            }
        }
        if (frame == previous && (size.width to size.height) == previousSize) return
        output(frame)
        // Only successful output is cached, so the same snapshot retries after a failed write.
        previous = frame
        previousSize = size.width to size.height
    }
}

/** Preserve only SGR styling; cursor/OSC/control input cannot escape the composed row. */
private fun safeTerminalText(text: String): String = buildString {
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
