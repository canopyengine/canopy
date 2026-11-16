package io.canopy.engine.ui

import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.math.Vector2

internal object UiTreeInput {
    fun descendants(element: UiNode<*>): List<UiNode<*>> =
        listOf(element) + element.content?.elements().orEmpty().flatMap(::descendants)

    fun mouseInput(root: UiNode<*>, viewport: UiRect, event: InputEvent, position: Vector2): Boolean {
        if (!root.isValid || !root.isInsideTree || !root.isVisibleInTree || !isPresentationRoot(root)) return false
        val candidates = descendants(root)
        for (node in candidates.asReversed()) {
            if (!root.isValid || !root.isInsideTree || !isPresentationRoot(root)) return false
            if (!node.isValid || !node.isInsideTree || !node.isVisibleInTree || !belongsTo(node, root)) continue
            val filter = node.mouseFilter
            if (filter == UiMouseFilter.Ignore || !hit(node, root, viewport, position)) continue
            node.dispatchMouseInput(event)
            if (event.isHandled || filter == UiMouseFilter.Stop) {
                event.consume()
                return true
            }
        }
        return false
    }

    fun pointerTargets(root: UiNode<*>, viewport: UiRect, position: Vector2): List<UiNode<*>> =
        if (!root.isValid || !root.isInsideTree || !root.isVisibleInTree || !isPresentationRoot(root)) {
            emptyList()
        } else {
            descendants(root).asReversed().filter {
                it.isValid &&
                    it.isInsideTree &&
                    it.isVisibleInTree &&
                    belongsTo(it, root) &&
                    it.mouseFilter != UiMouseFilter.Ignore &&
                    hit(it, root, viewport, position)
            }
        }

    fun hasPointerTargets(root: UiNode<*>): Boolean {
        if (!root.isValid || !root.isInsideTree || !root.isVisibleInTree || !isPresentationRoot(root)) return false
        return descendants(root).any {
            it.isValid &&
                it.isInsideTree &&
                it.isVisibleInTree &&
                belongsTo(it, root) &&
                it.mouseFilter != UiMouseFilter.Ignore
        }
    }

    private fun belongsTo(node: UiNode<*>, root: UiNode<*>): Boolean {
        var ancestor: Node<*>? = node
        while (ancestor != null) {
            if (ancestor === root) return true
            ancestor = ancestor.parent
        }
        return false
    }

    fun isPresentationRoot(root: UiNode<*>): Boolean {
        var ancestor = root.parent
        while (ancestor != null) {
            if (ancestor is UiNode<*>) return false
            ancestor = ancestor.parent
        }
        return true
    }

    private fun hit(element: UiNode<*>, root: UiNode<*>, viewport: UiRect, position: Vector2): Boolean {
        var clip = element.bounds.intersect(viewport)
        element.layoutClip?.let { clip = clip.intersect(it) }
        var ancestor = if (element === root) null else element.parent
        while (ancestor != null) {
            (ancestor as? UiNode<*>)?.layoutClip?.let { clip = clip.intersect(it) }
            if (ancestor is UiNode<*> && ancestor.style.clip) {
                clip = clip.intersect(
                    if (ancestor.resolvedBorder == UiBorder.None) ancestor.bounds else ancestor.contentBounds
                )
            }
            if (ancestor === root) break
            ancestor = ancestor.parent
        }
        return position.x.toDouble() >= clip.x &&
            position.x.toDouble() < clip.x + clip.width &&
            position.y.toDouble() >= clip.y &&
            position.y.toDouble() < clip.y + clip.height
    }
}
