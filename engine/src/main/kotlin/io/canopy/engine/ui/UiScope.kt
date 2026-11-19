package io.canopy.engine.ui

import io.canopy.engine.core.CleanupFailures
import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.core.nodes.nodeConstruction
import io.canopy.engine.ui.components.Box as BoxNode
import io.canopy.engine.ui.components.Button as ButtonNode
import io.canopy.engine.ui.components.Column as ColumnNode
import io.canopy.engine.ui.components.Row as RowNode
import io.canopy.engine.ui.components.Text as TextNode

/** Declarative construction receiver. The compiler lowers property expressions and structural regions independently. */
@DeclarativeUi
@Suppress("ktlint:standard:function-naming")
class UiScope internal constructor(private val parent: Node<*>) {
    private val retained = linkedMapOf<Any, Entry>()
    private var requested = mutableListOf<Any>()
    private var counter = 0
    private var composing = false
    private var disposed = false

    private class Entry(val node: Node<*>? = null, val scope: UiScope? = null) {
        val element: UiNode<*>? get() = node as? UiNode<*>
        val region: UiRegion? get() = node as? UiRegion
        var observer: UiObserver? = null
        var remembered: Any? = null
        var hasRemembered = false
        var configured = false
        fun dispose() {
            observer?.dispose()
            scope?.dispose()
            val component = element
            if (component?.isValid == true) component.content?.dispose()
        }
    }
    internal fun compose(block: UiScope.() -> Unit) {
        check(!disposed)
        check(!composing) { "Cannot recursively compose the same UI scope" }
        composing = true
        val previousKeys = retained.keys.toSet()
        requested = mutableListOf()
        counter = 0
        try {
            NodeLifetime.withOwner(parent) { block() }
            val omitted = retained.keys - requested.toSet()
            val cleanup = CleanupFailures()
            for (key in omitted) {
                val entry = retained.remove(key)!!
                retire(entry, cleanup)
            }
            cleanup.rethrow()
            val ordered = requested.mapNotNull { retained[it] }
            retained.clear()
            requested.zip(ordered).forEach { (key, entry) -> retained[key] = entry }
            reorder()
        } catch (failure: Throwable) {
            val cleanup = CleanupFailures()
            cleanup.attempt { throw failure }
            val createdKeys = retained.keys - previousKeys
            for (key in createdKeys) retire(retained.remove(key)!!, cleanup)
            cleanup.rethrow()
        } finally {
            composing = false
        }
    }
    private fun retire(entry: Entry, failures: CleanupFailures): Unit = untrack {
        val nodes = entryNodes(entry)
        failures.attempt { entry.dispose() }
        nodes.forEach { node ->
            if (node.isValid && node.parent === parent) {
                failures.attempt { parent.withManagedChildrenMutation { parent.removeChild(node) } }
                failures.attempt { node.queueFree() }
            }
        }
    }
    private fun claim(key: Any): Entry? {
        check(key !in requested) { "Duplicate UI key '$key' in one sibling scope" }
        requested += key
        return retained[key]
    }
    private fun element(key: Any, kind: UiKind): UiNode<*> {
        val existing = claim(key)
        if (existing != null) {
            require(existing.element?.kind == kind) { "UI site '$key' changed primitive type" }
            return existing.element!!
        }
        val created = parent.withManagedChildrenMutation {
            parent.withChildConstruction {
                when (kind) {
                    UiKind.Text -> TextNode()
                    UiKind.Button -> ButtonNode()
                    UiKind.Row -> RowNode()
                    UiKind.Column -> ColumnNode()
                    UiKind.Box -> BoxNode()
                    UiKind.FlowRow -> io.canopy.engine.ui.components.FlowRow()
                    UiKind.Grid -> io.canopy.engine.ui.components.Grid()
                }
            }
        }
        retained[key] = Entry(node = created)
        enterCreated(created)
        return created
    }

