package io.canopy.engine.ui.components

import io.canopy.engine.core.flows.events.Input
import io.canopy.engine.ui.Place
import io.canopy.engine.ui.Size
import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiStyle

/**
 * Extensible text content measured and clipped using the current host's metrics.
 * @param N Concrete node type exposed to its initialization block.
 * @param name Optional explicit name; otherwise the concrete class and its sibling count determine the name.
 * @param label Read-only constructor input; reactive expressions update the label without replacing this node.
 * The named constructor argument is `label` rather than the former `text`.
 * Subclasses may delegate literal or omitted labels to this constructor, but dynamic superclass labels are unsupported.
 * @param block Runs once after onSetup, before entry callbacks; not replayed on reentry.
 */
open class Text<N : Text<N>>(
    @Input val label: String = "",
    name: String? = null,
    place: Place = Place.Auto,
    size: Size = Size.Content,
    block: N.() -> Unit = {},
) : UiNode<N>(name, UiStyle(width = size.width, height = size.height, place = place), block = block) {
    internal override val kind: UiKind get() = UiKind.Text
    internal override val contentText: String get() = text
    override val role: String get() = "text"
    private var textOverride by nodeProperty<String?>(null)

    /** Displayed content; assignment overrides future [label] updates while retaining the mutable legacy API. */
    var text: String
        get() = textOverride ?: label
        set(value) {
            if (textOverride != value) {
                textOverride = value
            }
        }
}
