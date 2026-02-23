package io.canopy.engine.core.nodes.core

import kotlin.reflect.KClass
import com.badlogic.gdx.math.Vector2
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.engine.EngineLogs
import ktx.math.plus

/**
 * Base node class for 2D scene graph systems.
 */
@Suppress("UNCHECKED_CAST")
abstract class Node<N : Node<N>> protected constructor(
    /** Node name (unique among siblings) */
    val name: String,
    /** Optional behavior script attached to the node */
    behavior: (node: N) -> Behavior<N>?,
    /** Local position in 2D space */
    open var position: Vector2 = Vector2.Zero,
    /** Local scale in 2D space */
    open var scale: Vector2 = Vector2(1f, 1f),
    /** Local rotation in radians */
    open var rotation: Float = 0f,
    val groups: MutableList<String> = mutableListOf(),
    /** Optional DSL block for building children inline */
    block: N.() -> Unit,
) {
    // Use a stable engine subsystem logger so it routes to engine logs.
    private val log = EngineLogs.node

    /** Reference to the scene manager (set automatically on init) */
    protected val sceneManager: SceneManager by lazy { ManagersRegistry.get(SceneManager::class) }

    /** Reference to the injection manager (for prop injection) */
    protected val injectionManager: InjectionManager by lazy { ManagersRegistry.get(InjectionManager::class) }

    /** Whether this node is a prefab (not active until instantiated) */
    private var isPrefab: Boolean = false

    /** Behavior script instance */
    private val behavior: Behavior<N>? = behavior(this as N)

    /** Parent node reference */
    private var _parent: Node<*>? = null
    val parent get() = _parent

    /** Child nodes mapped by name */
    private val _children: MutableMap<String, Node<*>> = mutableMapOf()
    val children: Map<String, Node<*>> get() = _children

    /**
     * Full path from scene root (or from this node if detached).
     * Format: "/Root/Player/Weapon"
     */
    private var _path: String = name
    val path: String get() = _path

    // ===============================
    //      GLOBAL TRANSFORMS
    // ===============================

    val globalPosition: Vector2
        get() = position + (parent?.globalPosition ?: Vector2.Zero)

    val globalScale: Vector2
        get() = scale + (parent?.globalScale ?: Vector2.Zero)

    val globalRotation: Float
        get() = rotation + (parent?.globalRotation ?: 0f)

    // ===============================
    //           DSL SUPPORT
    // ===============================
    companion object {
        private val currentParent = ThreadLocal.withInitial<Node<*>?> { null }
    }

    init {
        // Fail fast with a real message; do not log in a require/check lambda.
        check(ManagersRegistry.has(SceneManager::class)) {
            """
            [NODE]
            You're trying to create nodes without a Scene Manager!
            This will cause critical errors along the way!
            To fix it: Register the Scene Manager on 'ManagersRegistry'.
            """.trimIndent()
        }

        // Attach to current DSL parent if exists
        currentParent.get()?.addChildInternal(this)

        // Build subtree through DSL
        val oldParent = currentParent.get()
        currentParent.set(this)
        block(this as N)
        currentParent.set(oldParent)

        // Optional: only log construction at TRACE to avoid noise
        LogContext.with("nodePath" to path) {
            log.trace(fields = mapOf("event" to "node.constructed")) { "Node constructed" }
        }
    }

    // ===============================
    //          CHILD MANAGEMENT
    // ===============================
    private fun addChildInternal(child: Node<*>) {
        check(child.name !in children) {
            "Child with name '${child.name}' already exists under '${this.name}'"
        }

        _children[child.name] = child
        child._parent = this
        child.recomputePathRecursively()

        LogContext.with(
            "nodePath" to this.path,
            "childPath" to child.path
        ) {
            log.debug(
                fields = mapOf(
                    "event" to "node.add_child_internal",
                    "parent" to this@Node.name,
                    "child" to child.name
                )
            ) { "Attached child" }
        }

        sceneManager.registerSubtree(child)
    }

    /** Adds a child node at runtime */
    fun addChild(child: Node<*>) {
        check(child.parent == null) { "Node '${child.name}' already has a parent!" }

        addChildInternal(child)

        if (child.isPrefab) {
            LogContext.with("nodePath" to child.path) {
                log.trace(fields = mapOf("event" to "node.add_child.prefab")) { "Child is prefab; skipping lifecycle" }
            }
            return
        }

        LogContext.with("nodePath" to child.path) {
            log.trace(fields = mapOf("event" to "node.lifecycle.enter_tree")) { "enterTree()" }
            child.nodeEnterTree()
            log.trace(fields = mapOf("event" to "node.lifecycle.ready")) { "ready()" }
            child.nodeReady()
        }
    }

    /** Removes a child node */
    fun removeChild(child: Node<*>) {
        check(child.parent == this) { "Node '${child.name}' is not a child of '$name'!" }

        LogContext.with(
            "nodePath" to this.path,
            "childPath" to child.path
        ) {
            log.debug(fields = mapOf("event" to "node.remove_child")) { "Removing child" }
        }

        // Lifecycle teardown
        LogContext.with("nodePath" to child.path) {
            log.trace(fields = mapOf("event" to "node.lifecycle.exit_tree")) { "exitTree()" }
            child.nodeExitTree()
        }

        _children.remove(child.name)
        child._parent = null
        child.recomputePathRecursively()

        sceneManager.unregisterSubtree(child)

        // Cleanup: remove grandchildren
        child.children.values.toList().forEach { child.removeChild(it) }
    }

    /** Removes a child node by path */
    fun removeChild(path: String) {
        val child = getNode(path)
        removeChild(child)
    }

    /** Returns a child node by relative path (e.g., "parent/child") */
    @Suppress("UNCHECKED_CAST")
    fun <T : Node<T>> getNode(path: String): T {
        val parts = path.split("/")

        if (parts.size == 1) return this.children[parts[0]] as T

        var current: Node<*>? =
            when (parts.first()) {
                "$" -> sceneManager.currScene
                "", "." -> this
                else -> this
            }

        val searchParts =
            if (parts.first() in listOf("$", ".") || path.startsWith("/")) parts.drop(1) else parts

        for (part in searchParts) {
            when (part) {
                "", "." -> {}
                ".." -> current = current?.parent ?: throw IllegalArgumentException("No parent for path: $path")
                else -> {
                    val child = current?.children[part]
                    current = child ?: throw IllegalArgumentException(
                        "No child '$part' under '${current?.name}' for path '$path'"
                    )
                }
            }
        }

        return current as? T ?: throw IllegalArgumentException("Node at path '$path' is not of expected type")
    }

    /** Marks this node as a prefab (not active until instantiated) */
    fun asPrefab(): N {
        isPrefab = true
        LogContext.with("nodePath" to path) {
            log.trace(fields = mapOf("event" to "node.prefab")) { "Marked as prefab" }
        }
        return this as N
    }

    /** Self-remove from parent */
    fun queueFree() {
        LogContext.with("nodePath" to path) {
            log.debug(fields = mapOf("event" to "node.queue_free")) { "Queue free" }
        }
        parent?.removeChild(this)
    }

    /** Reparent a child to another node */
    fun reparent(child: Node<*>, newParent: Node<*>) {
        LogContext.with(
            "childPath" to child.path,
            "fromParent" to this.path,
            "toParent" to newParent.path
        ) {
            log.info(fields = mapOf("event" to "node.reparent")) { "Reparenting child" }
        }
        removeChild(child)
        newParent.addChild(child)
    }

    fun hasChildType(type: KClass<out Node<*>>) = children.values.any { it::class == type }

    // ===============================
    //         GROUP MANAGEMENT
    // ===============================

    fun inGroup(group: String) = group in groups

    fun addGroup(group: String) = groups.add(group).also {
        LogContext.with("nodePath" to path, "group" to group) {
            log.trace(fields = mapOf("event" to "node.group.add")) { "Add group" }
        }
        sceneManager.addToGroup(group, this)
    }

    fun removeGroup(group: String) = groups.remove(group).also {
        LogContext.with("nodePath" to path, "group" to group) {
            log.trace(fields = mapOf("event" to "node.group.remove")) { "Remove group" }
        }
        sceneManager.removeFromGroup(group, this)
    }

    // ===============================
    //       SCENE TREE BUILDING
    // ===============================

    fun buildTree() {
        LogContext.with("nodePath" to path) {
            log.debug(fields = mapOf("event" to "node.build_tree")) { "Building tree" }
        }
        nodeEnterTree()
        nodeReady()
    }

    // ===============================
    //        LIFECYCLE METHODS
    // ===============================

    open fun nodeReady() {
        LogContext.with("nodePath" to path) {
            log.trace(fields = mapOf("event" to "node.ready")) { "nodeReady()" }
        }
        children.values.forEach { it.nodeReady() }
        behavior?.let { runBehavior("ready") { it.onReady() } }
    }

    open fun nodeEnterTree() {
        LogContext.with("nodePath" to path) {
            log.trace(fields = mapOf("event" to "node.enter_tree")) { "nodeEnterTree()" }
        }
        groups.forEach { sceneManager.addToGroup(it, this) }
        behavior?.let { runBehavior("enter_tree") { it.onEnterTree() } }
        children.values.forEach { it.nodeEnterTree() }
    }

    open fun nodeExitTree() {
        LogContext.with("nodePath" to path) {
            log.trace(fields = mapOf("event" to "node.exit_tree")) { "nodeExitTree()" }
        }
        children.values.forEach { it.nodeExitTree() }
        behavior?.let { runBehavior("exit_tree") { it.onExitTree() } }
    }

    // ===============================
    //          UPDATES
    // ===============================

    open fun nodeUpdate(delta: Float) {
        LogContext.with("nodePath" to path, "delta" to delta) {
            log.trace(fields = mapOf("event" to "node.update")) { "nodeUpdate()" }
        }
        children.values.forEach { it.nodeUpdate(delta) }
        behavior?.let { runBehavior("update") { it.onUpdate(delta) } }
    }

    open fun nodePhysicsUpdate(delta: Float) {
        LogContext.with("nodePath" to path, "delta" to delta) {
            log.trace(fields = mapOf("event" to "node.physics_update")) { "nodePhysicsUpdate()" }
        }
        children.values.forEach { it.nodePhysicsUpdate(delta) }
        behavior?.let { runBehavior("physics_update") { it.onPhysicsUpdate(delta) } }
    }

    // Helpers
    private inline fun runBehavior(phase: String, delta: Float? = null, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            val fields = buildMap<String, Any?> {
                put("event", "behavior.error")
                put("phase", phase)
                put("nodePath", path)
                put("behavior", behavior?.javaClass?.name)
                if (delta != null) put("delta", delta)
            }
            EngineLogs.node.error(t = t, fields = fields) { "Behavior threw during $phase" }
            throw t // rethrow so you fail fast, unless you want to swallow
        }
    }

    private fun recomputePathRecursively() {
        _path = parent?.let { "${it.path}/$name" } ?: "/$name"
        _children.values.forEach { it.recomputePathRecursively() }
    }

    infix fun child(node: Node<*>) = addChild(node)
}

operator fun Node<*>.plus(node: Node<*>): Node<*> {
    addChild(node)
    return this
}

operator fun Node<*>.unaryPlus(): Node<*> {
    parent?.addChild(this)
    return this
}

fun Node<*>.asSceneRoot(): Node<*> {
    val sceneManager = ManagersRegistry.get(SceneManager::class)
    sceneManager.currScene = this

    LogContext.with("nodePath" to this.path) {
        EngineLogs.subsystem("scene").info(fields = mapOf("event" to "scene.set_root")) { "Set as scene root" }
    }

    return this
}
