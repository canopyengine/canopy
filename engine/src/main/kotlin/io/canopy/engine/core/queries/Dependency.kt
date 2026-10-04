package io.canopy.engine.core.queries

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node

/**
 * A typed, read-only runtime dependency for a [Node] or [Behavior] property.
 *
 * Each read resolves again on the game thread: no node, owner, provider, or resolved value is retained.
 * Valid detached nodes can query their hierarchy and context; tree/group queries require entered membership.
 * Missing required values throw [NoSuchElementException], including the property and query in the message.
 * Optional queries return null only for missing values; invalid owners and provider failures still throw.
 * Construct these final delegates through the query factories in this package.
 */
class Dependency<T> private constructor(
    private val kind: Kind,
    private val type: KClass<*>,
    private val key: String?,
    private val optional: Boolean,
) {
    @PublishedApi
    internal enum class Kind { Ancestor, Child, Tree, Group, Context, Manager }

    @PublishedApi
    internal companion object {
        fun <T> create(kind: Kind, type: KClass<*>, key: String? = null, optional: Boolean = false): Dependency<T> =
            Dependency(kind, type, key, optional)
    }

    /** Resolves against this node, enforcing its lifetime before every read. */
    operator fun getValue(thisRef: Node<*>, property: KProperty<*>): T = resolve(thisRef, property)

    /** Resolves against the behavior's constructor-supplied node; detached behaviors have no owner. */
    operator fun getValue(thisRef: Behavior<*>, property: KProperty<*>): T =
        resolve(thisRef.dependencyOwner(), property)

    @Suppress("UNCHECKED_CAST")
    private fun resolve(owner: Node<*>?, property: KProperty<*>): T {
        owner?.requireValid("read dependency '${property.name}'")
        val result: Any? = when (kind) {
            Kind.Manager -> if (owner == null) {
                null
            } else {
                val managerType = type as KClass<Manager>
                if (ManagersRegistry.has(managerType)) ManagersRegistry.getManager(managerType) else null
            }
            Kind.Ancestor -> {
                var current = owner?.parent
                while (current != null && !type.isInstance(current)) current = current.parent
                current
            }
            Kind.Child -> owner?.let { visibleChildren(it).firstOrNull(type::isInstance) }
            Kind.Tree -> owner?.takeIf { it.isInsideTree }?.let {
                var root = it
                while (root.parent != null) root = root.parent!!
                walk(root).firstOrNull { node -> node.isInsideTree && type.isInstance(node) }
            }
            Kind.Group -> owner?.takeIf { it.isInsideTree }?.owningManager()
                ?.queryGroup(key!!)?.filter(type::isInstance).orEmpty()
            Kind.Context -> owner?.let {
                if (key != null) {
                    var current: Node<*>? = it
                    var value: Any? = null
                    while (current != null) {
                        if (current is Context && key in current.provided) {
                            // A null provider shadows outer scopes, just like a non-null provider.
                            value = current.provided.getValue(key)()
                            break
                        }
                        current = current.parent
                    }
                    check(value == null || type.isInstance(value)) {
                        "Dependency '${property.name}': context '$key' is not ${type.qualifiedName}"
                    }
                    value
                } else {
                    var current: Node<*>? = it
                    var value: Any? = null
                    while (current != null) {
                        if (current is Context && current.hasTypedProvider(type)) {
                            value = current.typedValue(type)
                            break
                        }
                        current = current.parent
                    }
                    value
                }
            }
        }
        if (result == null && !optional) {
            throw NoSuchElementException(
                "Dependency '${property.name}': $kind<${type.qualifiedName}>" +
                    (key?.let { "('$it')" } ?: "") + " not found for " +
                    (owner?.let { "node ${it.nodeId} (${it.path})" } ?: "a behavior without a node")
            )
        }
        return result as T
    }

    private fun visibleChildren(node: Node<*>): Sequence<Node<*>> = node.children.values.asSequence().flatMap {
        if (it.isSearchTransparent()) visibleChildren(it) else sequenceOf(it)
    }

    private fun walk(root: Node<*>): Sequence<Node<*>> = sequence {
        // Iterative preorder avoids recursive stack growth for deep scene trees.
        val pending = ArrayDeque<Node<*>>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            yield(node)
            node.children.values.toList().asReversed().forEach(pending::add)
        }
    }
}
