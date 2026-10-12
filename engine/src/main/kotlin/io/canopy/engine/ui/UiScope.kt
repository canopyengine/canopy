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
class UiScope internal constructor(private val root: UiRoot, private val parent: Node<*>) {
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
            root.dirty = true
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
    private fun retire(entry: Entry, failures: CleanupFailures) {
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
                val name = "Ui${identities++}"
                when (kind) {
                    UiKind.Text -> TextNode(name = name)
                    UiKind.Button -> ButtonNode(name = name)
                    UiKind.Row -> RowNode(name = name)
                    UiKind.Column -> ColumnNode(name = name)
                    UiKind.Box -> BoxNode(name = name)
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
        val previousChildren = parent.children.values.toSet()
        val created = nodeConstruction {
            parent.withManagedChildrenMutation {
                untrack { parent.withChildConstruction { create() } }.also {
                    val added = parent.children.values.filter { child -> child !in previousChildren }
                    require(
                        added == listOf(it) &&
                            it.parent === parent &&
                            it !is UiRoot &&
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

    /** Universal horizontal container. */
    fun Row(style: UiStyle = UiStyle(width = UiLength.Fill), block: UiScope.() -> Unit): RowNode =
        container(UiKind.Row, style, block) as RowNode

    /** Universal vertical container. */
    fun Column(style: UiStyle = UiStyle(width = UiLength.Fill), block: UiScope.() -> Unit): ColumnNode =
        container(UiKind.Column, style, block) as ColumnNode

    /** Universal overlapping container. */
    fun Box(
        style: UiStyle = UiStyle(width = UiLength.Fill, height = UiLength.Fill),
        block: UiScope.() -> Unit,
    ): BoxNode = container(UiKind.Box, style, block) as BoxNode
    private fun container(kind: UiKind, style: UiStyle, block: UiScope.() -> Unit): UiNode<*> {
        val element = element(slot(kind.name), kind)
        element.style = style
        val scope = element.content ?: UiScope(root, element).also { element.content = it }
        untrack { scope.compose(block) }
        return element
    }

    /** Text primitive. With the compiler plugin, signal reads in [value] update this property only. */
    fun Text(value: String): TextNode = (element(slot("Text"), UiKind.Text) as TextNode).also { it.text = value }

    /** Action primitive, invoked by keyboard focus or explicit activation. */
    fun Button(label: String, onClick: () -> Unit): ButtonNode =
        (element(slot("Button"), UiKind.Button) as ButtonNode).also {
            it.text = label
            it.action = onClick
        }

    /** Compiler ABI: installs a targeted text expression observer. */
    fun bindText(site: String, value: () -> String): TextNode = bind(site, UiKind.Text, value, null) as TextNode

    /** Compiler ABI: installs a targeted button-label observer while leaving event callbacks unobserved. */
    fun bindButtonText(site: String, label: () -> String, onClick: () -> Unit): ButtonNode =
        bind(site, UiKind.Button, label, onClick) as ButtonNode
    private fun bind(site: String, kind: UiKind, value: () -> String, action: (() -> Unit)?): UiNode<*> {
        val element = element(site, kind)
        if (element is ButtonNode) element.action = action ?: {}
        val entry = retained[site]!!
        entry.observer?.dispose()
        entry.observer = UiObserver(element) {
            val next = value()
            when (element) {
                is TextNode -> element.text = next
                is ButtonNode -> element.text = next
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
    fun bindRow(site: String, style: () -> UiStyle, block: UiScope.() -> Unit): RowNode =
        bindContainer(site, UiKind.Row, style, block) as RowNode

    /** Compiler ABI for a targeted reactive column style. */
    fun bindColumn(site: String, style: () -> UiStyle, block: UiScope.() -> Unit): ColumnNode =
        bindContainer(site, UiKind.Column, style, block) as ColumnNode

    /** Compiler ABI for a targeted reactive box style. */
    fun bindBox(site: String, style: () -> UiStyle, block: UiScope.() -> Unit): BoxNode =
        bindContainer(site, UiKind.Box, style, block) as BoxNode
    private fun bindContainer(site: String, kind: UiKind, style: () -> UiStyle, block: UiScope.() -> Unit): UiNode<*> {
        val element = element(site, kind)
        val scope = element.content ?: UiScope(root, element).also { element.content = it }
        untrack { scope.compose(block) }
        val entry = retained[site]!!
        entry.observer?.dispose()
        entry.observer = UiObserver(element) { element.style = style() }
        return element
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

    /** Compiler ABI for inherited visibility without pausing simulation. */
    fun bindVisible(site: String, element: UiNode<*>, value: () -> Boolean) = property(site, element) {
        val next = value()
        if (element.isVisible != next) {
            element.isVisible = next
            root.dirty = true
        }
    }

    /** Compiler ABI for action eligibility. */
    fun bindEnabled(site: String, element: UiNode<*>, value: () -> Boolean) = property(site, element) {
        val next = value()
        require(element is ButtonNode)
        if (element.enabled != next) {
            element.enabled = next
            root.dirty = true
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
            it.scope?.suspend()
            componentScopes(it).forEach { scope -> scope.suspend() }
        }
    }
    internal fun resume() {
        retained.values.forEach {
            it.observer?.resume()
            it.scope?.resume()
            componentScopes(it).forEach { scope -> scope.resume() }
        }
    }
    private fun componentScopes(entry: Entry): List<UiScope> = if (entry.scope == null) {
        entry.node?.let(::layoutChildren).orEmpty().mapNotNull { if (it.isValid) it.content else null }
    } else {
        emptyList()
    }
    private fun region(): Entry {
        val region = parent.withManagedChildrenMutation {
            parent.withChildConstruction { UiRegion(root, "UiRegion${identities++}") }
        }
        val scope = UiScope(root, region)
        region.content = scope
        enterCreated(region)
        return Entry(node = region, scope = scope)
    }
    private fun entryNodes(entry: Entry): List<Node<*>> = entry.node?.let(::listOf).orEmpty()
    internal fun nodes(): List<Node<*>> = retained.values.flatMap { entry ->
        entry.scope?.nodes() ?: entryNodes(entry)
    }
    internal fun elements(): List<UiNode<*>> = nodes().flatMap(::layoutChildren)
    private fun layoutChildren(node: Node<*>): List<UiNode<*>> {
        if (!node.isValid) return emptyList()
        return when (node) {
            is UiRoot -> emptyList()
            is UiNode<*> -> listOf(node)
            else -> node.children.values.flatMap(::layoutChildren)
        }
    }
    private fun reorder() {
        val parentScope = when (parent) {
            is UiNode<*> -> parent.content
            is UiRegion -> parent.content
            else -> root.content
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
    private companion object {
        var identities = 0L
    }
}
