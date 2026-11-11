package io.canopy.engine.graphics

import kotlin.math.max
import io.canopy.engine.ui.UiAppearance
import io.canopy.engine.ui.UiBackend
import io.canopy.engine.ui.UiRect
import io.canopy.engine.ui.UiSize

/**
 * Drawing area supplied to a node's drawing callback. Coordinates are local logical units:
 * terminal cells or backend pixels. The host applies the inherited origin and clip automatically.
 * Draws populate the pending frame; they do not present output. Use only during the callback,
 * on the engine thread. Drawing does not apply Node2D transforms or affect layout measurements.
 */
class DrawContext internal constructor(
    private val backend: UiBackend,
    private val area: UiRect,
    private val inheritedClip: UiRect,
    private val inheritedAppearance: UiAppearance = UiAppearance(),
) {
    private var active = true

    /** Available content size, excluding the containing UI node's padding. */
    val size: UiSize = UiSize(area.width, area.height)

    /** Measures text using the current host's metrics without submitting output. */
    fun measureText(text: String, maxWidth: Double = size.width, wrap: Boolean = true): UiSize {
        checkActive()
        require(maxWidth.isFinite() && maxWidth >= 0)
        return backend.measureText(text, maxWidth, wrap)
    }

    /** Draws text at a local position, clipped to the inherited area and allocated text bounds. */
    fun text(
        text: String,
        x: Double = 0.0,
        y: Double = 0.0,
        width: Double = max(0.0, size.width - x),
        height: Double = max(0.0, size.height - y),
        wrap: Boolean = true,
        appearance: UiAppearance = UiAppearance(),
    ) {
        checkActive()
        val bounds = UiRect(area.x + x, area.y + y, width, height)
        backend.drawText(
            text,
            bounds,
            inheritedClip.intersect(bounds),
            focused = false,
            wrap = wrap,
            appearance = inheritedAppearance.merge(appearance)
        )
    }

    private fun checkActive() = check(active) { "DrawContext is only valid during its drawing callback" }

    internal fun fork() = DrawContext(backend, area, inheritedClip, inheritedAppearance)

    internal fun close() {
        active = false
    }
}
