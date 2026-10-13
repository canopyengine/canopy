package io.canopy.engine.ui

import kotlin.math.max
import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.input.events.InputEvent

/** Participation in front-to-back, clipped UI pointer routing. Keyboard focus is independent. */
enum class UiMouseFilter {
    /** Skip this element, allowing eligible children and elements behind it to receive the event. */
    Ignore,

    /** Receive the event, then continue unless the handler consumes it. */
    Pass,

    /** Receive and consume the event, even when the handler does nothing. */
    Stop,
}

/**
 * Base for built-in and custom UI components. Shares ordinary node identity, state and lifecycle.
 * Compose children once from [nodeInit] using [ui]; Signals provide live values inside that builder.
 * Custom components arrange children as an overlay by default and may also contain ordinary nodes.
 * A containing [UiRoot] supplies host geometry, presentation and focus.
 */
abstract class UiNode<N : UiNode<N>> protected constructor(
    name: String,
    style: UiStyle = UiStyle(),
    block: N.() -> Unit = {},
) : Node<N>(name, block = block) {
    internal open val kind: UiKind get() = UiKind.Box
    internal open val contentText: String get() = ""
    internal open val actionEnabled: Boolean get() = true
    internal val root: UiRoot
        get() {
            var node: Node<*>? = this
            while (node != null) {
                if (node is UiRoot) return node
                node = node.parent
            }
            error("UI components must be attached beneath a UiRoot before layout or composition")
        }

    private var storedStyle by nodeProperty(style)

    /** Sizing, arrangement and clipping in backend logical units. */
    var style: UiStyle
        get() = storedStyle
        set(value) {
            if (storedStyle != value) {
                storedStyle = value
                invalidate()
            }
        }

    /** Accessible semantic role; custom components default to a container. */
    open val role: String get() = "container"

    /** Accessible content label, empty for containers. */
    open val accessibleLabel: String get() = contentText

    /** Pointer participation, ignoring events by default. Buttons opt in with [UiMouseFilter.Stop]. */
    var mouseFilter by nodeProperty(UiMouseFilter.Ignore)

    /**
     * Handles hit-tested mouse button, movement or wheel events on the lifecycle thread, including paused frames.
     * Positions are in viewport units, like [bounds]. Opt in with [mouseFilter]; consume an event to stop propagation.
     * Hidden/detached nodes never receive this callback. The open command prompt captures events before UI routing.
     */
    protected open fun onMouseInput(event: InputEvent) {}

    internal fun dispatchMouseInput(event: InputEvent) {
        untrack { NodeLifetime.withOwner(this) { onMouseInput(event) } }
    }

    /** Most recent layout rectangle in viewport coordinates. */
    var bounds by nodeProperty(UiRect(0.0, 0.0, 0.0, 0.0))
        internal set
    internal var measurementKey by nodeProperty<List<Any?>>(emptyList())
    internal var measured by nodeProperty(UiSize(0.0, 0.0))
    internal var content by nodeProperty<UiScope?>(null)

    /** Nearest explicit theme applies to this subtree; null inherits from ancestors. */
    var theme by nodeProperty<UiTheme?>(null)
    internal val resolvedTheme: UiTheme
        get() {
            var node: Node<*>? = this
            while (node != null) {
                if (node is UiNode<*>) node.theme?.let { return it }
                node = node.parent
            }
            return UiTheme.Default
        }
    internal val resolvedBorder: UiBorder
        get() = style.border ?: if (kind == UiKind.Button) resolvedTheme.buttonBorder else UiBorder.None
    internal val contentInset: Double
        get() = style.padding + if (kind != UiKind.Button && resolvedBorder != UiBorder.None) 1.0 else 0.0
    internal val contentBounds: UiRect
        get() = UiRect(
            bounds.x + contentInset,
            bounds.y + contentInset,
            max(0.0, bounds.width - 2 * contentInset),
            max(0.0, bounds.height - 2 * contentInset)
        )

    internal fun appearance(focused: Boolean = false): UiAppearance {
        val theme = resolvedTheme
        var overrides = UiAppearance.Empty
        var node: Node<*>? = this
        while (node != null) {
            // Walk outward: nearer explicit properties override farther ancestors.
            if (node is UiNode<*>) overrides = node.style.appearance.merge(overrides)
            node = node.parent
        }
        val defaults = if (kind == UiKind.Button) theme.text.merge(theme.button) else theme.text
        val result = defaults.merge(overrides)
        return when {
            kind == UiKind.Button && !actionEnabled -> result.merge(theme.disabledButton)
            kind == UiKind.Button && focused -> result.merge(theme.focusedButton)
            else -> result
        }
    }

    override fun nodeInit() {
        markChildrenManaged()
    }

    /** Builds the component's retained children once during initialization. */
    protected fun ui(block: UiScope.() -> Unit) {
        check(state("compose component").initializing) { "Compose UI children from nodeInit, once per component" }
        check(content == null) { "A UI component has one composition builder" }
        markChildrenManaged()
        val scope = UiScope(root, this)
        content = scope
        onDestroy { scope.dispose() }
        untrack { scope.compose(block) }
    }

    internal fun invalidate() {
        var node: Node<*>? = this
        while (node != null) {
            if (node is UiRoot) {
                node.dirty = true
                return
            }
            node = node.parent
        }
    }
}
