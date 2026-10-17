package io.canopy.engine.ui

import kotlin.math.max
import kotlin.math.min
import io.canopy.engine.graphics.GridContent
import io.canopy.engine.graphics.GridStyle

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

/** Placement on a container's cross axis. */
enum class UiAlignment { Start, Center, End, Stretch }

/** Universal layout constraints. Padding and gap use backend logical units. */
data class UiStyle(
    val width: UiLength = UiLength.Content,
    val height: UiLength = UiLength.Content,
    val padding: Double = 0.0,
    val gap: Double = 0.0,
    val alignment: UiAlignment = UiAlignment.Start,
    val wrap: Boolean = true,
    val clip: Boolean = true,
    val maxWidth: Double? = null,
    val maxHeight: Double? = null,
    val border: UiBorder? = null,
    val appearance: UiAppearance = UiAppearance(),
) {
    internal val layoutKey = listOf(width, height, padding, gap, alignment, wrap, clip, maxWidth, maxHeight, border)
    init {
        require(padding.isFinite() && gap.isFinite() && padding >= 0 && gap >= 0)
        require(maxWidth == null || maxWidth.isFinite() && maxWidth >= 0)
        require(maxHeight == null || maxHeight.isFinite() && maxHeight >= 0)
    }
}

/** Platform implements metrics and primitives; containers and sizing remain shared engine behavior. */
interface UiBackend {
    /** Style-aware natural size; solid grids reserve a line around each cell, including the outside edges. */
    fun measureGrid(columns: Int, rows: Int, columnGap: Int, gridStyle: GridStyle): UiSize {
        if (gridStyle == GridStyle.Dotted) return measureGrid(columns, rows, columnGap)
        val cell = measureText("M", Double.MAX_VALUE, false)
        return UiSize(
            (columns.toDouble() * (columnGap.toDouble() + 2) + 1) * cell.width,
            (rows.toDouble() * 2 + 1) * cell.height
        )
    }

    /** Natural grid dimensions using this backend's character metrics; grids clip rather than wrap. */
    fun measureGrid(columns: Int, rows: Int, columnGap: Int): UiSize {
        val cell = measureText("M", Double.MAX_VALUE, false)
        return UiSize((columns.toDouble() * (columnGap.toDouble() + 1) - columnGap) * cell.width, rows * cell.height)
    }

    /** Paints grid content synchronously into the pending frame; retaining the live content is unsupported. */
    fun drawGrid(grid: GridContent, bounds: UiRect, clip: UiRect, appearance: UiAppearance) {
        error("This UI backend does not support grid drawing")
    }

    /** Natural horizontal divider size in this backend's logical units. */
    fun measureDivider(width: Double): UiSize = UiSize(width, measureText("M", Double.MAX_VALUE, false).height)

    /** Paints a horizontal divider into the pending frame. */
    fun drawDivider(bounds: UiRect, clip: UiRect, appearance: UiAppearance) {
        error("This UI backend does not support divider drawing")
    }

    /** Maps host geometry to the backend drawable area, such as reserving a terminal wrap column. */
    fun viewport(size: UiSize): UiSize = size

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
internal enum class UiKind { Row, Column, Box, Text, Button }

/** Portable root composition layers; overlay roots are drawn after ordinary content. */
enum class UiLayer { Content, Overlay }
