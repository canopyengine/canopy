package io.canopy.engine.ui.components

import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiScope
import io.canopy.engine.ui.UiStyle

/** Arranges children vertically. */
class Column(
    style: UiStyle = UiStyle(width = UiLength.Fill),
    name: String = "Column",
    block: UiScope.() -> Unit = {},
) : UiNode<Column>(name, style, block = { if (content == null) ui(block) }) {
    internal override val kind: UiKind get() = UiKind.Column
}
