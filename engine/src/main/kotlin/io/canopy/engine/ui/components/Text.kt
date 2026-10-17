package io.canopy.engine.ui.components

import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiNode

/**
 * Extensible text content measured and clipped using the current host's metrics.
 * @param N Concrete node type exposed to its initialization block.
 * @param block Runs once after nodeInit, before entry callbacks; not replayed on reentry.
 */
open class Text<N : Text<N>>(text: String = "", name: String = "Text", block: N.() -> Unit = {}) :
    UiNode<N>(name, block = block) {
    internal override val kind: UiKind get() = UiKind.Text
    internal override val contentText: String get() = text
    override val role: String get() = "text"
    private var storedText by nodeProperty(text)

    /** Current content; equal assignments preserve layout and presentation caches. */
    var text: String
        get() = storedText
        set(value) {
            if (storedText != value) {
                storedText = value
                invalidate()
            }
        }
}
