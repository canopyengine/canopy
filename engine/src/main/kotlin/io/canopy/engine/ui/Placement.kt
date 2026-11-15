package io.canopy.engine.ui

/** Parent-relative attachment point. Named presets also select the matching point on the child. */
data class Anchor(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
    }
    companion object {
        val TopLeft = Anchor(0.0, 0.0)
        val TopCenter = Anchor(0.5, 0.0)
        val TopRight = Anchor(1.0, 0.0)
        val CenterLeft = Anchor(0.0, 0.5)
        val Center = Anchor(0.5, 0.5)
        val CenterRight = Anchor(1.0, 0.5)
        val BottomLeft = Anchor(0.0, 1.0)
        val BottomCenter = Anchor(0.5, 1.0)
        val BottomRight = Anchor(1.0, 1.0)
    }
}

/** Nonnegative content insets in backend logical units: cells on terminals, pixels on graphical hosts. */
data class Insets(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    constructor(all: Double = 0.0) : this(all, all, all, all)
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it >= 0.0 })
    }
}

/** Finite placement displacement in the parent's logical coordinate system. */
data class Offset(val x: Double = 0.0, val y: Double = 0.0) {
    init {
        require(x.isFinite() && y.isFinite())
    }
}

/** Cross-axis placement for children of a flow container; distinct from parent-relative anchors. */
enum class CrossAlign { Start, Center, End, Stretch }

/** Main-axis distribution of unused space, after child allocation and minimum gaps. */
enum class Arrange { Start, Center, End, SpaceBetween, SpaceAround, SpaceEvenly }

/** Exactly one placement policy per rectangular component. Container children cannot use [At] or [Stretch]. */
sealed interface Place {
    /** Uses the parent's placement defaults. */
    data object Auto : Place

    /** Attaches [origin] on this child to [anchor] in the parent's inset content area. */
    data class At(
        val anchor: Anchor,
        val inset: Insets = Insets(),
        val offset: Offset = Offset(),
        val origin: Anchor = anchor,
    ) : Place

    /** Takes part in a row, column, grid or wrapping flow, with an optional cross-axis override. */
    data class InFlow(val align: CrossAlign? = null) : Place

    /** Fills the parent's content area minus [inset], subject to the child's maximum dimensions. */
    data class Stretch(val inset: Insets = Insets()) : Place
}

/** Independent sizing preferences. Fill siblings divide remaining space; Weight permits unequal shares. */
data class Size(val width: UiLength = UiLength.Content, val height: UiLength = UiLength.Content) {
    companion object {
        val Content = Size()
        val Fill = Size(UiLength.Fill, UiLength.Fill)
        val FillWidth = Size(width = UiLength.Fill)
        val FillHeight = Size(height = UiLength.Fill)
    }
}

/** Canvas dimensions. Fixed dimensions are logical units, never an implicit reference-resolution scale. */
sealed interface CanvasSize {
    data object Viewport : CanvasSize
    data class Fixed(val width: Double, val height: Double) : CanvasSize {
        init {
            UiSize(width, height)
        }
    }
}

internal fun UiStyle.withLayout(
    place: Place = this.place,
    size: Size? = null,
    arrange: Arrange = arrangement,
    align: CrossAlign? = null,
    gap: Double = this.gap,
): UiStyle = copy(
    place = place,
    width = size?.width ?: width,
    height = size?.height ?: height,
    arrangement = arrange,
    alignment = align ?: alignment,
    gap = gap
)
