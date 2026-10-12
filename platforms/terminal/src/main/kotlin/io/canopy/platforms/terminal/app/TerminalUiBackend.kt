package io.canopy.platforms.terminal.app

import kotlin.math.ceil
import kotlin.math.floor
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.withPadding
import io.canopy.engine.ui.UiAppearance
import io.canopy.engine.ui.UiBackend
import io.canopy.engine.ui.UiBorder
import io.canopy.engine.ui.UiColor
import io.canopy.engine.ui.UiRect
import io.canopy.engine.ui.UiSize

/** Terminal-cell implementation of the shared UI backend; layouts remain platform independent. */
internal class TerminalUiBackend(private val terminal: Terminal, private val submit: (List<TerminalUiSpan>) -> Unit) :
    UiBackend {
    private data class Glyph(val text: String, val width: Int)

    private var columns = 0
    private var rows = 0
    private var cells = emptyArray<Array<Glyph?>>()

    override fun measureText(text: String, maxWidth: Double, wrap: Boolean): UiSize {
        val lines = textLines(text, maxWidth, wrap)
        return UiSize(lines.maxOfOrNull { line -> line.sumOf { width(it) } }?.toDouble() ?: 0.0, lines.size.toDouble())
    }

    override fun viewport(size: UiSize) = UiSize((size.width - 1).coerceAtLeast(0.0), size.height)

    override fun measureButton(text: String, maxWidth: Double, wrap: Boolean, border: UiBorder): UiSize {
        val available = floor(maxWidth).toInt().coerceAtLeast(0)
        if (available == 0) return UiSize(0.0, 0.0)
        val rendered = buttonWidget(text, available, wrap, UiAppearance(), border).render(terminal, available)
        return UiSize(rendered.width.coerceAtMost(available).toDouble(), rendered.height.toDouble())
    }

    override fun drawButton(
        text: String,
        bounds: UiRect,
        clip: UiRect,
        focused: Boolean,
        enabled: Boolean,
        wrap: Boolean,
        appearance: UiAppearance,
        border: UiBorder,
    ) {
        val available = floor(bounds.width).toInt().coerceAtLeast(0)
        if (available == 0 || bounds.height < 1) return
        val lines =
            buttonWidget(text, available, wrap, appearance, border)
                .render(terminal, available)
                .lines
                .map { line -> line.joinToString("") { it.style(it.text) } }
        // Widget rendering remains in memory; the surface owns output and partial repainting.
        drawText(lines.joinToString("\n"), bounds, clip, false, false, appearance)
    }

    private fun buttonWidget(
        text: String,
        available: Int,
        wrap: Boolean,
        appearance: UiAppearance,
        border: UiBorder,
    ): Widget {
        val style = appearance.mordant()
        val safe = safeTerminalText(text)
        fun label(value: String, width: Int): Text {
            // Keep Canopy's grapheme wrapping: Mordant's word breaking can duplicate wide labels.
            val lines = textLines(value, width.toDouble(), wrap).joinToString("\n") { it.joinToString("") }
            return Text(style(lines), whitespace = Whitespace.PRE)
        }
        // A panel needs two border cells, two padding cells and at least one content cell.
        if (border == UiBorder.None) {
            if (available < 3) return label(safe, available)
            return label(safe, available - 2).withPadding(Padding(0, 1, 0, 1))
        }
        if (available < 5) {
            val compact = safe.split('\n').joinToString("\n") { "[ $it ]" }
            return label(compact, available)
        }
        return Panel(
            label(safe, available - 4),
            padding = Padding(0, 1, 0, 1),
            borderType = border.mordant(),
            borderStyle = style
        )
    }

    override fun drawBox(bounds: UiRect, clip: UiRect, appearance: UiAppearance, border: UiBorder) {
        val visible = bounds.intersect(clip).intersect(UiRect(0.0, 0.0, columns.toDouble(), rows.toDouble()))
        val left = ceil(visible.x).toInt()
        val right = floor(visible.x + visible.width).toInt()
        val top = ceil(visible.y).toInt()
        val bottom = floor(visible.y + visible.height).toInt()
        if (right <= left || bottom <= top) return
        // Bound temporary strings and iteration to drawable cells, even for huge off-screen panels.
        if (appearance.background != null) {
            val background = UiAppearance(background = appearance.background)
            val spaces = " ".repeat(right - left)
            for (row in top until bottom) {
                drawText(
                    spaces,
                    UiRect(left.toDouble(), row.toDouble(), (right - left).toDouble(), 1.0),
                    clip,
                    false,
                    false,
                    background
                )
            }
        }
        if (border == UiBorder.None || bounds.width < 2 || bounds.height < 2) return
        val type = border.mordant()
        val firstX = ceil(bounds.x)
        val lastX = firstX + floor(bounds.width) - 1
        val firstY = ceil(bounds.y)
        val lastY = firstY + floor(bounds.height) - 1
        fun edge(y: Double, start: String, middle: String, end: String) {
            if (y < top || y >= bottom) return
            val line = buildString {
                for (x in left until right) {
                    append(
                        when (x.toDouble()) {
                            firstX -> start
                            lastX -> end
                            else -> middle
                        }
                    )
                }
            }
            drawText(
                line,
                UiRect(left.toDouble(), y, (right - left).toDouble(), 1.0),
                clip,
                false,
                false,
                appearance
            )
        }
        edge(firstY, type.head.es, type.head.ew, type.head.sw)
        edge(lastY, type.foot.ne, type.foot.ew, type.foot.nw)
        for (row in top until bottom) {
            if (row.toDouble() == firstY || row.toDouble() == lastY) continue
            for (x in listOf(firstX, lastX)) {
                if (x >= left && x < right) {
                    drawText(type.body.ns, UiRect(x, row.toDouble(), 1.0, 1.0), clip, false, false, appearance)
                }
            }
        }
    }

    override fun begin(viewport: UiSize) {
        columns = floor(viewport.width).toInt().coerceAtLeast(0)
        rows = floor(viewport.height).toInt().coerceAtLeast(0)
        cells = Array(rows) { arrayOfNulls(columns) }
    }

    override fun drawText(
        text: String,
        bounds: UiRect,
        clip: UiRect,
        focused: Boolean,
        wrap: Boolean,
        appearance: UiAppearance,
    ) {
        val left = ceil(clip.x).toInt().coerceIn(0, columns)
        val right = floor(clip.x + clip.width).toInt().coerceIn(left, columns)
        val top = ceil(clip.y).toInt().coerceIn(0, rows)
        val bottom = floor(clip.y + clip.height).toInt().coerceIn(top, rows)
        val visual = if (focused &&
            appearance.inverse == null
        ) {
            appearance.merge(UiAppearance(inverse = true))
        } else {
            appearance
        }
        val style = visual.mordant()
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
                    val glyph = Glyph(style(cluster), width)
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
                    val text =
                        buildString {
                            while (x < columns) {
                                val next = row[x] ?: break
                                append(next.text)
                                x += next.width
                            }
                        }
                    spans.add(TerminalUiSpan(start, y, text))
                }
            }
        }
        submit(spans)
    }

    override fun abort() {
        cells = emptyArray()
        columns = 0
        rows = 0
    }

    private fun textLines(text: String, maxWidth: Double, wrap: Boolean): List<List<String>> {
        val available = floor(maxWidth).toInt().coerceAtLeast(0)
        return text.split('\n').flatMap { raw ->
            // Mordant expands tabs and neutralizes styling into spans; only printable graphemes are stored.
            val safe = safeTerminalText(raw)
            val clusters =
                Text(safe, whitespace = Whitespace.PRE)
                    .render(terminal, Int.MAX_VALUE)
                    .lines
                    .firstOrNull()
                    ?.flatMap { span ->
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
        .measure(terminal, Int.MAX_VALUE)
        .max
}

private val GRAPHEMES = Regex("\\X")

/** A positioned, bounded grapheme produced by the shared layout backend. */
internal data class TerminalUiSpan(val x: Int, val y: Int, val text: String)

private fun UiBorder.mordant(): BorderType = when (this) {
    UiBorder.None -> BorderType.BLANK
    UiBorder.Rounded -> BorderType.ROUNDED
    UiBorder.Square -> BorderType.SQUARE
    UiBorder.Double -> BorderType.DOUBLE
}

private fun UiAppearance.mordant(): TextStyle {
    var style = TextStyle(bold = bold, dim = dim, italic = italic, underline = underline, inverse = inverse)
    foreground?.let { color ->
        style += when (color) {
            UiColor.Default -> TextStyles.resetForeground.style
            is UiColor.Rgb -> TextColors.rgb(color.red / 255.0, color.green / 255.0, color.blue / 255.0)
        }
    }
    background?.let { color ->
        style += when (color) {
            UiColor.Default -> TextStyles.resetBackground.style
            is UiColor.Rgb -> TextColors.rgb(color.red / 255.0, color.green / 255.0, color.blue / 255.0).bg
        }
    }
    return style
}
