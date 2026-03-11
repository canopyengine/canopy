package io.canopy.engine.core.nodes

import kotlin.reflect.KClass
import io.canopy.engine.core.flow.Context
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.managers.manager
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
 * - `nodeReady()` runs `create()` and the user-provided DSL [block] exactly once
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
    /**
     * Node DSL block used to configure/build the node subtree.
     * Executed once during [nodeReady].
     */
    private val block: N.() -> Unit = {},
) {

    /* ============================================================
     * Identity
     * ============================================================ */

    private var _name = name

    /**
     * Node name. Renaming updates:
     * - the parent’s children map key
     * - this node’s [path] and all descendant paths
     */
    var name
        get() = _name
        set(value) = rename(value)

    /**
     * Local group memberships. Groups are also mirrored into the [SceneManager] registry
     * when the node enters the tree.
     */
    val groups: MutableList<String> = mutableListOf()

    /** Stable engine logger for node operations (routable to engine logs). */
    private val log = EngineLogs.node

    /** Scene manager instance (resolved lazily from ManagersRegistry). */
    protected val sceneManager: SceneManager by lazyManager()

    /** Prefab nodes do not run lifecycle automatically when attached. */
    private var isPrefab: Boolean = false

    /** Optional behavior instance attached to this node. */
    internal var behavior: Behavior<N>? = null

    /* ============================================================
     * Tree structure
     * ============================================================ */

    private var _parent: Node<*>? = null
    val parent get() = _parent

    private val _children: MutableMap<String, Node<*>> = mutableMapOf()
    val children: Map<String, Node<*>> get() = _children

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
    private var _path: String = name
    val path: String get() = _path

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
        // If we are inside a DSL build block, auto-attach to the current parent.
        // This is intentionally done in init to allow nested construction:
        //
        // parent.nodeReady() sets currentParent = parent
        // child init reads it and attaches itself to parent
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
     * - "../" goes to parent (skipping [ContextScopeNode] wrappers)
     * - Paths may omit ContextScopeNode segments; lookup searches through context wrappers
     *   to make DSL context blocks transparent.
     *
     * Examples:
     * - `$/Player/Weapon`
     * - `./UI/HUD`
     * - `../Camera`
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Node<T>> getNode(path: String): T {
        val parts = path.split("/")

        var current: Node<*>? = when (parts.firstOrNull()) {
            "$" -> sceneManager.currScene
            "", "." -> this
            else -> this
        }

        val searchParts =
            if (parts.firstOrNull() in listOf("$", ".") || path.startsWith("/")) {
                parts.drop(1)
            } else {
                parts
            }

        // Walk up skipping ContextScopeNode wrappers (they are implementation details).
        fun Node<*>.visibleParent(): Node<*>? {
            var p = this.parent
            while (p is Context) p = p.parent
            return p
        }

        // Find child, treating ContextScopeNode as transparent containers.
        fun Node<*>.findChildSkippingContext(name: String): Node<*>? {
            // 1) direct child wins
            this.children[name]?.let { return it }

            // 2) otherwise, search one level into context scopes
            for (c in this.children.values) {
                if (c is Context) {
                    c.children[name]?.let { return it }

                    // 3) also allow nested context scopes (common with nested context blocks)
                    for (nested in c.children.values) {
                        if (nested is Context) {
                            nested.children[name]?.let { return it }
                        }
                    }
                }
            }

            return null
        }

        for (part in searchParts) {
            when (part) {
                "", "." -> Unit
                ".." -> {
                    current = current?.visibleParent()
                        ?: throw IllegalArgumentException("No parent for path: $path")
                }
                else -> {
                    val cur = current ?: throw IllegalArgumentException("Null node while resolving path: $path")
                    val next = cur.findChildSkippingContext(part)
                        ?: throw IllegalArgumentException("No child '$part' under '${cur.name}' for path '$path'")
                    current = next
                }
            }
        }

        return current as T
    }

    /** Kotlin shorthand: `node["Player/Weapon"]` */
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

    /** Removes this node from its parent. */
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
     * - enterTree
     * - ready (which executes create() + DSL block once)
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
     * Called when the node and its subtree should finish initialization.
     *
     * This method:
     * - runs `create()` + the DSL [block] once (guarded by [built])
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
     * Called when the node enters the tree.
     *
     * Order:
     * - register groups in SceneManager
     * - behavior.onEnterTree()
     * - recurse into children
     */
    open fun nodeEnterTree() {
        LogContext.with("nodePath" to path) {
            log.trace("event" to "node.enter_tree") { "nodeEnterTree()" }
        }

        // Avoid executing DSL/build twice (e.g., if nodeReady is triggered again).
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
