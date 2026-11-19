package io.canopy.engine.core.nodes

import io.canopy.engine.core.CleanupFailures

/** Applies physical tree mutations while Node retains its public facade and destruction-phase state access. */
internal object NodeHierarchy {
    fun rename(node: Node<*>, value: String) {
        val nodeState = node.state("rename")
        if (nodeState.name == value) {
            nodeState.autoName = false
            return
        }
        val parentState = nodeState.parent?.state("rename child")
        nodeState.parent?.checkChildrenMutation("rename child")
        if (parentState != null && value in parentState.children) {
            node.fail("rename", "Sibling '$value' already exists")
        }
        parentState?.children?.remove(nodeState.name)
        nodeState.name = value
        nodeState.autoName = false
        parentState?.children?.set(value, node)
        parentState?.changed()
        node.refreshPaths()
        nodeState.owner.hierarchyChanged()
    }

    fun reorderManagedChildren(parent: Node<*>, order: List<Node<*>>): Unit = parent.owningManager().mutateHierarchy {
        parent.checkChildrenMutation("reorder managed children")
        val parentState = parent.state("reorder managed children")
        require(order.size == parentState.children.size && order.toSet() == parentState.children.values.toSet()) {
            "Reordering must preserve exact child membership"
        }
        if (parentState.snapshot() == order) return
        parentState.children.clear()
        order.forEach { child -> parentState.children[child.name] = child }
        parentState.changed()
        parentState.owner.hierarchyChanged()
    }

    fun attach(parent: Node<*>, child: Node<*>): Unit = parent.owningManager().mutateHierarchy {
        parent.checkChildrenMutation("attach")
        val parentState = parent.state("attach")
        val childState = child.state("attach")
        if (parentState.owner !== childState.owner) parent.fail("attach", "Nodes belong to different scene managers")
        if (childState.parent != null) parent.fail("attach", "Child already has a parent")
        var ancestor: Node<*>? = parent
        while (ancestor != null) {
            if (ancestor === child) parent.fail("attach", "Cannot create a hierarchy cycle")
            ancestor = ancestor.state("attach").parent
        }
        val targetName = if (childState.autoName) automaticName(parentState, child) else childState.name
        if (targetName in parentState.children) parent.fail("attach", "Child '$targetName' already exists")
        childState.name = targetName
        parentState.children[childState.name] = child
        parentState.changed()
        childState.parent = parent
        childState.parentState = parentState
        child.refreshPaths()
        parentState.owner.hierarchyChanged()
        if (parentState.entered) parentState.owner.registerSubtree(child)
    }

    private fun automaticName(parentState: NodeState, child: Node<*>): String {
        val base = child.javaClass.simpleName.ifEmpty { "Node" }
        var index = parentState.children.values.count { it.javaClass == child.javaClass }
        var candidate = if (index == 0) base else "$base$index"
        while (candidate in parentState.children) {
            index++
            candidate = "$base$index"
        }
        return candidate
    }

    fun addChild(parent: Node<*>, child: Node<*>): Unit = parent.owningManager().mutateHierarchy {
        parent.checkChildrenMutation("addChild")
        val parentState = parent.state("addChild")
        val childState = child.state("addChild")
        if (childState.parent !== parent) attach(parent, child)
        if (parentState.entered && !parentState.initializing && !childState.prefab) child.buildTree()
    }

    fun removeChild(parent: Node<*>, child: Node<*>): Unit = parent.owningManager().mutateHierarchy {
        parent.checkChildrenMutation("removeChild")
        val parentState = parent.state("removeChild")
        if (child.state("removeChild").parent !== parent) parent.fail("removeChild", "Node is not a child")
        val failures = CleanupFailures()
        failures.attempt { child.nodeExitTree() }
        failures.attempt { parentState.owner.unregisterSubtree(child) }
        child.detachFromParent()
        failures.rethrow()
    }

    fun reparent(parent: Node<*>, child: Node<*>, destination: Node<*>): Unit = parent.owningManager().mutateHierarchy {
        val parentState = parent.state("reparent")
        val childState = child.state("reparent")
        val destinationState = destination.state("reparent")
        if (childState.parent !== parent) parent.fail("reparent", "Node is not a child")
        if (destination === parent) return
        parent.checkChildrenMutation("reparent")
        destination.checkChildrenMutation("reparent")
        var ancestor: Node<*>? = destination
        while (ancestor != null) {
            if (ancestor === child) parent.fail("reparent", "Cannot create a hierarchy cycle")
            ancestor = ancestor.state("reparent").parent
        }
        if ((!childState.autoName && childState.name in destinationState.children) ||
            parentState.owner !== destinationState.owner
        ) {
            parent.fail("reparent", "Invalid destination")
        }
        val sameTree = parentState.entered && destinationState.entered
        if (!sameTree) {
            val failures = CleanupFailures()
            failures.attempt { child.nodeExitTree() }
            failures.attempt { parentState.owner.unregisterSubtree(child) }
            child.detachFromParent()
            destination.addChild(child)
            failures.rethrow()
        } else {
            child.detachFromParent()
            attach(destination, child)
        }
    }
}
