package io.canopy.engine.core.queries

import io.canopy.engine.core.flows.ContextKey
import io.canopy.engine.core.nodes.Node

/**
 * Required dependency on the nearest matching ancestor, excluding the owner and including actual context ancestors.
 * Missing values throw on read. See [NodeDependency] for lifetime and threading rules.
 */
inline fun <reified T : Node<*>> ancestor(): NodeDependency<T> =
    NodeDependency.create(NodeDependency.Kind.Ancestor, T::class)

/** Optional version of [ancestor]; missing dependencies return null on each read. */
inline fun <reified T : Node<*>> ancestorOrNull(): NodeDependency<T?> =
    NodeDependency.create(NodeDependency.Kind.Ancestor, T::class, optional = true)

/**
 * Required dependency on the first matching direct child in insertion order, through transparent context wrappers.
 * Missing values throw on read. See [NodeDependency] for lifetime and threading rules.
 */
inline fun <reified T : Node<*>> child(): NodeDependency<T> = NodeDependency.create(NodeDependency.Kind.Child, T::class)

/** Optional version of [child]; missing dependencies return null on each read. */
inline fun <reified T : Node<*>> childOrNull(): NodeDependency<T?> =
    NodeDependency.create(NodeDependency.Kind.Child, T::class, optional = true)

/**
 * Required dependency on the first entered match in preorder from the owner's hierarchy root, including that root.
 * Missing values throw on read. See [NodeDependency] for lifetime and threading rules.
 */
inline fun <reified T : Node<*>> tree(): NodeDependency<T> = NodeDependency.create(NodeDependency.Kind.Tree, T::class)

/** Optional version of [tree]; missing dependencies return null on each read. */
inline fun <reified T : Node<*>> treeOrNull(): NodeDependency<T?> =
    NodeDependency.create(NodeDependency.Kind.Tree, T::class, optional = true)

/**
 * Required dependency on the nearest scope's provider registered with Context.provide<T>; types match exactly.
 * Missing values throw on read. See [NodeDependency] for lifetime and threading rules.
 */
inline fun <reified T : Any> context(): NodeDependency<T> = NodeDependency.create(NodeDependency.Kind.Context, T::class)

/** Optional version of [context]; missing dependencies return null on each read. */
inline fun <reified T : Any> contextOrNull(): NodeDependency<T?> =
    NodeDependency.create(NodeDependency.Kind.Context, T::class, optional = true)

/**
 * Snapshot of entered group members assignable to T, in the owner's scene manager's registration order.
 * Missing groups and detached owners return an empty list. Each read produces a new list.
 */
inline fun <reified T : Node<*>> group(name: String): NodeDependency<List<T>> =
    NodeDependency.create(NodeDependency.Kind.Group, T::class, name)

/** Required keyed context dependency, preserving nearest-scope shadowing; incompatible values throw on read. */
inline fun <reified T : Any> context(key: String): NodeDependency<T> =
    NodeDependency.create(NodeDependency.Kind.Context, T::class, key)

/** Required keyed context dependency using an existing [ContextKey]. */
inline fun <reified T : Any> context(key: ContextKey): NodeDependency<T> = context(key.key)

/** Optional keyed context dependency; missing/null values return null and incompatible values throw. */
inline fun <reified T : Any> contextOrNull(key: String): NodeDependency<T?> =
    NodeDependency.create(NodeDependency.Kind.Context, T::class, key, optional = true)

/** Optional keyed context dependency using an existing [ContextKey]. */
inline fun <reified T : Any> contextOrNull(key: ContextKey): NodeDependency<T?> = contextOrNull(key.key)
