package io.canopy.engine.core.managers

import io.canopy.engine.core.nodes.Node

/** Maintains scene group membership in insertion order, independent of each node's local group set. */
internal class SceneGroups {
    private val groupsByNode = mutableMapOf<Node<*>, MutableSet<String>>()
    private val groups = mutableMapOf<String, MutableSet<Node<*>>>()

    fun query(group: String): List<Node<*>> = groups[group].orEmpty().filter { it.isInsideTree }

    fun snapshotForSignal(group: String): List<Node<*>> = groups[group]?.toList()
        ?: error("Group $group does not exist")

    fun contains(group: String, node: Node<*>): Boolean = node in groups[group].orEmpty()

    fun add(group: String, node: Node<*>): Boolean {
        if (!groups.getOrPut(group) { linkedSetOf() }.add(node)) return false
        groupsByNode.getOrPut(node) { linkedSetOf() }.add(group)
        return true
    }

    fun remove(group: String, node: Node<*>) {
        val members = groups[group] ?: error("Node $node does not exist in group $group")
        members.remove(node)
        groupsByNode[node]?.remove(group) ?: error("Group $group does not exist")
        if (members.isEmpty()) groups.remove(group)
        if (groupsByNode[node].isNullOrEmpty()) groupsByNode.remove(node)
    }

    fun update(node: Node<*>, desired: Set<String>): Boolean {
        val previous = groupsByNode[node].orEmpty()
        if (previous == desired) return false
        previous.filterNot(desired::contains).forEach { group ->
            val members = groups[group]
            members?.remove(node)
            if (members.isNullOrEmpty()) groups.remove(group)
        }
        desired.filterNot(previous::contains).forEach { group ->
            groups.getOrPut(group) { linkedSetOf() }.add(node)
        }
        if (desired.isEmpty()) {
            groupsByNode.remove(node)
        } else {
            groupsByNode[node] = linkedSetOf<String>().apply { addAll(desired) }
        }
        return true
    }

    fun unregister(node: Node<*>) {
        groupsByNode.remove(node)?.forEach { group ->
            groups[group]?.remove(node)
            if (groups[group].isNullOrEmpty()) groups.remove(group)
        }
    }
}
