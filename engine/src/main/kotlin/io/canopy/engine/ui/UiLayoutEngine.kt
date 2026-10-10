package io.canopy.engine.ui

import kotlin.math.max
import kotlin.math.min

internal object UiLayoutEngine {
    fun layout(root: UiRoot, backend: UiBackend, viewport: UiSize) {
        val elements = root.content?.elements().orEmpty()
        val available = UiSize(
            resolve(root.style.width, viewport.width, viewport.width, root.style.maxWidth),
            resolve(root.style.height, viewport.height, viewport.height, root.style.maxHeight)
        )
        val x = align(root.horizontalAlignment, viewport.width, available.width)
        val y = align(root.verticalAlignment, viewport.height, available.height)
        root.bounds = UiRect(x, y, available.width, available.height)
        val padding = root.style.padding
        val inner = UiRect(
            x + padding,
            y + padding,
            max(0.0, available.width - 2 * padding),
            max(0.0, available.height - 2 * padding)
        )
        elements.forEach { element ->
            val measured = measure(element, backend, UiSize(inner.width, inner.height))
            place(
                element,
                backend,
                UiRect(
                    inner.x,
                    inner.y,
                    resolve(element.style.width, inner.width, measured.width, element.style.maxWidth),
                    resolve(element.style.height, inner.height, measured.height, element.style.maxHeight)
                )
            )
        }
    }
    private fun measure(element: UiElement, backend: UiBackend, available: UiSize): UiSize {
        val style = element.style
        val children = element.content?.elements().orEmpty()
        val constrainedWidth = when (val width = style.width) {
            UiLength.Content, UiLength.Fill -> available.width
            is UiLength.Fixed -> width.value
            is UiLength.Fraction -> available.width * width.value
        }.coerceAtMost(style.maxWidth ?: Double.MAX_VALUE)
        val inner = UiSize(
            max(0.0, constrainedWidth - style.padding * 2),
            max(0.0, available.height - style.padding * 2)
        )
        var measures = children.map { measure(it, backend, inner) }
        if (element.kind == UiKind.Row) {
            val fills = children.count { it.style.width == UiLength.Fill }
            if (fills > 0) {
                val fixed = children.zip(measures).sumOf { (child, size) ->
                    if (child.style.width == UiLength.Fill) 0.0 else size.width
                }
                val allocations = allocateFills(
                    children,
                    true,
                    max(0.0, inner.width - fixed - max(0, children.size - 1) * style.gap)
                )
                measures = children.zip(measures).map { (child, size) ->
                    if (child.style.width == UiLength.Fill) {
                        measure(child, backend, UiSize(allocations.getValue(child), inner.height))
                    } else {
                        size
                    }
                }
            }
        }
        val key = listOf(backend, element.text, style, available, children.map { it.nodeId }, measures)
        if (key == element.measurementKey) return element.measured
        val intrinsic = when (element.kind) {
            UiKind.Text -> backend.measureText(element.text, inner.width, style.wrap)
            UiKind.Button -> backend.measureButton(element.text, inner.width, style.wrap)
            UiKind.Row -> UiSize(
                measures.sumOf { it.width } + max(0, children.size - 1) * style.gap,
                measures.maxOfOrNull { it.height } ?: 0.0
            )
            UiKind.Column -> UiSize(
                measures.maxOfOrNull { it.width } ?: 0.0,
                measures.sumOf { it.height } + max(0, children.size - 1) * style.gap
            )
            UiKind.Box -> UiSize(
                measures.maxOfOrNull { it.width } ?: 0.0,
                measures.maxOfOrNull { it.height } ?: 0.0
            )
        }
        val measured = UiSize(
            resolve(
                style.width,
                available.width,
                intrinsic.width + style.padding * 2,
                style.maxWidth
            ),
            resolve(
                style.height,
                available.height,
                intrinsic.height + style.padding * 2,
                style.maxHeight
            )
        )
        element.measurementKey = key
        element.measured = measured
        return measured
    }
    private fun place(element: UiElement, backend: UiBackend, bounds: UiRect) {
        element.bounds = bounds
        val style = element.style
        val inner = UiRect(
            bounds.x + style.padding,
            bounds.y + style.padding,
            max(0.0, bounds.width - style.padding * 2),
            max(0.0, bounds.height - style.padding * 2)
        )
        val children = element.content?.elements().orEmpty()
        if (children.isEmpty()) return
        val horizontal = element.kind == UiKind.Row
        val overlay = element.kind == UiKind.Box
        val sizes = children.map { measure(it, backend, UiSize(inner.width, inner.height)) }
        val availableMain = if (horizontal) inner.width else inner.height
        val fixedMain = children.zip(sizes).sumOf { (child, size) ->
            val policy = if (horizontal) child.style.width else child.style.height
            if (policy == UiLength.Fill) {
                0.0
            } else if (horizontal) {
                size.width
            } else {
                size.height
            }
        }
        val fillAllocations = allocateFills(
            children,
            horizontal,
            max(0.0, availableMain - fixedMain - max(0, children.size - 1) * style.gap)
        )
        var cursor = 0.0
        children.zip(sizes).forEach { (child, measured) ->
            val mainPolicy = if (horizontal) child.style.width else child.style.height
            val main = if (!overlay && mainPolicy == UiLength.Fill) {
                fillAllocations.getValue(child)
            } else if (horizontal) {
                measured.width
            } else {
                measured.height
            }
            val crossAvailable = if (horizontal) inner.height else inner.width
            val allocatedMeasure = if (horizontal && mainPolicy == UiLength.Fill) {
                measure(child, backend, UiSize(main, inner.height))
            } else {
                measured
            }
            val crossCap = if (horizontal) child.style.maxHeight else child.style.maxWidth
            val cross = if (style.alignment == UiAlignment.Stretch) {
                min(crossAvailable, crossCap ?: Double.MAX_VALUE)
            } else if (horizontal) {
                allocatedMeasure.height
            } else {
                measured.width
            }
            val offset = align(style.alignment, crossAvailable, cross)
            val childBounds = if (overlay) {
                UiRect(
                    inner.x + align(style.alignment, inner.width, measured.width),
                    inner.y + align(style.alignment, inner.height, measured.height),
                    measured.width,
                    measured.height
                )
            } else if (horizontal) {
                UiRect(inner.x + cursor, inner.y + offset, main, cross)
            } else {
                UiRect(inner.x + offset, inner.y + cursor, cross, main)
            }
            place(child, backend, childBounds)
            cursor += main + style.gap
        }
    }
    private fun allocateFills(
        children: List<UiElement>,
        horizontal: Boolean,
        available: Double,
    ): Map<UiElement, Double> {
        val allocations = mutableMapOf<UiElement, Double>()
        var remaining = available
        val unallocated = children.filter {
            (if (horizontal) it.style.width else it.style.height) == UiLength.Fill
        }.toMutableList()
        while (unallocated.isNotEmpty()) {
            val share = remaining / unallocated.size
            val capped = unallocated.filter {
                val cap = if (horizontal) it.style.maxWidth else it.style.maxHeight
                cap != null && cap < share
            }
            if (capped.isEmpty()) {
                unallocated.forEach { allocations[it] = share }
                break
            }
            capped.forEach {
                val allocation = (if (horizontal) it.style.maxWidth else it.style.maxHeight)!!
                allocations[it] = allocation
                remaining = max(0.0, remaining - allocation)
                unallocated.remove(it)
            }
        }
        return allocations
    }
    fun paint(element: UiElement, backend: UiBackend, clip: UiRect, focused: UiElement?) {
        if (!element.isVisibleInTree) return
        val ownClip = if (element.style.clip) clip.intersect(element.bounds) else clip
        when (element.kind) {
            UiKind.Text, UiKind.Button -> {
                val padding = element.style.padding
                val bounds = element.bounds
                val textBounds = UiRect(
                    bounds.x + padding,
                    bounds.y + padding,
                    max(0.0, bounds.width - 2 * padding),
                    max(0.0, bounds.height - 2 * padding)
                )
                if (element.kind == UiKind.Button) {
                    backend.drawButton(
                        element.text,
                        textBounds,
                        ownClip,
                        element === focused,
                        element.enabled,
                        element.style.wrap
                    )
                } else {
                    backend.drawText(element.text, textBounds, ownClip, element === focused, element.style.wrap)
                }
            }
            else -> element.content?.elements()?.forEach { paint(it, backend, ownClip, focused) }
        }
    }
    private fun resolve(policy: UiLength, available: Double, content: Double, cap: Double?): Double = min(
        cap ?: Double.MAX_VALUE,
        when (policy) {
            UiLength.Content -> content
            UiLength.Fill -> available
            is UiLength.Fixed -> policy.value
            is UiLength.Fraction -> available * policy.value
        }
    ).coerceAtLeast(0.0)
    private fun align(alignment: UiAlignment, available: Double, used: Double): Double = when (alignment) {
        UiAlignment.Start, UiAlignment.Stretch -> 0.0
        UiAlignment.Center -> max(0.0, (available - used) / 2)
        UiAlignment.End -> max(0.0, available - used)
    }
}
