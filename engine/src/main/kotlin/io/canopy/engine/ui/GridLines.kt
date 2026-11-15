package io.canopy.engine.ui

import kotlin.math.max
import io.canopy.engine.graphics.GridSymbol

/** Synchronous read-only glyph/decorative view; UI children remain in the retained node tree. */
interface GridSurface {
    /** Positive number of columns. */
    val columns: Int

    /** Logical rows, zero for an empty automatic grid. */
    val rows: Int

    /** Portable stroke intent; empty glyph content is configured separately. */
    val lines: GridLines

    /** Reads glyph content synchronously for a validated cell coordinate during painting. */
    operator fun get(x: Int, y: Int): GridSymbol
}

/** Portable line pattern. Backends choose native strokes or equivalent terminal characters. */
enum class LinePattern { None, Single, Double, Rounded, Dotted }

/**
 * Grid stroke intent. Width is in host logical units; cell backends resolve any visible stroke to one cell.
 * Null color inherits appearance. Styling is independent of empty-cell glyphs.
 */
data class LineStyle(val pattern: LinePattern, val color: UiColor? = null, val width: Double = 1.0) {
    init {
        require(width.isFinite() && width > 0.0)
    }
    companion object {
        val None = LineStyle(LinePattern.None)
        val Single = LineStyle(LinePattern.Single)
        val Double = LineStyle(LinePattern.Double)
        val Rounded = LineStyle(LinePattern.Rounded)
        val Dotted = LineStyle(LinePattern.Dotted)
    }
}

/**
 * Independent outer border and internal dividers. [inner] is a convenience default for both axes;
 * [horizontal] and [vertical] override it independently. Lines occupy reserved space, never cell content.
 */
data class GridLines(
    val outer: LineStyle = LineStyle.None,
    val inner: LineStyle = LineStyle.None,
    val horizontal: LineStyle? = null,
    val vertical: LineStyle? = null,
) {
    /** Effective horizontal style; null overrides continue to inherit through copy(inner = ...). */
    val horizontalStyle: LineStyle get() = horizontal ?: inner

    /** Effective vertical style, independent from the horizontal override. */
    val verticalStyle: LineStyle get() = vertical ?: inner
}

/** Backend-resolved line dimensions. All outer edges share [outer]; divider dimensions are axis-specific. */
data class GridLineMetrics(val outer: Double, val horizontal: Double, val vertical: Double) {
    init {
        require(listOf(outer, horizontal, vertical).all { it.isFinite() && it >= 0.0 })
    }
}

/** Immutable zero-based cell coordinate; grid bounds are checked before content mutation or layout. */
data class GridPosition(val column: Int, val row: Int) {
    init {
        require(column >= 0 && row >= 0)
    }
}

/**
 * Read-only shared cell geometry supplied to a backend during painting. Coordinates are local to the grid.
 * Sparse row-height overrides keep large glyph boards bounded by actual UI content, not board dimensions.
 */
class GridGeometry internal constructor(
    val columns: Int,
    val rows: Int,
    val cellWidth: Double,
    val defaultHeight: Double,
    rowHeights: Map<Int, Double>,
    val gap: Double,
    val lineGap: Double,
    val padding: Double,
    val metrics: GridLineMetrics,
) {
    private val heights = rowHeights.toMap()

    /** Full natural extent, including lines and gaps. Empty grids have no border extent. */
    val size: UiSize = if (rows == 0) {
        UiSize(0.0, 0.0)
    } else {
        UiSize(
            metrics.outer * 2 + columns.toDouble() * cellWidth + (columns - 1) * (gap + metrics.vertical),
            metrics.outer * 2 + rows.toDouble() * defaultHeight + heights.values.sumOf { it - defaultHeight } +
                (rows - 1) * (lineGap + metrics.horizontal)
        )
    }

    /** Outer cell rectangle, excluding divider space and including cell padding. */
    fun cell(column: Int, row: Int): UiRect {
        require(column in 0 until columns && row in 0 until rows)
        return UiRect(
            metrics.outer + column * (cellWidth + gap + metrics.vertical),
            rowTop(row),
            cellWidth,
            heights[row] ?: defaultHeight
        )
    }

    /** Cell content rectangle. Glyph and UI content share this area and its clip. */
    fun content(column: Int, row: Int): UiRect = cell(column, row).let {
        UiRect(it.x + padding, it.y + padding, max(0.0, it.width - 2 * padding), max(0.0, it.height - 2 * padding))
    }

    /** Position of an internal row divider, including padding but excluding the following gap. */
    fun rowTop(row: Int): Double = metrics.outer + row.toDouble() * (defaultHeight + lineGap + metrics.horizontal) +
        heights.entries.sumOf { if (it.key < row) it.value - defaultHeight else 0.0 }

    /** Candidate cell column at a logical x coordinate; callers must exclude divider/gap rectangles. */
    fun columnAt(x: Double): Int = if (cellWidth + gap + metrics.vertical <= 0.0) {
        0
    } else {
        kotlin.math.floor((x - metrics.outer) / (cellWidth + gap + metrics.vertical)).toInt().coerceIn(0, columns - 1)
    }

    /** Candidate row found without walking the logical board; callers must exclude divider/gap rectangles. */
    fun rowAt(y: Double): Int {
        if (rows == 0) return 0
        var low = 0
        var high = rows - 1
        while (low < high) {
            val mid = low + ((high.toLong() - low + 1) / 2).toInt()
            if (rowTop(mid) <= y) low = mid else high = mid - 1
        }
        return low
    }
}
