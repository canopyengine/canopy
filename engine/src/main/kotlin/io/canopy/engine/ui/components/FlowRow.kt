package io.canopy.engine.ui.components

import io.canopy.engine.ui.*

/** Extensible wrapping row. Measurement and drawing use host backend metrics in logical units. */
open class FlowRow<N : FlowRow<N>>(
    style: UiStyle = UiStyle(width = UiLength.Fill),
    name: String = "FlowRow",
    block: N.() -> Unit = {},
    content: UiScope.() -> Unit = {},
) : UiNode<N>(name, style, block = {
    block()
    if (this.content == null) ui(content)
}) {
    internal override val kind: UiKind get() = UiKind.FlowRow
}
