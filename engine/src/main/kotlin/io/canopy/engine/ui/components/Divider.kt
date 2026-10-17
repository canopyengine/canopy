package io.canopy.engine.ui.components

import io.canopy.engine.ui.*

/**
 * Extensible horizontal separator that fills its allocated width. Style controls color, spacing and clipping.
 * @param N Concrete node type exposed to its initialization block.
 * @param block Runs once after nodeInit, before entry callbacks; not replayed on reentry.
 */
open class Divider<N : Divider<N>>(
    style: UiStyle = UiStyle(width = UiLength.Fill),
    name: String = "Divider",
    block: N.() -> Unit = {},
) : UiNode<N>(name, style, block = block) {
    override val role: String get() = "separator"
    internal override fun measureContent(backend: UiBackend, available: UiSize) =
        backend.measureDivider(available.width)
    internal override fun paintContent(backend: UiBackend, clip: UiRect, appearance: UiAppearance) =
        backend.drawDivider(contentBounds, clip.intersect(contentBounds), appearance)
}
