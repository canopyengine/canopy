package io.canopy.engine.ui

import kotlin.math.max
import kotlin.math.min

/** Logical dimensions: cells for a terminal backend, pixels for a graphical backend. */
data class UiSize(val width: Double, val height: Double) {
    init {
        require(width.isFinite() && height.isFinite() && width >= 0 && height >= 0)
    }
}

/** Logical rectangle in viewport coordinates. */
data class UiRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    init {
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() && width >= 0 && height >= 0)
    }

    /** Intersection used to constrain backend drawing to every ancestor's clip. */
    fun intersect(other: UiRect): UiRect {
        val left = max(x, other.x)
        val top = max(y, other.y)
        return UiRect(
            left,
            top,
            max(0.0, min(x + width, other.x + other.width) - left),
            max(0.0, min(y + height, other.y + other.height) - top)
        )
    }
}

/** Dimension policy evaluated against the available parent dimension on every layout pass. */
sealed interface UiLength {
    /** Uses measured content. */
    data object Content : UiLength

    /** Receives remaining space, shared evenly with fill siblings. */
    data object Fill : UiLength

    /** Positive proportional share of remaining space in a flow container. */
    data class Weight(val value: Double) : UiLength {
        init {
            require(value.isFinite() && value > 0.0)
        }
    }

    /** Fixed logical units. */
    data class Fixed(val value: Double) : UiLength {
        init {
            require(value.isFinite() && value >= 0)
        }
    }

    /** Fraction of the parent's available size, in the inclusive range zero to one. */
    data class Fraction(val value: Double) : UiLength {
        init {
            require(value.isFinite() && value in 0.0..1.0)
        }
    }
}

/**
 * Universal component configuration. Padding and gap use backend logical units.
 * Placement and sizing are also available directly through [UiNode.place] and [UiNode.size].
 * @property place One parent-relative placement policy; flow parents reject explicit anchors.
 * @property arrangement Main-axis free-space distribution after sizing and minimum gaps.
 * @property lineGap Vertical separation between wrapping/grid rows.
 */
data class UiStyle(
    val width: UiLength = UiLength.Content,
    val height: UiLength = UiLength.Content,
    val padding: Double = 0.0,
    val gap: Double = 0.0,
    val alignment: CrossAlign = CrossAlign.Start,
    val wrap: Boolean = true,
    val clip: Boolean = true,
    val maxWidth: Double? = null,
    val maxHeight: Double? = null,
    val border: UiBorder? = null,
    val appearance: UiAppearance = UiAppearance(),
    val place: Place = Place.Auto,
    val arrangement: Arrange = Arrange.Start,
    val lineGap: Double = gap,
) {
    internal val layoutKey = listOf(
        width, height, padding, gap, alignment, wrap, clip, maxWidth, maxHeight, border,
        place, arrangement, lineGap
    )
    init {
        require(lineGap.isFinite() && lineGap >= 0.0)
        require(padding.isFinite() && gap.isFinite() && padding >= 0 && gap >= 0)
        require(maxWidth == null || maxWidth.isFinite() && maxWidth >= 0)
        require(maxHeight == null || maxHeight.isFinite() && maxHeight >= 0)
    }
}

/** Platform implements metrics and primitives; containers and sizing remain shared engine behavior. */
interface UiBackend {
    /** Resolves portable grid stroke widths. Terminal backends reserve one cell per enabled line. */
    fun measureGridLines(lines: GridLines): GridLineMetrics {
        fun width(line: LineStyle) = if (line.pattern == LinePattern.None) 0.0 else line.width
        return GridLineMetrics(width(lines.outer), width(lines.horizontalStyle), width(lines.verticalStyle))
    }

    /** Draws grid borders/dividers. Backends without line primitives must explicitly reject nonempty lines. */
    fun drawGridLines(
        lines: GridLines,
        geometry: GridGeometry,
        bounds: UiRect,
        clip: UiRect,
        appearance: UiAppearance,
    ) {
        require(
            listOf(lines.outer, lines.horizontalStyle, lines.verticalStyle).all {
                it.pattern == LinePattern.None
            }
        ) {
            "This UI backend does not support Grid lines"
        }
    }