    /**
     * Retains a UI or ordinary node at [site]. [create] runs once per retained identity and must
     * construct one fresh child. UI nodes participate in layout; ordinary nodes share the container's
     * drawing area. Omission destroys the subtree through normal node cleanup. The matching compiler
     * uses this hook for direct constructors, so user declarations normally need no explicit call.
     */
    @Suppress("UNCHECKED_CAST")
    fun <N : Node<*>> node(site: Any, create: () -> N): N {
        val existing = claim(site)
        if (existing != null) {
            require(existing.node != null) { "UI site '$site' changed declaration type" }
            return existing.node as N
        }
        val previousChildren = untrack { parent.children.values.toSet() }
        val created = nodeConstruction {
            parent.withManagedChildrenMutation {
                untrack { parent.withChildConstruction { create() } }.also {
                    val added = untrack { parent.children.values.filter { child -> child !in previousChildren } }
                    require(
                        added == listOf(it) &&
                            untrack { it.parent === parent } &&
                            it !is UiRegion
                    ) {
                        "A node declaration must construct exactly one fresh child in this scope"
                    }
                }
            }
        }
        retained[site] = Entry(node = created)
        enterCreated(created)
        return created
    }

    private fun enterCreated(node: Node<*>) {
        if (parent.isInsideTree && !parent.state("enter UI child").initializing) node.buildTree()
    }
    private fun slot(prefix: String) = "$prefix:${counter++}"

    /** Local Canvas boundary, retained with this scope; it does not register another global overlay. */
    fun Canvas(
        size: CanvasSize = CanvasSize.Viewport,
        anchor: Anchor = Anchor.TopLeft,
        content: UiScope.() -> Unit,
    ): Canvas<*> = node(slot("Canvas")) { io.canopy.engine.ui.Canvas(size, anchor, content = content) }

    /** Universal horizontal container. */
    fun Row(
        style: UiStyle = UiStyle(width = UiLength.Fill),
        place: Place = style.place,
        size: Size? = null,
        arrange: Arrange = style.arrangement,
        align: CrossAlign? = null,
        gap: Double = style.gap,
        block: UiScope.() -> Unit,
    ): RowNode<*> = container(UiKind.Row, style.withLayout(place, size, arrange, align, gap), block) as RowNode<*>

    /** Universal vertical container. */
    fun Column(
        style: UiStyle = UiStyle(width = UiLength.Fill),
        place: Place = style.place,
        size: Size? = null,
        arrange: Arrange = style.arrangement,
        align: CrossAlign? = null,
        gap: Double = style.gap,
        block: UiScope.() -> Unit,
    ): ColumnNode<*> =
        container(UiKind.Column, style.withLayout(place, size, arrange, align, gap), block) as ColumnNode<*>

    /** Universal overlapping container. */
    fun Box(
        style: UiStyle = UiStyle(width = UiLength.Fill, height = UiLength.Fill),
        place: Place = style.place,
        size: Size? = null,
        block: UiScope.() -> Unit,
    ): BoxNode<*> = container(UiKind.Box, style.withLayout(place, size), block) as BoxNode<*>

    /** Wrapping horizontal flow. Line spacing is independent of text wrapping. */
    fun FlowRow(
        style: UiStyle = UiStyle(width = UiLength.Fill),
        place: Place = style.place,
        size: Size? = null,
        arrange: Arrange = style.arrangement,
        align: CrossAlign? = null,
        gap: Double = style.gap,
        lineGap: Double = gap,
        block: UiScope.() -> Unit,
    ): io.canopy.engine.ui.components.FlowRow<*> = container(
        UiKind.FlowRow,
        style.withLayout(place, size, arrange, align, gap).copy(lineGap = lineGap),
        block
    ) as io.canopy.engine.ui.components.FlowRow<*>

    /** Equal-width grid cells; each row is as tall as its tallest child. */
    fun Grid(
        columns: Int,
        style: UiStyle? = null,
        gap: Double? = null,
        rows: Int? = null,
        place: Place? = null,
        size: Size? = null,
        cellSize: Size? = null,
        lines: GridLines? = null,
        cellPadding: Double? = null,
        block: io.canopy.engine.ui.components.Grid<*>.() -> Unit = {},
        content: UiScope.() -> Unit = {},
    ): io.canopy.engine.ui.components.Grid<*> = bindGrid(
        slot("Grid"), { columns }, style?.let { { it } }, gap?.let { { it } }, { rows },
        place?.let { { it } }, size?.let { { it } }, cellSize?.let { { it } },
        lines?.let { { it } }, cellPadding?.let { { it } }, block, content
    )

