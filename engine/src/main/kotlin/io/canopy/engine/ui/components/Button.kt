package io.canopy.engine.ui.components

import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiNode

/** Focusable action. Its label and eligibility may change without replacing its identity. */
class Button(text: String = "", name: String = "Button", onClick: () -> Unit = {}) : UiNode<Button>(name) {
    internal override val kind: UiKind get() = UiKind.Button
    internal override val contentText: String get() = text
    internal override val actionEnabled: Boolean get() = enabled
    override val role: String get() = "button"
    private var storedText by nodeProperty(text)

    /** Current action label. */
    var text: String
        get() = storedText
        set(value) {
            if (storedText != value) {
                storedText = value
                invalidate()
            }
        }

    /** Disabled actions keep their layout but cannot receive focus or activate. */
    var enabled by nodeProperty(true)
    internal var action by nodeProperty(onClick)

    /** Invokes an enabled, visible, entered action without changing simulation pause state. */
    fun activate() {
        if (enabled && isInsideTree && isVisibleInTree) {
            untrack { NodeLifetime.withOwner(this) { action() } }
        }
    }
}
