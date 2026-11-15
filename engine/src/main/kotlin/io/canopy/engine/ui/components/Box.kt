package io.canopy.engine.ui.components

import io.canopy.engine.ui.Place
import io.canopy.engine.ui.Size
import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiScope
import io.canopy.engine.ui.UiStyle
import io.canopy.engine.ui.withLayout

/**
 * Extensible container that overlays children in declaration order.
 * @param N Concrete node type exposed to its initialization block.
 * @param block Runs once after nodeInit, before entry callbacks; not replayed on reentry.
 */
open class Box<N : Box<N>>(
    style: UiStyle = UiStyle(width = UiLength.Fill, height = UiLength.Fill),
    name: String = "Box",
    block: N.() -> Unit = {},
    place: Place = style.place,
    size: Size? = null,
    content: UiScope.() -> Unit = {},
) : UiNode<N>(name, style.withLayout(place, size), block = {
    block()
    if (this.content == null) ui(content)
}) {
    internal override val kind: UiKind get() = UiKind.Box
}