    /** Explicit retained cell slot. Use coordinate slots or sequential children within a grid, not both. */
    fun cell(x: Int, y: Int, content: UiScope.() -> Unit) {
        var owner: Node<*>? = parent
        while (owner != null && owner !is UiNode<*>) owner = owner.parent
        require(owner is io.canopy.engine.ui.components.Grid<*>) { "cell(x, y) requires a Grid parent" }
        require(x in 0 until owner.columns && y in 0 until (owner.rowLimit ?: Int.MAX_VALUE)) {
            "Cell ($x, $y) is outside the configured Grid dimensions"
        }
        val slot = node("cell:$x:$y") { io.canopy.engine.ui.components.GridCell(GridPosition(x, y)) {} }
        val scope = slot.content ?: UiScope(slot).also { slot.content = it }
        scope.compose(content)
    }
    private fun container(kind: UiKind, style: UiStyle, block: UiScope.() -> Unit): UiNode<*> {
        val element = element(slot(kind.name), kind)
        element.style = style
        val scope = element.content ?: UiScope(element).also { element.content = it }
        untrack { scope.compose(block) }
        return element
    }

    /** Text primitive. With the compiler plugin, signal reads in [value] update this property only. */
    fun Text(value: String, place: Place = Place.Auto, size: Size = Size.Content): TextNode<*> =
        (element(slot("Text"), UiKind.Text) as TextNode<*>).also {
            it.text = value
            it.style = it.style.withLayout(place, size)
        }

    /** Action primitive, invoked by keyboard focus or explicit activation. */
    fun Button(
        label: String,
        place: Place = Place.Auto,
        size: Size = Size.Content,
        onClick: () -> Unit,
    ): ButtonNode<*> = (element(slot("Button"), UiKind.Button) as ButtonNode<*>).also {
        it.text = label
        it.style = it.style.withLayout(place, size)
        it.action = onClick
    }

    /** Compiler ABI: installs a targeted text expression observer. */
    fun bindText(
        site: String,
        place: (() -> Place)? = null,
        size: (() -> Size)? = null,
        value: () -> String,
    ): TextNode<*> = bind(site, UiKind.Text, value, null) {
        it.withLayout(place?.invoke() ?: it.place, size?.invoke())
    } as TextNode<*>

    /** Compiler ABI: installs a targeted button-label observer while leaving event callbacks unobserved. */
    fun bindButtonText(
        site: String,
        label: () -> String,
        place: (() -> Place)? = null,
        size: (() -> Size)? = null,
        onClick: () -> Unit,
    ): ButtonNode<*> = bind(site, UiKind.Button, label, onClick) {
        it.withLayout(place?.invoke() ?: it.place, size?.invoke())
    } as ButtonNode<*>
    private fun bind(
        site: String,
        kind: UiKind,
        value: () -> String,
        action: (() -> Unit)?,
        layout: (UiStyle) -> UiStyle,
    ): UiNode<*> {
        val element = element(site, kind)
        if (element is ButtonNode<*>) element.action = action ?: {}
        val entry = retained[site]!!
        entry.observer?.dispose()
        entry.observer = UiObserver(element) {
            element.style = layout(element.style)
            val next = value()
            when (element) {
                is TextNode<*> -> element.text = next
                is ButtonNode<*> -> element.text = next
            }
        }
        return element
    }

    /** Compiler ABI: observes conditions/list dependencies and reconciles only this structural region. */
    fun structure(site: String, content: UiScope.() -> Unit) {
        val existing = claim(site)
        val entry = existing ?: region().also { retained[site] = it }
        entry.observer?.dispose()
        entry.observer = UiObserver(entry.region!!) {
            entry.scope!!.compose(content)
            reorder()
        }
    }

    /** Stable sibling identity for a changing collection; duplicate keys fail before identity reuse. */
    fun key(value: Any, content: UiScope.() -> Unit) {
        val existing = claim(value)
        val entry = existing ?: region().also { retained[value] = it }
        entry.scope!!.compose(content)
    }

    /** Compiler ABI for a targeted reactive row style. */
    fun bindRow(
        site: String,
        style: () -> UiStyle = { UiStyle(width = UiLength.Fill) },
        place: (() -> Place)? = null,
        size: (() -> Size)? = null,
        arrange: (() -> Arrange)? = null,
        align: (() -> CrossAlign?)? = null,
        gap: (() -> Double)? = null,
        block: UiScope.() -> Unit,
    ): RowNode<*> = bindContainer(site, UiKind.Row, {
        style().let {
            it.withLayout(
                place?.invoke() ?: it.place,
                size?.invoke(),
                arrange?.invoke() ?: it.arrangement,
                align?.invoke(),
                gap?.invoke() ?: it.gap
            )
        }
    }, block) as RowNode<*>

