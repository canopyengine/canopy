package io.canopy.engine.graphics

import io.canopy.engine.ui.UiColor

/** A reusable printable ASCII glyph and optional color; null inherits the grid's text color. */
data class GridSymbol(val glyph: Char, val color: UiColor? = null) {
    init {
        require(glyph in ' '..'~') { "Grid symbols must be printable ASCII characters" }
    }
}
