package io.canopy.engine.ui.components

import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiNode

/** Text content measured and clipped using the current host's metrics. */
class Text(text: String = "", name: String = "Text") : UiNode<Text>(name) {
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
