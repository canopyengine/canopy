package io.canopy.engine.core.nodes

import kotlin.reflect.KClass
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.input.InputEvent
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext

/**
 * Base node class for a 2D scene graph.
 *
 * Design overview:
 * - Nodes form a tree (parent/children) and expose a small DSL for building that tree.
 * - Nodes can optionally have a [Behavior] attached (script-like logic).
 * - The [SceneManager] uses the node tree to:
 *   - drive lifecycle callbacks (enter/ready/exit)
 *   - run updates (frame + physics)
 *   - register nodes into systems and groups
 *
 * Construction vs initialization:
 * - `init { ... }` attaches this node to the current DSL parent (if any).
 * - `nodeEnterTree()` runs `create()` and the user-provided DSL [block] exactly once
 *   to build children and configure the node.
 *
 * Generic type parameter:
 * - `N : Node<N>` enables DSL blocks to have the concrete node type as receiver.
 */
@CanopyDsl
@Suppress("UNCHECKED_CAST")
abstract class Node<N : Node<N>> protected constructor(
    /** Node name (expected to be unique among siblings). */
    name: String,
    protected open val skipOnSearch: Boolean = false,
    /**
     * Node DSL block used to configure/build the node subtree.
     * Executed once during [nodeEnterTree].
     */
    private val block: N.() -> Unit = {},
) {

    /* ============================================================
     * Identity
     * ============================================================ */

    /**
     * Node name. Renaming updates:
     * - the parent’s children map key
     * - this node’s [path] and all descendant paths
     */
    private var _name = name
    var name
        get() = _name
        set(value) = rename(value)

    /* ============================================================
     * Groups
     * ============================================================ */

    /**
     * Local group memberships for this node.
     *
     * Stored as a set to avoid duplicates.
     * Publicly exposed as read-only.
     */
    private val _groups = linkedSetOf<String>()
    val groups: Set<String> get() = _groups

    /**
     * Adds this node to a group.
     *
     * If the node is already inside the built tree, also mirrors the change
     * into the SceneManager group registry.
     */
    fun addGroup(group: String) {
        if (_groups.add(group) && built) {
            sceneManager.addToGroup(group, this)
        }
    }

    /**
     * Removes this node from a group.
     *
     * If the node is already inside the built tree, also mirrors the change
     * into the SceneManager group registry.
     */
    fun removeGroup(group: String) {
        if (_groups.remove(group) && built) {
            sceneManager.removeFromGroup(group, this)
        }
    }

    /**
     * Batch-updates groups and applies only the actual diff to SceneManager.
     *
     * Example:
     * ```kotlin
     * updateGroups {
     *     remove("enemy")
     *     add("player")
     *     add("controllable")
     * }
     * ```
     */
    fun updateGroups(block: MutableSet<String>.() -> Unit) {
        _groups.block()

        if (!built) return

        sceneManager.updateGroups(this)
    }

    /** Optional behavior instance attached to this node. */
    internal var behavior: Behavior<N>? = null

    /* ============================================================
     * Managers
     * ============================================================ */

    /** Scene manager instance (resolved lazily from ManagersRegistry). */
    protected val sceneManager: SceneManager by lazyManager()

    /* ============================================================
     * Other
     * ============================================================ */

    /**
     * Full path from the tree root.
     *
     * Format: `/Root/Player/Weapon`
     *
     * Notes:
     * - Root nodes have paths like `/RootName`
     * - Paths are recomputed when:
     *   - a node is attached/detached
     *   - a node is renamed
     */
    private var _path: String = "/$name"
    val path: String get() = _path

    /** Stable engine logger for node operations (routable to engine logs). */
    private val log = EngineLogs.node

    /** Prefab nodes do not run lifecycle automatically when attached. */
    private var isPrefab: Boolean = false

    /* ============================================================
     * Tree structure
     * ============================================================ */

    /**
     * References this node's parent
     */
    private var _parent: Node<*>? = null
    val parent get() = _parent

    /**
     * References this node's children
     */
    private val _children: MutableMap<String, Node<*>> = mutableMapOf()
    val children get() = _children.toMap()

    /* ============================================================
     * DSL support
     * ============================================================ */

    companion object {
        /**
         * DSL builder state: the "current parent" node.
         *
         * During `nodeReady()`, this is temporarily set to the node being built so that
         * children constructed in the DSL `block { ... }` automatically attach to it.
         */
        private val currentParent = ThreadLocal.withInitial<Node<*>?> { null }
    }

    init {
        /*
         * If we are inside a DSL build block, auto-attach to the current parent.
         * This is intentionally done in init to allow nested construction:
         */
        currentParent.get()?.addChildInternal(this)
    }

    /* ============================================================
     * Child management
     * ============================================================ */

    /**
     * Internal attach without lifecycle calls.
     *
     * Used by:
     * - DSL construction (children attach during init)
     * - runtime attach via [addChild] (then lifecycle is applied unless prefab)
     */
    private fun addChildInternal(child: Node<*>) {
        check(child.name !in children) {
            "Child with name '${child.name}' already exists under '${this.name}'"
        }

        _children[child.name] = child
        child._parent = this
        child.recomputePathRecursively()

        LogContext.with("nodePath" to this.path, "childPath" to child.path) {
            log.debug(
                "event" to "node.add_child_internal",
                "parent" to this@Node.name,
                "child" to child.name
            ) { "Attached child" }
        }

        // Register nodes into systems/groups indices maintained by SceneManager.
        sceneManager.registerSubtree(child)
    }

    /**
     * Attaches a child node at runtime and runs its lifecycle (unless it is a prefab).
     *
     * Lifecycle order for the attached subtree:
     * - enterTree
     * - ready
     */
    fun addChild(child: Node<*>) {
        check(child.parent == null) { "Node '${child.name}' already has a parent!" }

        addChildInternal(child)

        if (child.isPrefab) {
            LogContext.with("nodePath" to child.path) {
                log.trace("event" to "node.add_child.prefab") { "Child is prefab; skipping lifecycle" }
            }
            return
        }

        LogContext.with("nodePath" to child.path) {
            log.trace("event" to "node.lifecycle.enter_tree") { "enterTree()" }
            child.nodeEnterTree()
            log.trace("event" to "node.lifecycle.ready") { "ready()" }
            child.nodeReady()
        }
    }

    /** DSL: `+childNode` inside a node scope. */
    operator fun Node<*>.unaryPlus() = addChild(this)

    /** DSL: `node += child` */
    operator fun plusAssign(child: Node<*>) = addChild(child)

    /**
     * Removes a child node and runs teardown lifecycle.
     *
     * Order:
     * - exitTree on subtree
     * - detach from parent
     * - unregister subtree from SceneManager
     */
    fun removeChild(child: Node<*>) {
        check(child.parent == this) { "Node '${child.name}' is not a child of '$name'!" }

        LogContext.with("nodePath" to this.path, "childPath" to child.path) {
            log.debug("event" to "node.remove_child") { "Removing child" }
        }

        LogContext.with("nodePath" to child.path) {
            log.trace("event" to "node.lifecycle.exit_tree") { "exitTree()" }
            child.nodeExitTree()
        }

        _children.remove(child.name)
        child._parent = null
        child.recomputePathRecursively()

        sceneManager.unregisterSubtree(child)

        // Remove remaining descendants by detaching them from the child.
        child.children.values.toList().forEach { child.removeChild(it) }
    }

    /** DSL: `-childNode` */
    operator fun Node<*>.unaryMinus() = removeChild(this)

    /** DSL: `node -= child` */
    operator fun minusAssign(child: Node<*>) = removeChild(child)

    /** Removes a child node by path. */
    fun removeChild(path: String) {
        val child = getNode(path)
        removeChild(child)
    }

    /* ============================================================
     * Node lookup
     * ============================================================ */

    /**
     * Resolves a node by path.
     *
     * Path rules:
     * - "$/..." resolves from the current scene root
     * - "./..." resolves from this node
     * - "../" goes to parent (skipping [Context] wrappers)
     * - Paths may omit ContextScopeNode segments; lookup searches through context wrappers
     *   to make DSL context blocks transparent.
     *
     * Examples:
     * - `$/Player/Weapon`
     * - `./UI/HUD`
     * - `../Camera`
     */
    fun <T : Node<T>> getNode(path: String): T {
        val parts = path.split("/")
        val firstPart = parts.firstOrNull()

        var current: Node<*>? =
            if (firstPart == "$") sceneManager.currScene else this

        val searchParts = when {
            path.startsWith("/") -> parts.drop(1)
            firstPart == "$" || firstPart == "." -> parts.drop(1)
            else -> parts
        }

        /**
         * Skips wrapper nodes(nodes with skipOnSearch = false) and finds closest parent node
         */
        fun Node<*>.findVisibleParent(): Node<*>? {
            var p = parent
            while (p?.skipOnSearch == true) p = p.parent
            return p
        }

        fun Node<*>.resolveSearchHit(): Node<*>? {
            if (!skipOnSearch) return this

            for (child in children.values) {
                child.resolveSearchHit()?.let { return it }
            }

            return null
        }

        /**
         * Skips wrapper nodes to find searchable nodes
         */
        fun Node<*>.findVisibleChild(name: String): Node<*>? {
            children[name]?.resolveSearchHit()?.let { return it }

            for (child in children.values) {
                if (child.skipOnSearch) {
                    child.findVisibleChild(name)?.let { return it }
                }
            }

            return null
        }

        for (part in searchParts) {
            current = when (part) {
                // Start here
                "", "." -> current
                // Go back one node
                ".." -> current?.findVisibleParent()
                    ?: throw IllegalArgumentException("No parent for path: $path")
                // Paths (ex: [a,b,c])
                else -> {
                    val node = current
                        ?: throw IllegalArgumentException("Null node while resolving path: $path")

                    node.findVisibleChild(part)
                        ?: throw IllegalArgumentException(
                            "No child '$part' under '${node.name}' for path '$path'"
                        )
                }
            }
        }

        return current as T
    }

    /**
     * Kotlin shorthand: `node["Player/Weapon"]`
     */
    inline operator fun <reified T : Node<T>> get(path: String): T = getNode(path)

    /* ============================================================
     * Prefab / instancing
     * ============================================================ */

    /**
     * Marks this node as a prefab.
     *
     * Prefabs are attachable as children but do not automatically run lifecycle.
     * Intended for templates that are instantiated/activated later.
     */
    fun asPrefab(): N {
        isPrefab = true
        LogContext.with("nodePath" to path) {
            log.trace("event" to "node.prefab") { "Marked as prefab" }
        }
        return this as N
    }

    /**
     * Removes this node from its parent.
     *
     * TODO: Improve this method so that it properly frees the node/queues it for removal
     */
    fun queueFree() {
        LogContext.with("nodePath" to path) {
            log.debug("event" to "node.queue_free") { "Queue free" }
        }
        parent?.removeChild(this)
    }

    /**
     * Moves an existing child from this parent to [newParent].
     */
    fun reparent(child: Node<*>, newParent: Node<*>) {
        LogContext.with("childPath" to child.path, "fromParent" to this.path, "toParent" to newParent.path) {
            log.info("event" to "node.reparent") { "Reparenting child" }
        }
        removeChild(child)
        newParent.addChild(child)
    }

    fun hasChildType(type: KClass<out Node<*>>) = children.values.any { it::class == type }

    /* ============================================================
     * Tree building
     * ============================================================ */

    /**
     * Builds this node as a root/subtree:
     * - enterTree (which executes create() + DSL block once)
     * - ready
     */
    fun buildTree() {
        LogContext.with("nodePath" to path) {
            log.debug("event" to "node.build_tree") { "Building tree" }
        }
        nodeEnterTree()
        nodeReady()
    }

    /* ============================================================
     * Lifecycle hooks
     * ============================================================ */

    /**
     * Override for predefined node configuration.
     *
     * Example uses:
     * - internal child structure
     * - default components/behavior
     * - setting initial transforms
     */
    open fun create() {}

    private var built = false

    /**
     * Called when the node enters the tree.
     *
     * Order:
     * - runs `create()` + the DSL [block] once (guarded by [built])
     * - register groups in SceneManager
     * - behavior.onEnterTree()
     * - recurse into children
     */
    open fun nodeEnterTree() {
        LogContext.with("nodePath" to path) {
            log.trace("event" to "node.enter_tree") { "nodeEnterTree()" }
        }

        // Avoid executing DSL/build twice (e.g., if nodeEnterTree is triggered again).
        if (built) return
        built = true

        // Build subtree via DSL after full construction.
        val oldParent = currentParent.get()
        currentParent.set(this)

        try {
            create()
            block(this as N)
        } finally {
            currentParent.set(oldParent)
            LogContext.with("nodePath" to path) {
                log.trace("event" to "node.constructed") { "Node constructed" }
            }
        }

        groups.forEach { sceneManager.addToGroup(it, this) }
        behavior?.let { runBehavior("enter_tree") { it.onEnterTree() } }
        children.values.forEach { it.nodeEnterTree() }
    }

    /**
     * Called when the node and its subtree should finish initialization.
     *
     * This method:
     * - then recurses into children (so the entire subtree becomes ready)
     * - then fires behavior.onReady()
     */
    open fun nodeReady() {
        LogContext.with("nodePath" to path) {
            log.trace("event" to "node.ready") { "nodeReady()" }
        }

        // Children were attached during their init; now recurse.
        children.values.forEach { it.nodeReady() }
        behavior?.let { runBehavior("ready") { it.onReady() } }
    }

    /**
     * Called when the node exits the tree.
     *
     * Order:
     * - recurse into children
     * - behavior.onExitTree()
     */
    open fun nodeExitTree() {
        LogContext.with("nodePath" to path) {
            log.trace("event" to "node.exit_tree") { "nodeExitTree()" }
        }
        children.values.forEach { it.nodeExitTree() }
        behavior?.let { runBehavior("exit_tree") { it.onExitTree() } }
    }

    /* ============================================================
     * Updates
     * ============================================================ */

    open fun nodeUpdate(delta: Float) {
        LogContext.with("nodePath" to path, "delta" to delta) {
            log.trace("event" to "node.update") { "nodeUpdate()" }
        }
        children.values.forEach { it.nodeUpdate(delta) }
        behavior?.let { runBehavior("update") { it.onUpdate(delta) } }
    }

    open fun nodePhysicsUpdate(delta: Float) {
        LogContext.with("nodePath" to path, "delta" to delta) {
            log.trace("event" to "node.physics_update") { "nodePhysicsUpdate()" }
        }
        children.values.forEach { it.nodePhysicsUpdate(delta) }
        behavior?.let { runBehavior("physics_update") { it.onPhysicsUpdate(delta) } }
    }

    /* ============================================================
     * Input
     * ============================================================ */

    open fun nodeInput(event: InputEvent) {
        children.values.forEach { it.nodeInput(event) }
        behavior?.let { runBehavior("input") { it.onInput(event) } }
    }

    /* ============================================================
     * Internals
     * ============================================================ */

    /**
     * Executes a behavior callback and logs/rethrows exceptions with useful context.
     * This is intentionally fail-fast: behavior errors should be surfaced quickly.
     */
    private inline fun runBehavior(phase: String, delta: Float? = null, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            val fields = buildMap {
                put("event", "behavior.error")
                put("phase", phase)
                put("nodePath", path)
                put("behavior", behavior?.javaClass?.name)
                if (delta != null) put("delta", delta)
            }.map { Pair(it.key, it.value) }

            EngineLogs.node.error(t = t, *fields.toTypedArray()) { "Behavior threw during $phase" }
            throw t
        }
    }

    /** Recomputes this node path and all descendant paths. */
    private fun recomputePathRecursively() {
        _path = parent?.let { "${it.path}/$name" } ?: "/$name"
        _children.values.forEach { it.recomputePathRecursively() }
    }

    /**
     * Renames this node and updates the parent index + paths.
     */
    private fun rename(newName: String) {
        if (newName == name) return

        val p = parent
        if (p != null) {
            require(!p._children.containsKey(newName)) {
                "Sibling with name '$newName' already exists under parent '${p.path}'."
            }

            p._children.remove(name)
            p._children[newName] = this
        }

        _name = newName
        recomputePathRecursively()
    }

    /* ============================================================
     * DSL helpers
     * ============================================================ */

    infix fun child(node: Node<*>) = addChild(node)

    // fun groups(vararg groups: String) = apply { groups.forEach { addGroup(it) } }

    fun <T : Node<T>> patch(path: String, handler: T.() -> Unit) = getNode<T>(path).apply(handler)

    /* ------------------------------------------------------------------
     * Top-level DSL helpers
     * ------------------------------------------------------------------ */

    /** `parent + child` attaches [child] to [parent] and returns [parent]. */
    operator fun Node<*>.plus(node: Node<*>): Node<*> {
        addChild(node)
        return this
    }

    /**
     * Sets this node as the active scene root in the global [SceneManager].
     *
     * This triggers SceneManager scene replacement logic (unregister old scene, register new scene).
     */
    fun asSceneRoot(): Node<*> {
        val sceneManager = manager<SceneManager>()
        sceneManager.currScene = this

        LogContext.with("nodePath" to this.path) {
            EngineLogs.subsystem("scene").info("event" to "scene.set_root") { "Set as scene root" }
        }

        return this
    }
}