    /** Compiler ABI for a targeted reactive column style. */
    fun bindColumn(
        site: String,
        style: () -> UiStyle = { UiStyle(width = UiLength.Fill) },
        place: (() -> Place)? = null,
        size: (() -> Size)? = null,
        arrange: (() -> Arrange)? = null,
        align: (() -> CrossAlign?)? = null,
        gap: (() -> Double)? = null,
        block: UiScope.() -> Unit,
    ): ColumnNode<*> = bindContainer(site, UiKind.Column, {
        style().let {
            it.withLayout(
                place?.invoke() ?: it.place,
                size?.invoke(),
                arrange?.invoke() ?: it.arrangement,
                align?.invoke(),
                gap?.invoke() ?: it.gap
            )
        }
    }, block) as ColumnNode<*>

    /** Compiler ABI for a targeted reactive box style. */
    fun bindBox(
        site: String,
        style: () -> UiStyle = { UiStyle(width = UiLength.Fill, height = UiLength.Fill) },
        place: (() -> Place)? = null,
        size: (() -> Size)? = null,
        block: UiScope.() -> Unit,
    ): BoxNode<*> = bindContainer(site, UiKind.Box, {
        style().let { it.withLayout(place?.invoke() ?: it.place, size?.invoke()) }
    }, block) as BoxNode<*>
    private fun bindContainer(site: String, kind: UiKind, style: () -> UiStyle, block: UiScope.() -> Unit): UiNode<*> {
        val element = element(site, kind)
        val scope = element.content ?: UiScope(element).also { element.content = it }
        untrack { scope.compose(block) }
        val entry = retained[site]!!
        entry.observer?.dispose()
        entry.observer = UiObserver(element) { element.style = style() }
        return element
    }

    /** Compiler ABI for reactive wrapping flow configuration, without reconstructing child nodes. */
    fun bindFlowRow(
        site: String,
        style: () -> UiStyle = { UiStyle(width = UiLength.Fill) },
        place: (() -> Place)? = null,
        size: (() -> Size)? = null,
        arrange: (() -> Arrange)? = null,
        align: (() -> CrossAlign?)? = null,
        gap: (() -> Double)? = null,
        lineGap: (() -> Double)? = null,
        block: UiScope.() -> Unit,
    ): io.canopy.engine.ui.components.FlowRow<*> = bindContainer(site, UiKind.FlowRow, {
        style().let {
            val spacing = gap?.invoke() ?: it.gap
            it.withLayout(
                place?.invoke() ?: it.place,
                size?.invoke(),
                arrange?.invoke() ?: it.arrangement,
                align?.invoke(),
                spacing
            ).copy(lineGap = lineGap?.invoke() ?: spacing)
        }
    }, block) as io.canopy.engine.ui.components.FlowRow<*>

    /** Compiler ABI for reactive column count and grid sizing. */
    fun bindGrid(
        site: String,
        columns: () -> Int,
        style: (() -> UiStyle?)? = null,
        gap: (() -> Double?)? = null,
        rows: (() -> Int?)? = null,
        place: (() -> Place?)? = null,
        size: (() -> Size?)? = null,
        cellSize: (() -> Size?)? = null,
        lines: (() -> GridLines?)? = null,
        cellPadding: (() -> Double?)? = null,
        block: io.canopy.engine.ui.components.Grid<*>.() -> Unit = {},
        content: UiScope.() -> Unit = {},
    ): io.canopy.engine.ui.components.Grid<*> {
        val grid = element(site, UiKind.Grid) as io.canopy.engine.ui.components.Grid<*>
        val entry = retained.getValue(site)
        entry.observer?.dispose()
        entry.observer = UiObserver(grid) {
            val limit = rows?.invoke()
            val base = style?.invoke() ?: if (entry.configured) {
                grid.style
            } else {
                UiStyle(width = if (limit == null) UiLength.Fill else UiLength.Content)
            }
            val spacing = gap?.invoke() ?: base.gap
            val columnCount = columns()
            grid.configureColumns(columnCount)
            grid.style = base.copy(gap = spacing, lineGap = if (gap != null) spacing else base.lineGap)
                .withLayout(place?.invoke() ?: base.place, size?.invoke())
            if (rows != null) grid.rowLimit = limit
            if (cellSize != null) grid.cellSize = cellSize()
            lines?.invoke()?.let { grid.lines = it }
            cellPadding?.invoke()?.let { grid.cellPadding = it }
            if (!entry.configured) {
                entry.configured = true
                untrack { block(grid) }
            }
        }
        val scope = grid.content ?: UiScope(grid).also { grid.content = it }
        untrack { scope.compose(content) }
        return grid
    }

