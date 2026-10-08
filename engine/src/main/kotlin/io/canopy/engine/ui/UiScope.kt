package io.canopy.engine.ui

import io.canopy.engine.core.CleanupFailures
import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/** Declarative construction receiver. The compiler lowers property expressions and structural regions independently. */
@DeclarativeUi
@Suppress("ktlint:standard:function-naming")
class UiScope internal constructor(private val root: UiRoot, private val parent: Node<*>) {
    private val retained = linkedMapOf<Any, Entry>()
    private var requested = mutableListOf<Any>()
    private var counter = 0
    private var composing = false
    private var disposed = false

    private class Entry(val element: UiElement? = null, val scope: UiScope? = null, val region: UiRegion? = null) {
        var observer: UiObserver? = null
        var remembered: Any? = null
        var hasRemembered = false
        fun dispose() {
            observer?.dispose()
            scope?.dispose()
            if (element?.isValid ==
                true
            ) {
                element.content?.dispose()
            }
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
    private fun element(key: Any, kind: UiKind): UiElement {
        val existing = claim(key)
        if (existing != null) {
            require(existing.element?.kind == kind) { "UI site '$key' changed primitive type" }
            return existing.element!!
        }
        val created = parent.withManagedChildrenMutation {
            parent.withChildConstruction { UiElement(kind, root, "Ui${identities++}") }
        }
        retained[key] = Entry(element = created)
        if (parent.isInsideTree) created.buildTree()
        return created
    }
    private fun slot(prefix: String) = "$prefix:${counter++}"

    /** Universal horizontal container. */
    fun Row(style: UiStyle = UiStyle(width = UiLength.Fill), block: UiScope.() -> Unit): UiElement =
        container(UiKind.Row, style, block)

    /** Universal vertical container. */
    fun Column(style: UiStyle = UiStyle(width = UiLength.Fill), block: UiScope.() -> Unit): UiElement =
        container(UiKind.Column, style, block)

    /** Universal overlapping container. */
    fun Box(
        style: UiStyle = UiStyle(width = UiLength.Fill, height = UiLength.Fill),
        block: UiScope.() -> Unit,
    ): UiElement = container(UiKind.Box, style, block)
    private fun container(kind: UiKind, style: UiStyle, block: UiScope.() -> Unit): UiElement {
        val element = element(slot(kind.name), kind)
        element.style = style
        val scope = element.content ?: UiScope(root, element).also { element.content = it }
        untrack { scope.compose(block) }
        return element
    }

    /** Text primitive. With the compiler plugin, signal reads in [value] update this property only. */
    fun Text(value: String): UiElement = element(slot("Text"), UiKind.Text).also { it.text = value }

    /** Action primitive, invoked by keyboard focus or explicit activation. */
    fun Button(label: String, onClick: () -> Unit): UiElement = element(slot("Button"), UiKind.Button).also {
        it.text = label
        it.action = onClick
    }

    /** Compiler ABI: installs a targeted text expression observer. */
    fun bindText(site: String, value: () -> String): UiElement = bind(site, UiKind.Text, value, null)

    /** Compiler ABI: installs a targeted button-label observer while leaving event callbacks unobserved. */
    fun bindButtonText(site: String, label: () -> String, onClick: () -> Unit): UiElement =
        bind(site, UiKind.Button, label, onClick)
    private fun bind(site: String, kind: UiKind, value: () -> String, action: (() -> Unit)?): UiElement {
        val element = element(site, kind)
        element.action = action
        val entry = retained[site]!!
        entry.observer?.dispose()
        entry.observer = UiObserver(element) {
            val next = value()
            if (element.text != next) {
                element.text = next
                root.dirty = true
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
    fun bindRow(site: String, style: () -> UiStyle, block: UiScope.() -> Unit): UiElement =
        bindContainer(site, UiKind.Row, style, block)

    /** Compiler ABI for a targeted reactive column style. */
    fun bindColumn(site: String, style: () -> UiStyle, block: UiScope.() -> Unit): UiElement =
        bindContainer(site, UiKind.Column, style, block)

    /** Compiler ABI for a targeted reactive box style. */
    fun bindBox(site: String, style: () -> UiStyle, block: UiScope.() -> Unit): UiElement =
        bindContainer(site, UiKind.Box, style, block)
    private fun bindContainer(site: String, kind: UiKind, style: () -> UiStyle, block: UiScope.() -> Unit): UiElement {
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
    fun bindStyle(site: String, element: UiElement, value: () -> UiStyle) = property(site, element) {
        element.style = value()
    }

    /** Compiler ABI for inherited visibility without pausing simulation. */
    fun bindVisible(site: String, element: UiElement, value: () -> Boolean) = property(site, element) {
        val next = value()
        if (element.isVisible != next) {
            element.isVisible = next
            root.dirty = true
        }
    }

    /** Compiler ABI for action eligibility. */
    fun bindEnabled(site: String, element: UiElement, value: () -> Boolean) = property(site, element) {
        val next = value()
        if (element.enabled != next) {
            element.enabled = next
            root.dirty = true
        }
    }
    private fun property(site: String, element: UiElement, action: () -> Unit) {
        val existing = claim(site)
        val entry = existing ?: Entry().also { retained[site] = it }
        entry.observer?.dispose()
        entry.observer = UiObserver(element, action)
    }
    internal fun suspend() {
        retained.values.forEach {
            it.observer?.suspend()
            it.scope?.suspend()
            if (it.element?.isValid ==
                true
            ) {
                it.element.content?.suspend()
            }
        }
    }
    internal fun resume() {
        retained.values.forEach {
            it.observer?.resume()
            it.scope?.resume()
            if (it.element?.isValid ==
                true
            ) {
                it.element.content?.resume()
            }
        }
    }
    private fun region(): Entry {
        val region = parent.withManagedChildrenMutation {
            parent.withChildConstruction { UiRegion(root, "UiRegion${identities++}") }
        }
        val scope = UiScope(root, region)
        region.content = scope
        if (parent.isInsideTree) region.buildTree()
        return Entry(scope = scope, region = region)
    }
    private fun entryNodes(entry: Entry): List<Node<*>> = entry.element?.let(::listOf)
        ?: entry.region?.let(::listOf).orEmpty()
    internal fun elements(): List<UiElement> = retained.values.flatMap(::entryElements)
    private fun entryElements(entry: Entry): List<UiElement> = entry.element?.let(::listOf)
        ?: entry.scope?.elements().orEmpty()
    private fun reorder() {
        val parentScope = when (parent) {
            is UiElement -> parent.content
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
