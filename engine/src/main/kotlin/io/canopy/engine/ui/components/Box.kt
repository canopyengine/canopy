package io.canopy.engine.ui.components

import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiScope
import io.canopy.engine.ui.UiStyle

/** Overlays children in declaration order. */
class Box(
    style: UiStyle = UiStyle(width = UiLength.Fill, height = UiLength.Fill),
    name: String = "Box",
    block: UiScope.() -> Unit = {},
) : UiNode<Box>(name, style, block = { if (content == null) ui(block) }) {
    internal override val kind: UiKind get() = UiKind.Box
}
