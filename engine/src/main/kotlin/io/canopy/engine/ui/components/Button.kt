package io.canopy.engine.ui.components

import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.ui.Place
import io.canopy.engine.ui.Size
import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiMouseFilter
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiStyle

/**
 * Extensible focusable action. Its label and eligibility may change without replacing its identity.
 * @param N Concrete node type exposed to its initialization block.
 * @param block Runs once after nodeInit, before entry callbacks; not replayed on reentry.
 */
open class Button<N : Button<N>>(
    text: String = "",
    name: String = "Button",
    place: Place = Place.Auto,
    size: Size = Size.Content,
    block: N.() -> Unit = {},
    onClick: () -> Unit = {},
) : UiNode<N>(name, UiStyle(width = size.width, height = size.height, place = place), block = block) {
    init {
        mouseFilter = UiMouseFilter.Stop
    }
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

    override fun onMouseInput(event: InputEvent) {
        if (event is MouseButtonEvent &&
            event.action == InputBind.LEFT_MOUSE.name &&
            event.state == InputState.JustPressed &&
            enabled
        ) {
            root.focus(this)
            activate()
            event.consume()
        }
    }

    /**
     * Invokes an enabled, visible, entered action synchronously without changing simulation pause state.
     * This button owns local node query reads and lifecycle resources during the action; nested actions restore
     * the previous owner on return or failure. Ownership does not extend to asynchronous work.
     */
    fun activate() {
        if (enabled && isInsideTree && isVisibleInTree) {
            untrack { NodeLifetime.withOwner(this) { action() } }
        }
    }
}