    /** Retains initialization once per stable declaration/key identity. */
    @Suppress("UNCHECKED_CAST")
    fun <T> remember(site: String, initializer: () -> T): T {
        val existing = claim(site)
        val entry = existing ?: region().also { retained[site] = it }
        if (!entry.hasRemembered) {
            entry.remembered = NodeLifetime.withOwner(entry.region) { initializer() }
            entry.hasRemembered = true
        }
        return entry.remembered as T
    }

    /** Compiler ABI for a reactive universal layout property. */
    fun bindStyle(site: String, element: UiNode<*>, value: () -> UiStyle) = property(site, element) {
        element.style = value()
    }

    /** Compiler ABI for a targeted reactive placement update. */
    fun bindPlace(site: String, element: UiNode<*>, value: () -> Place) = property(site, element) {
        element.place = value()
    }

    /** Compiler ABI for targeted reactive logical sizing. */
    fun bindSize(site: String, element: UiNode<*>, value: () -> Size) = property(site, element) {
        element.size = value()
    }

    /** Compiler ABI for a reactive subtree theme. */
    fun bindTheme(site: String, element: UiNode<*>, value: () -> UiTheme?) = property(site, element) {
        element.theme = value()
    }

    /** Compiler ABI for inherited visibility without pausing simulation. */
    fun bindVisible(site: String, element: UiNode<*>, value: () -> Boolean) = property(site, element) {
        val next = value()
        if (element.isVisible != next) {
            element.isVisible = next
        }
    }

    /** Compiler ABI for action eligibility. */
    fun bindEnabled(site: String, element: UiNode<*>, value: () -> Boolean) = property(site, element) {
        val next = value()
        require(element is ButtonNode<*>)
        if (element.enabled != next) {
            element.enabled = next
        }
    }
    private fun property(site: String, element: UiNode<*>, action: () -> Unit) {
        val existing = claim(site)
        val entry = existing ?: Entry().also { retained[site] = it }
        entry.observer?.dispose()
        entry.observer = UiObserver(element, action)
    }
    internal fun suspend() {
        retained.values.forEach {
            it.observer?.suspend()
        }
    }
    internal fun resume() {
        retained.values.forEach {
            it.observer?.resume()
        }
    }
    private fun region(): Entry {
        val region = parent.withManagedChildrenMutation {
            parent.withChildConstruction { UiRegion() }
        }
        val scope = UiScope(region)
        region.content = scope
        enterCreated(region)
        return Entry(node = region, scope = scope)
    }
    private fun entryNodes(entry: Entry): List<Node<*>> = entry.node?.let(::listOf).orEmpty()
    internal fun nodes(): List<Node<*>> = buildList { forEachNode { add(it) } }
    internal fun elements(): List<UiNode<*>> = buildList { forEachElement { add(it) } }
    internal fun forEachNode(action: (Node<*>) -> Unit) {
        retained.values.forEach { entry ->
            val scope = entry.scope
            if (scope != null) scope.forEachNode(action) else entry.node?.let(action)
        }
    }
    internal fun forEachElement(action: (UiNode<*>) -> Unit) {
        forEachNode { layoutChildren(it, action) }
    }
    private fun layoutChildren(node: Node<*>, action: (UiNode<*>) -> Unit) {
        if (!node.isValid) return
        when (node) {
            is UiNode<*> -> action(node)
            else -> node.childSnapshot().forEach { layoutChildren(it, action) }
        }
    }
    private fun reorder(): Unit = untrack {
        val parentScope = when (parent) {
            is UiNode<*> -> parent.content
            is UiRegion -> parent.content
            else -> null
        }
        val order = parentScope?.retained?.values?.flatMap(::entryNodes).orEmpty()
        if (order.size == parent.children.size && order.all { it.isValid && it.parent === parent }) {
            parent.withManagedChildrenMutation { parent.reorderManagedChildren(order) }
        }
    }
    internal fun dispose() {
        if (disposed) return
        disposed = true
        retained.values.forEach { it.dispose() }
        retained.clear()
        requested.clear()
    }
}
