package io.canopy.engine.ui.components

import io.canopy.engine.ui.*

/** Horizontal separator that fills its allocated width. Style controls color, spacing and clipping. */
class Divider(style: UiStyle = UiStyle(width = UiLength.Fill), name: String = "Divider") :
    UiNode<Divider>(name, style) {
    override val role: String get() = "separator"
    internal override fun measureContent(backend: UiBackend, available: UiSize) =
        backend.measureDivider(available.width)
    internal override fun paintContent(backend: UiBackend, clip: UiRect, appearance: UiAppearance) =
        backend.drawDivider(contentBounds, clip.intersect(contentBounds), appearance)
}
