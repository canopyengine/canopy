package io.canopy.engine.ui

import kotlin.math.max
import kotlin.math.min
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.ui.components.Grid
import io.canopy.engine.ui.components.GridCell

internal object UiLayoutEngine {
    fun layoutViewport(element: UiNode<*>, backend: UiBackend, viewport: UiSize) {
        val area = UiRect(0.0, 0.0, viewport.width, viewport.height)
        val measured = measure(element, backend, viewport)
        place(
            element,
            backend,
            anchored(
                element.style.place,
                area,
                measured,
                maxWidth = element.style.maxWidth,
                maxHeight = element.style.maxHeight
            )
        )
    }

    fun layout(root: UiRoot<*>, backend: UiBackend, viewport: UiSize) {
        val elements = root.content?.elements().orEmpty()
        val available = UiSize(
            resolve(root.style.width, viewport.width, viewport.width, root.style.maxWidth),
            resolve(root.style.height, viewport.height, viewport.height, root.style.maxHeight)
        )
        val rootArea = backend.layoutBounds(
            anchored(
                root.style.place,
                UiRect(0.0, 0.0, viewport.width, viewport.height),
                available,
                root.horizontalAlignment,
                root.verticalAlignment,
                root.style.maxWidth,
                root.style.maxHeight
            )
        )
        val x = rootArea.x
        val y = rootArea.y
        root.bounds = rootArea
        val padding = root.contentInset
        val inner = UiRect(
            x + padding,
            y + padding,
            max(0.0, rootArea.width - 2 * padding),
            max(0.0, rootArea.height - 2 * padding)
        )
        elements.forEach { element ->
            val measured = measure(element, backend, UiSize(inner.width, inner.height))
            place(
                element,
                backend,
                anchored(
                    element.style.place,
                    inner,
                    measured,
                    maxWidth = element.style.maxWidth,
                    maxHeight = element.style.maxHeight
                )
            )
        }
    }
    private fun measure(element: UiNode<*>, backend: UiBackend, available: UiSize): UiSize {
        val style = element.style
        if (element is Grid<*>) {
            val width = resolve(style.width, available.width, available.width, style.maxWidth)
            val geometry = gridGeometry(
                element,
                backend,
                UiSize(
                    max(0.0, width - element.contentInset * 2),
                    max(0.0, available.height - element.contentInset * 2)
                )
            )
            element.geometry = geometry
            return UiSize(
                resolve(style.width, available.width, geometry.size.width + element.contentInset * 2, style.maxWidth),
                resolve(
                    style.height,
                    available.height,
                    geometry.size.height + element.contentInset * 2,
                    style.maxHeight
                )
            )
        }
        val children = element.content?.elements().orEmpty()
        val constrainedWidth = when (val width = style.width) {
            UiLength.Content, UiLength.Fill, is UiLength.Weight -> available.width
            is UiLength.Fixed -> width.value
            is UiLength.Fraction -> available.width * width.value
        }.coerceAtMost(style.maxWidth ?: Double.MAX_VALUE)
        val inner = UiSize(
            max(0.0, constrainedWidth - element.contentInset * 2),
            max(0.0, available.height - element.contentInset * 2)
        )
        var measures = children.map { measure(it, backend, inner) }
        if (element.kind == UiKind.Row) {
            val fills = children.count { weight(it.style.width) > 0.0 }
            if (fills > 0) {
                val fixed = children.zip(measures).sumOf { (child, size) ->
                    if (weight(child.style.width) > 0.0) 0.0 else size.width
                }
                val allocations = allocateFills(
                    children,
                    true,
                    max(0.0, inner.width - fixed - max(0, children.size - 1) * style.gap)
                )
                measures = children.zip(measures).map { (child, size) ->
                    if (weight(child.style.width) > 0.0) {
                        measure(child, backend, UiSize(allocations.getValue(child), inner.height))
                    } else {
                        size
                    }
                }
            }
        }
        // Text metrics depend on constrained width, not the height left by a layout pass.
        // Fill/fraction heights still depend on allocation; containers also retain child measurements.
        val allocatedHeight = when (style.height) {
            UiLength.Content, is UiLength.Fixed -> null
            UiLength.Fill, is UiLength.Weight, is UiLength.Fraction -> available.height
        }
        val key = listOf(
            backend,
            element.contentText,
            element.contentMeasureKey,
            style.layoutKey,
            element.resolvedBorder,
            constrainedWidth,
            allocatedHeight,
            children.map { it.nodeId },
            measures
        )
        if (key == element.measurementKey) return element.measured
        val intrinsic = element.measureContent(backend, inner) ?: when (element.kind) {
            UiKind.Text -> backend.measureText(element.contentText, inner.width, style.wrap)
            UiKind.Button -> backend.measureButton(element.contentText, inner.width, style.wrap, element.resolvedBorder)
            UiKind.Row -> UiSize(
                measures.sumOf { it.width } + max(0, children.size - 1) * style.gap,
                measures.maxOfOrNull { it.height } ?: 0.0
            )
            UiKind.Column -> UiSize(
                measures.maxOfOrNull { it.width } ?: 0.0,
                measures.sumOf { it.height } + max(0, children.size - 1) * style.gap
            )
            UiKind.Grid -> error("Grid nodes must use shared cell geometry")
            UiKind.FlowRow -> {
                val packed = packed(element, inner, measures)
                UiSize(
                    packed.maxOfOrNull { it.x + it.width } ?: 0.0,
                    packed.maxOfOrNull { it.y + it.height } ?: 0.0
                )
            }
            UiKind.Box -> UiSize(
                measures.maxOfOrNull { it.width } ?: 0.0,
                measures.maxOfOrNull { it.height } ?: 0.0
            )
        }
        val measured = UiSize(
            resolve(
                style.width,
                available.width,
                intrinsic.width + element.contentInset * 2,
                style.maxWidth
            ),
            resolve(
                style.height,
                available.height,
                intrinsic.height + element.contentInset * 2,
                style.maxHeight
            )
        )
        element.measurementKey = key
        element.measured = measured
        return measured
    }
    private fun place(element: UiNode<*>, backend: UiBackend, requestedBounds: UiRect) {
        val bounds = backend.layoutBounds(requestedBounds)
        element.bounds = bounds
        element.layoutClip = null
        val style = element.style
        val inner = UiRect(
            bounds.x + element.contentInset,
            bounds.y + element.contentInset,
            max(0.0, bounds.width - element.contentInset * 2),
            max(0.0, bounds.height - element.contentInset * 2)
        )
        val children = element.content?.elements().orEmpty()
        if (element is Grid<*>) {
            val geometry = gridGeometry(element, backend, UiSize(inner.width, inner.height))
            element.geometry = geometry
            val positions = element.positions(children)
            children.zip(positions).forEach { (child, position) ->
                requireFlow(child)
                val area = geometry.content(position.column, position.row).let {
                    backend.layoutBounds(it.copy(x = inner.x + it.x, y = inner.y + it.y))
                }
                val measured = measure(child, backend, UiSize(area.width, area.height))
                val alignment = (child.place as? Place.InFlow)?.align
                    ?: style.alignment
                val height = if (child is GridCell || alignment == CrossAlign.Stretch) {
                    area.height
                } else {
                    min(area.height, measured.height)
                }
                place(
                    child,
                    backend,
                    UiRect(
                        area.x,
                        area.y + align(alignment, area.height, height),
                        min(area.width, child.style.maxWidth ?: area.width),
                        height
                    )
                )
                child.layoutClip = area
            }
            return
        }
        if (children.isEmpty()) return
        if (element.kind == UiKind.FlowRow) {
            val sizes = children.map { measure(it, backend, UiSize(inner.width, inner.height)) }
            children.zip(packed(element, UiSize(inner.width, inner.height), sizes)).forEach { (child, rect) ->
                requireFlow(child)
                place(child, backend, rect.copy(x = inner.x + rect.x, y = inner.y + rect.y))
            }
            return
        }
        val horizontal = element.kind == UiKind.Row
        val overlay = element.kind == UiKind.Box
        val sizes = children.map { measure(it, backend, UiSize(inner.width, inner.height)) }
        val availableMain = if (horizontal) inner.width else inner.height
        val fixedMain = children.zip(sizes).sumOf { (child, size) ->
            val policy = if (horizontal) child.style.width else child.style.height
            if (weight(policy) > 0.0) {
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
        val used = fixedMain + fillAllocations.values.sum() + max(0, children.size - 1) * style.gap
        val (initial, extraGap) = distribution(style.arrangement, max(0.0, availableMain - used), children.size)
        var cursor = initial
        children.zip(sizes).forEach { (child, measured) ->
            if (!overlay) requireFlow(child)
            val mainPolicy = if (horizontal) child.style.width else child.style.height
            val main = if (!overlay && weight(mainPolicy) > 0.0) {
                fillAllocations.getValue(child)
            } else if (horizontal) {
                measured.width
            } else {
                measured.height
            }
            val crossAvailable = if (horizontal) inner.height else inner.width
            val allocatedMeasure = if (horizontal && weight(mainPolicy) > 0.0) {
                measure(child, backend, UiSize(main, inner.height))
            } else {
                measured
            }
            val crossCap = if (horizontal) child.style.maxHeight else child.style.maxWidth
            val alignment = (child.style.place as? Place.InFlow)?.align
                ?: style.alignment
            val cross = if (alignment == CrossAlign.Stretch) {
                min(crossAvailable, crossCap ?: Double.MAX_VALUE)
            } else if (horizontal) {
                allocatedMeasure.height
            } else {
                measured.width
            }
            val offset = align(alignment, crossAvailable, cross)
            val childBounds = if (overlay) {
                anchored(
                    child.style.place,
                    inner,
                    measured,
                    style.alignment,
                    style.alignment,
                    child.style.maxWidth,
                    child.style.maxHeight
                )
            } else if (horizontal) {
                UiRect(inner.x + cursor, inner.y + offset, main, cross)
            } else {
                UiRect(inner.x + offset, inner.y + cursor, cross, main)
            }
            place(child, backend, childBounds)
            cursor += main + style.gap + extraGap
        }
    }
    private fun allocateFills(
        children: List<UiNode<*>>,
        horizontal: Boolean,
        available: Double,
    ): Map<UiNode<*>, Double> {
        val allocations = mutableMapOf<UiNode<*>, Double>()
        var remaining = available
        val unallocated = children.filter {
            weight(if (horizontal) it.style.width else it.style.height) > 0.0
        }.toMutableList()
        while (unallocated.isNotEmpty()) {
            val largest = unallocated.maxOf { weight(if (horizontal) it.style.width else it.style.height) }
            val total = unallocated.sumOf { weight(if (horizontal) it.style.width else it.style.height) / largest }
            fun share(child: UiNode<*>) = remaining *
                (weight(if (horizontal) child.style.width else child.style.height) / largest / total)
            val capped = unallocated.filter {
                val cap = if (horizontal) it.style.maxWidth else it.style.maxHeight
                cap != null && cap < share(it)
            }
            if (capped.isEmpty()) {
                unallocated.forEach { allocations[it] = share(it) }
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

    private fun weight(length: UiLength): Double = when (length) {
        UiLength.Fill -> 1.0
        is UiLength.Weight -> length.value
        else -> 0.0
    }

    private fun gridGeometry(grid: Grid<*>, backend: UiBackend, available: UiSize): GridGeometry {
        val children = grid.content?.elements().orEmpty()
        val positions = grid.positions(children)
        val rows = grid.rows
        val metrics = backend.measureGridLines(grid.lines)
        val padding = grid.cellPadding
        val style = grid.style
        val usableWidth = max(
            0.0,
            available.width - 2 * metrics.outer -
                (grid.columns - 1) * (style.gap + metrics.vertical)
        ) / grid.columns
        val glyph = if (grid.hasGlyphMeasurements) {
            backend.measureText(
                "M",
                Double.MAX_VALUE,
                false
            )
        } else {
            UiSize(0.0, 0.0)
        }
        val widthPolicy = grid.cellSize?.width
        val widthConstraint = if (widthPolicy != null && widthPolicy != UiLength.Content) {
            resolve(widthPolicy, max(0.0, usableWidth - padding * 2), glyph.width, null)
        } else {
            max(0.0, usableWidth - padding * 2)
        }
        val measured = children.map { measure(it, backend, UiSize(widthConstraint, available.height)) }
        val naturalWidth = max(glyph.width, measured.maxOfOrNull { it.width } ?: 0.0)
        val width = when {
            widthPolicy != null -> resolve(widthPolicy, max(0.0, usableWidth - padding * 2), naturalWidth, null)
            style.width == UiLength.Content -> naturalWidth
            else -> max(0.0, usableWidth - padding * 2)
        }
        val remeasured = if (width != widthConstraint) {
            children.map { measure(it, backend, UiSize(width, available.height)) }
        } else {
            measured
        }
        val heightPolicy = grid.cellSize?.height ?: UiLength.Content
        val defaultHeight = resolve(heightPolicy, available.height, glyph.height, null) + padding * 2
        val heights = mutableMapOf<Int, Double>()
        positions.zip(remeasured).forEach { (position, size) ->
            val height = resolve(heightPolicy, available.height, max(glyph.height, size.height), null) + padding * 2
            heights[position.row] = max(heights[position.row] ?: defaultHeight, height)
        }
        return GridGeometry(
            grid.columns, rows, width + padding * 2, defaultHeight, heights,
            style.gap, style.lineGap, padding, metrics
        )
    }

    private fun requireFlow(child: UiNode<*>) {
        require(child.style.place == Place.Auto || child.style.place is Place.InFlow) {
            "${child.name}: anchored placement requires a Canvas or Box parent; use Place.InFlow in containers"
        }
    }

    private fun anchored(
        placement: Place,
        area: UiRect,
        size: UiSize,
        horizontal: CrossAlign = CrossAlign.Start,
        vertical: CrossAlign = CrossAlign.Start,
        maxWidth: Double? = null,
        maxHeight: Double? = null,
    ): UiRect = when (placement) {
        Place.Auto -> UiRect(
            area.x + align(horizontal, area.width, size.width),
            area.y + align(vertical, area.height, size.height),
            size.width,
            size.height
        )
        is Place.InFlow -> error("Place.InFlow requires a Row, Column, FlowRow or Grid parent")
        is Place.Stretch -> UiRect(
            area.x + placement.inset.left,
            area.y + placement.inset.top,
            min(maxWidth ?: Double.MAX_VALUE, max(0.0, area.width - placement.inset.left - placement.inset.right)),
            min(maxHeight ?: Double.MAX_VALUE, max(0.0, area.height - placement.inset.top - placement.inset.bottom))
        )
        is Place.At -> {
            val inset = placement.inset
            UiRect(
                area.x + inset.left + max(0.0, area.width - inset.left - inset.right) * placement.anchor.x -
                    size.width * placement.origin.x + placement.offset.x,
                area.y + inset.top + max(0.0, area.height - inset.top - inset.bottom) * placement.anchor.y -
                    size.height * placement.origin.y + placement.offset.y,
                size.width,
                size.height
            )
        }
    }

    private fun distribution(arrange: Arrange, free: Double, count: Int): Pair<Double, Double> {
        if (count == 0) return 0.0 to 0.0
        return when (arrange) {
            Arrange.Start -> 0.0 to 0.0
            Arrange.Center -> free / 2 to 0.0
            Arrange.End -> free to 0.0
            Arrange.SpaceBetween -> 0.0 to if (count > 1) free / (count - 1) else 0.0
            Arrange.SpaceAround -> free / (2 * count) to free / count
            Arrange.SpaceEvenly -> free / (count + 1) to free / (count + 1)
        }
    }

    private fun packed(element: UiNode<*>, area: UiSize, sizes: List<UiSize>): List<UiRect> {
        val style = element.style
        val children = element.content?.elements().orEmpty()
        val lines = mutableListOf<MutableList<Int>>()
        var width = 0.0
        sizes.forEachIndexed { index, size ->
            val last = lines.lastOrNull()
            val newLine = last == null || (last.isNotEmpty() && width + style.gap + size.width > area.width)
            if (newLine) {
                lines += mutableListOf(index)
                width = size.width
            } else {
                last!!.add(index)
                width += style.gap + size.width
            }
        }
        val result = MutableList(sizes.size) { UiRect(0.0, 0.0, 0.0, 0.0) }
        var y = 0.0
        lines.forEach { line ->
            val height = line.maxOf { sizes[it].height }
            val used = line.sumOf { sizes[it].width } + (line.size - 1) * style.gap
            val (initial, extra) = distribution(style.arrangement, max(0.0, area.width - used), line.size)
            var x = initial
            line.forEach { index ->
                val size = sizes[index]
                val child = children[index]
                val alignment = (child.place as? Place.InFlow)?.align
                    ?: style.alignment
                val allocatedWidth = size.width
                val childHeight = if (alignment == CrossAlign.Stretch) height else size.height
                result[index] = UiRect(x, y + align(alignment, height, childHeight), allocatedWidth, childHeight)
                x += allocatedWidth + style.gap + extra
            }
            y += height + style.lineGap
        }
        return result
    }

    fun paint(element: UiNode<*>, backend: UiBackend, clip: UiRect, focused: UiNode<*>?) {
        if (!element.isVisibleInTree) return
        val inheritedClip = element.layoutClip?.let { clip.intersect(it) } ?: clip
        val ownClip = if (element.style.clip) inheritedClip.intersect(element.bounds) else inheritedClip
        val appearance = element.appearance(element === focused)
        backend.drawBox(
            element.bounds,
            ownClip,
            appearance,
            if (element.kind == UiKind.Button) UiBorder.None else element.resolvedBorder
        )
        element.paintContent(backend, ownClip, appearance)
        if (element.draws) {
            val padding = element.contentInset
            val area = UiRect(
                element.bounds.x + padding,
                element.bounds.y + padding,
                max(0.0, element.bounds.width - 2 * padding),
                max(0.0, element.bounds.height - 2 * padding)
            )
            val context =
                DrawContext(backend, area, if (element.style.clip) ownClip.intersect(area) else ownClip, appearance)
            try {
                element.drawSelf(context)
            } finally {
                context.close()
            }
        }
        when (element.kind) {
            UiKind.Text, UiKind.Button -> {
                val padding = element.contentInset
                val bounds = element.bounds
                val textBounds = UiRect(
                    bounds.x + padding,
                    bounds.y + padding,
                    max(0.0, bounds.width - 2 * padding),
                    max(0.0, bounds.height - 2 * padding)
                )
                if (element.kind == UiKind.Button) {
                    backend.drawButton(
                        element.contentText,
                        textBounds,
                        ownClip,
                        element === focused,
                        element.actionEnabled,
                        element.style.wrap,
                        appearance,
                        element.resolvedBorder
                    )
                } else {
                    backend.drawText(
                        element.contentText,
                        textBounds,
                        ownClip,
                        element === focused,
                        element.style.wrap,
                        appearance
                    )
                }
            }
            else -> {
                val padding = element.contentInset
                val area = UiRect(
                    element.bounds.x + padding,
                    element.bounds.y + padding,
                    max(0.0, element.bounds.width - 2 * padding),
                    max(0.0, element.bounds.height - 2 * padding)
                )
                val contentClip = if (element.style.clip) ownClip.intersect(area) else ownClip
                element.content?.nodes()?.forEach {
                    paintNode(
                        it,
                        backend,
                        area,
                        if (it is UiNode<*> && element.resolvedBorder == UiBorder.None) ownClip else contentClip,
                        focused,
                        appearance
                    )
                }
            }
        }
    }

    fun paintNode(
        node: Node<*>,
        backend: UiBackend,
        area: UiRect,
        clip: UiRect,
        focused: UiNode<*>?,
        appearance: UiAppearance = UiAppearance(),
    ) {
        if (node is UiNode<*>) {
            paint(node, backend, clip, focused)
        } else {
            val context = DrawContext(backend, area, clip, appearance)
            try {
                node.drawSubtree(context) {
                    when (it) {
                        is UiNode<*> -> {
                            paint(it, backend, clip, focused)
                            true
                        }
                        else -> false
                    }
                }
            } finally {
                context.close()
            }
        }
    }
    private fun resolve(policy: UiLength, available: Double, content: Double, cap: Double?): Double = min(
        cap ?: Double.MAX_VALUE,
        when (policy) {
            UiLength.Content -> content
            UiLength.Fill, is UiLength.Weight -> available
            is UiLength.Fixed -> policy.value
            is UiLength.Fraction -> available * policy.value
        }
    ).coerceAtLeast(0.0)
    private fun align(alignment: CrossAlign, available: Double, used: Double): Double = when (alignment) {
        CrossAlign.Start, CrossAlign.Stretch -> 0.0
        CrossAlign.Center -> max(0.0, (available - used) / 2)
        CrossAlign.End -> max(0.0, available - used)
    }
}
