package io.canopy.engine.ui.components

import io.canopy.engine.core.flows.events.Input
import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.ui.Place
import io.canopy.engine.ui.Size
import io.canopy.engine.ui.UiKind
import io.canopy.engine.ui.UiManager
import io.canopy.engine.ui.UiMouseFilter
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiStyle

/**
 * Extensible focusable action. Its label and eligibility may change without replacing its identity.
 * @param N Concrete node type exposed to its initialization block.
 * @param name Optional explicit name; otherwise the concrete class and its sibling count determine the name.
 * @param label Read-only constructor input; reactive expressions update the label without replacing this node.
 * The named constructor argument is `label` rather than the former `text`.
 * Subclasses may delegate literal or omitted labels to this constructor, but dynamic superclass labels are unsupported.
 * @param block Runs once after onSetup, before entry callbacks; not replayed on reentry.
 */
open class Button<N : Button<N>>(
    @Input val label: String = "",
    name: String? = null,
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
    private var textOverride by nodeProperty<String?>(null)

    /** Displayed action label; assignment overrides future [label] updates while retaining the mutable legacy API. */
    var text: String
        get() = textOverride ?: label
        set(value) {
            if (textOverride != value) {
                textOverride = value
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
            getManager<UiManager>().focus(this)
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
