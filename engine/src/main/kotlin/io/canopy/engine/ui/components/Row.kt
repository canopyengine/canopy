package io.canopy.engine.ui.components

import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiScope
import io.canopy.engine.ui.UiStyle

/** Arranges children horizontally. */
class Row(style: UiStyle = UiStyle(width = UiLength.Fill), name: String = "Row", block: UiScope.() -> Unit = {}) :
    UiNode<Row>(name, style, block = { if (content == null) ui(block) }) {
    internal override val kind: UiKind get() = UiKind.Row
}
