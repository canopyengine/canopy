package io.canopy.engine.core.queries

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node

/**
 * Read-only dependency resolved against a node hierarchy or context on each game-thread read.
 * Valid detached nodes can resolve ancestors, children and context; tree/group reads require entered membership.
 * Destroyed owners reject every read, including optional reads. Behaviors resolve against their supplied node.
 * No owner, provider or resolved value is retained. Construct through the node query factories.
 */
class NodeDependency<T> private constructor(private val kind: Kind, type: KClass<*>, key: String?, optional: Boolean) :
    Dependency<T>(kind.name, type, key, optional) {
    @PublishedApi
    internal enum class Kind { Ancestor, Child, Tree, Group, Context }

    @PublishedApi
    internal companion object {
        fun <T> create(kind: Kind, type: KClass<*>, key: String? = null, optional: Boolean = false): NodeDependency<T> =
            NodeDependency(kind, type, key, optional)
    }

    /** Resolves against this node after validating its lifetime. */
    operator fun getValue(thisRef: Node<*>, property: KProperty<*>): T = resolve(thisRef, property)

    /** Resolves against the behavior's supplied node; missing owners produce missing dependencies. */
    operator fun getValue(thisRef: Behavior<*>, property: KProperty<*>): T =
        resolve(thisRef.dependencyOwner(), property)

    @Suppress("UNCHECKED_CAST")
    private fun resolve(owner: Node<*>?, property: KProperty<*>): T {
        owner?.requireValid("read dependency '${property.name}'")
        val result: Any? = when (kind) {
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
        return finish(result, property) {
            owner?.let { "node ${it.nodeId} (${it.path})" } ?: "a behavior without a node"
        }
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
