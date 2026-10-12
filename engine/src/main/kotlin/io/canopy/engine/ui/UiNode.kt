package io.canopy.engine.ui

import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.nodes.Node

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

    /** Most recent layout rectangle in viewport coordinates. */
    var bounds by nodeProperty(UiRect(0.0, 0.0, 0.0, 0.0))
        internal set
    internal var measurementKey by nodeProperty<List<Any?>>(emptyList())
    internal var measured by nodeProperty(UiSize(0.0, 0.0))
    internal var content by nodeProperty<UiScope?>(null)

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
