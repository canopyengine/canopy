package io.canopy.engine.graphics

import io.canopy.engine.ui.UiColor

/** A reusable printable ASCII glyph and optional color; null inherits the grid's text color. */
data class GridSymbol(val glyph: Char, val color: UiColor? = null) {
    init {
        require(glyph in ' '..'~') { "Grid symbols must be printable ASCII characters" }
    }
}

/** Read-only grid content consumed synchronously by a platform backend during a frame. */
interface GridContent {
    /** Positive number of logical columns. */
    val columns: Int

    /** Positive number of logical rows. */
    val rows: Int

    /** Nonnegative space between columns, measured in backend character widths. */
    val columnGap: Int

    /** Reads one displayed symbol; coordinates start at zero in the top-left corner. */
    operator fun get(x: Int, y: Int): GridSymbol
}
