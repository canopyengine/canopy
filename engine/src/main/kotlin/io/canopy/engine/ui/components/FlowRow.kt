package io.canopy.engine.ui.components

import io.canopy.engine.ui.*

/**
 * Extensible wrapping row. Measurement and drawing use host backend metrics in logical units.
 * @param name Optional explicit name; otherwise the concrete class and its sibling count determine the name.
 */
open class FlowRow<N : FlowRow<N>>(
    style: UiStyle = UiStyle(width = UiLength.Fill),
    name: String? = null,
    block: N.() -> Unit = {},
    content: UiScope.() -> Unit = {},
) : UiNode<N>(name, style, block = {
    block()
    if (this.content == null) ui(content)
}) {
    internal override val kind: UiKind get() = UiKind.FlowRow
}
