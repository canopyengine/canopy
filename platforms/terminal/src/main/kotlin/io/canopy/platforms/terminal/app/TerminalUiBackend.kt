package io.canopy.platforms.terminal.app

import kotlin.math.ceil
import kotlin.math.floor
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import io.canopy.engine.ui.UiBackend
import io.canopy.engine.ui.UiRect
import io.canopy.engine.ui.UiSize

/** Terminal-cell implementation of the shared UI backend; layouts remain platform independent. */
internal class TerminalUiBackend(private val terminal: Terminal, private val submit: (List<TerminalUiSpan>) -> Unit) :
    UiBackend {
    private data class Glyph(val text: String, val width: Int, val focused: Boolean)

    private var columns = 0
    private var rows = 0
    private var cells = emptyArray<Array<Glyph?>>()

    override fun measureText(text: String, maxWidth: Double, wrap: Boolean): UiSize {
        val lines = textLines(text, maxWidth, wrap)
        return UiSize(lines.maxOfOrNull { line -> line.sumOf { width(it) } }?.toDouble() ?: 0.0, lines.size.toDouble())
    }

    override fun viewport(size: UiSize) = UiSize((size.width - 1).coerceAtLeast(0.0), size.height)

    override fun measureButton(text: String, maxWidth: Double, wrap: Boolean): UiSize =
        measureText(buttonLabel(text), maxWidth, wrap)

    override fun drawButton(
        text: String,
        bounds: UiRect,
        clip: UiRect,
        focused: Boolean,
        enabled: Boolean,
        wrap: Boolean,
    ) {
        val style = when {
            !enabled -> "\u001b[2m"
            focused -> "\u001b[1;7m"
            else -> "\u001b[36m"
        }
        drawText(style + buttonLabel(text) + "\u001b[0m", bounds, clip, false, wrap)
    }

    private fun buttonLabel(text: String): String = text.split('\n').joinToString("\n") { "[ $it ]" }

    override fun begin(viewport: UiSize) {
        columns = floor(viewport.width).toInt().coerceAtLeast(0)
        rows = floor(viewport.height).toInt().coerceAtLeast(0)
        cells = Array(rows) { arrayOfNulls(columns) }
    }

    override fun drawText(text: String, bounds: UiRect, clip: UiRect, focused: Boolean, wrap: Boolean) {
        val left = ceil(clip.x).toInt().coerceIn(0, columns)
        val right = floor(clip.x + clip.width).toInt().coerceIn(left, columns)
        val top = ceil(clip.y).toInt().coerceIn(0, rows)
        val bottom = floor(clip.y + clip.height).toInt().coerceIn(top, rows)
        val lines = textLines(text, bounds.width, wrap)
        lines.forEachIndexed { rowIndex, clusters ->
            val y = ceil(bounds.y).toInt() + rowIndex
            if (y !in top until bottom || rowIndex >= floor(bounds.height).toInt()) return@forEachIndexed
            var x = ceil(bounds.x).toInt()
            for (cluster in clusters) {
                val width = width(cluster)
                if (width == 0) continue
                if (x >= left && x + width <= right) {
                    // Removing every cell of the previous glyph prevents half-wide characters after overlap.
                    for (cell in x until x + width) {
                        val previous = cells[y][cell]
                        if (previous != null) {
                            for (index in cells[y].indices) {
                                if (cells[y][index] === previous) cells[y][index] = null
                            }
                        }
                    }
                    val glyph = Glyph(cluster, width, focused)
                    for (cell in x until x + width) cells[y][cell] = glyph
                }
                x += width
            }
        }
    }

    override fun end() {
        val spans = mutableListOf<TerminalUiSpan>()
        cells.forEachIndexed { y, row ->
            var x = 0
            while (x < columns) {
                val glyph = row[x]
                if (glyph == null) {
                    x++
                } else {
                    val start = x
                    val text = buildString {
                        while (x < columns) {
                            val next = row[x] ?: break
                            if (next.focused) append("\u001b[7m")
                            append(next.text)
                            if (next.focused) append("\u001b[0m")
                            x += next.width
                        }
                    }
                    spans.add(TerminalUiSpan(start, y, text))
                }
            }
        }
        submit(spans)
    }

    private fun textLines(text: String, maxWidth: Double, wrap: Boolean): List<List<String>> {
        val available = floor(maxWidth).toInt().coerceAtLeast(0)
        return text.split('\n').flatMap { raw ->
            // Mordant expands tabs and neutralizes styling into spans; only printable graphemes are stored.
            val safe = safeTerminalText(raw)
            val clusters = Text(safe, whitespace = Whitespace.PRE).render(terminal, Int.MAX_VALUE)
                .lines.firstOrNull()?.flatMap { span ->
                    GRAPHEMES.findAll(span.text).map { span.style(it.value) }.toList()
                }.orEmpty()
            val result = mutableListOf<List<String>>()
            var line = mutableListOf<String>()
            var used = 0
            for (cluster in clusters) {
                val size = width(cluster)
                if (wrap && used + size > available && line.isNotEmpty()) {
                    result.add(line)
                    line = mutableListOf()
                    used = 0
                }
                if (size > available && wrap) continue
                line.add(cluster)
                used += size
            }
            result.add(line)
            result
        }
    }

    private fun width(cluster: String): Int = Text(cluster, whitespace = Whitespace.PRE)
        .measure(terminal, Int.MAX_VALUE).max
}

private val GRAPHEMES = Regex("\\X")

/** A positioned, bounded grapheme produced by the shared layout backend. */
internal data class TerminalUiSpan(val x: Int, val y: Int, val text: String)