    /**
     * Paints only visible glyph candidates and grid lines. UI controls are painted afterwards using the same
     * geometry. No logical-board-sized iteration or persistent retention of [grid] is permitted.
     */
    fun drawGridCells(
        grid: GridSurface,
        geometry: GridGeometry,
        bounds: UiRect,
        clip: UiRect,
        appearance: UiAppearance,
    ) {
        if (geometry.rows == 0) return
        val visible = bounds.intersect(clip)
        if (visible.width <= 0.0 || visible.height <= 0.0) return
        drawGridLines(grid.lines, geometry, bounds, visible, appearance)
        val firstColumn = geometry.columnAt(visible.x - bounds.x)
        val lastColumn = geometry.columnAt(visible.x + visible.width - bounds.x)
        val firstRow = geometry.rowAt(visible.y - bounds.y)
        val lastRow = geometry.rowAt(visible.y + visible.height - bounds.y)
        for (y in firstRow..lastRow) {
            for (x in firstColumn..lastColumn) {
                val cell = geometry.content(x, y)
                val area = layoutBounds(cell.copy(x = bounds.x + cell.x, y = bounds.y + cell.y))
                val ownClip = visible.intersect(area)
                if (ownClip.width <= 0.0 || ownClip.height <= 0.0) continue
                val symbol = grid[x, y]
                if (symbol.glyph == ' ' && symbol.color == null) continue
                drawText(
                    symbol.glyph.toString(),
                    area,
                    ownClip,
                    false,
                    false,
                    appearance.merge(UiAppearance(foreground = symbol.color))
                )
            }
        }
    }

    /** Natural horizontal divider size in this backend's logical units. */
    fun measureDivider(width: Double): UiSize = UiSize(width, measureText("M", Double.MAX_VALUE, false).height)

    /** Paints a horizontal divider into the pending frame. */
    fun drawDivider(bounds: UiRect, clip: UiRect, appearance: UiAppearance) {
        error("This UI backend does not support divider drawing")
    }

    /** Maps host geometry to the backend drawable area, such as reserving a terminal wrap column. */
    fun viewport(size: UiSize): UiSize = size

    /**
     * Resolves layout rectangles to host geometry before painting and hit testing. Graphical hosts retain
     * fractional coordinates by default; cell backends snap edges to their cell grid. Must be deterministic.
     */
    fun layoutBounds(bounds: UiRect): UiRect = bounds

    /** Measures wrapped or unwrapped text using this backend's cell/font metrics. */
    fun measureText(text: String, maxWidth: Double, wrap: Boolean): UiSize

    /** Measures an action including backend control decoration; defaults to plain text metrics. */
    fun measureButton(text: String, maxWidth: Double, wrap: Boolean, border: UiBorder = UiBorder.Rounded): UiSize =
        measureText(text, maxWidth, wrap)

    /** Starts a frame. */
    fun begin(viewport: UiSize) = Unit

    /** Draws text within its allocated bounds and ancestor clipping rectangle. */
    fun drawText(
        text: String,
        bounds: UiRect,
        clip: UiRect,
        focused: Boolean,
        wrap: Boolean = true,
        appearance: UiAppearance = UiAppearance(),
    )

    /** Paints an action with its focus and eligibility; defaults to the backend's text primitive. */
    fun drawButton(
        text: String,
        bounds: UiRect,
        clip: UiRect,
        focused: Boolean,
        enabled: Boolean,
        wrap: Boolean,
        appearance: UiAppearance = UiTheme.Default.button.merge(
            if (!enabled) {
                UiTheme.Default.disabledButton
            } else if (focused) {
                UiTheme.Default.focusedButton
            } else {
                UiAppearance.Empty
            }
        ),
        border: UiBorder = UiBorder.Rounded,
    ) = drawText(text, bounds, clip, focused, wrap, appearance)

    /** Fills a background and paints a border inside allocated bounds; containers remain shared layout. */
    fun drawBox(bounds: UiRect, clip: UiRect, appearance: UiAppearance, border: UiBorder) = Unit

    /** Commits a successfully painted frame. Output belongs here rather than in drawing primitives. */
    fun end() = Unit

    /**
     * Discards an incomplete frame after begin, drawing or end fails. Must not present pending content.
     * Backends holding temporary frame state must release it here; cleanup failures are suppressed
     * onto the original frame failure. Already emitted output cannot be rolled back.
     */
    fun abort() = Unit
}

/** Internal layout/primitive policy; users compose concrete components. */
internal enum class UiKind { Row, Column, Box, FlowRow, Grid, Text, Button }

/** Portable root composition layers; overlay roots are drawn after ordinary content. */
enum class UiLayer { Content, Overlay }
