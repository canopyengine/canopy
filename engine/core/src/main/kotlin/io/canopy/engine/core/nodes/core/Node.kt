package io.canopy.engine.core.nodes.core

import kotlin.reflect.KClass
import com.badlogic.gdx.math.Vector2
import io.canopy.engine.core.log.logger
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import ktx.math.plus

/**
 * Base node class for 2D scene graph systems.
 *
 * See more [here](https://github.com/canopyengine/canopy-docs/blob/main/docs/manuals/core/node-system.md).
 *
 * @param N Type of the node (for generics / DSL chaining)
 */
@Suppress("UNCHECKED_CAST")
abstract class Node<N : Node<N>> protected constructor(
    // ===============================
    //            CORE PROPERTIES
    // ===============================
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
    // ===============================
    //        INTERNAL PROPERTIES
    // ===============================

    private val logger = logger<Node<N>>()

    // ========== MANAGER REFERENCES ================= //

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

    // ===============================
    //      GLOBAL TRANSFORMS
    // ===============================

    /** Global position (includes parent transforms) */
    val globalPosition: Vector2
        get() = position + (parent?.globalPosition ?: Vector2.Zero)

    /** Global scale (includes parent transforms) */
    val globalScale: Vector2
        get() = scale + (parent?.globalScale ?: Vector2.Zero)

    /** Global rotation (includes parent rotation) */
    val globalRotation: Float
        get() = rotation + (parent?.globalRotation ?: 0f)

    // ===============================
    //           DSL SUPPORT
    // ===============================
    companion object {
        /** Tracks the current parent for building the tree via DSL */
        private val currentParent = ThreadLocal.withInitial<Node<*>?> { null }
    }

    init {
        check(ManagersRegistry.has(SceneManager::class)) {
            logger.error {
                """

            [NODE]
            You're trying to create nodes without a Scene Manager!
            This will cause critical errors along the way!

            To fix it: Register the Scene Manager on 'ManagersRegistry'.

                """.trimIndent()
            }
        }

        // Attach to current DSL parent if exists
        currentParent.get()?.addChildInternal(this)

        val oldParent = currentParent.get()
        currentParent.set(this)
        block(this as N)
        currentParent.set(oldParent)
    }

    // ===============================
    //          CHILD MANAGEMENT
    // ===============================
    private fun addChildInternal(child: Node<*>) {
        check(child.name !in children) { logger.error { "Child with name '${child.name}' already exists" } }
        _children[child.name] = child
        child._parent = this
        sceneManager.registerSubtree(child)
    }

    /** Adds a child node at runtime */
    fun addChild(child: Node<*>) {
        check(child.parent == null) { logger.error { "Node '${child.name}' already has a parent!" } }
        addChildInternal(child)

        if (child.isPrefab) return

        // Lifecycle setup for runtime node
        child.nodeEnterTree()
        child.nodeReady()
    }

    /** Removes a child node */
    fun removeChild(child: Node<*>) {
        check(child.parent == this) { logger.error { "Node '${child.name}' is not a child of '$name'!" } }

        // Lifecycle teardown
        child.nodeExitTree()
        _children.remove(child.name)
        child._parent = null

        sceneManager.unregisterSubtree(child)

        // CLEANUP
        child.children.values
            .toList()
            .forEach { child.removeChild(it) }
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

        // Node is direct child -> get it
        if (parts.size == 1) return this.children[parts[0]] as T

        var current: Node<*>? =
            when (parts.first()) {
                "$" -> sceneManager.currScene
                "", "." -> this
                else -> this
            }
        val searchParts =
            if (parts.first() in listOf("$", ".") || path.startsWith("/")) {
                parts.drop(1)
            } else {
                parts
            }

        // Skip the first part (we already processed it)
        for (part in searchParts) {
            when (part) {
                "", "." -> { /* stay on current */ }

                ".." -> {
                    current = current?.parent ?: throw IllegalArgumentException("No parent for path: $path")
                }

                else -> {
                    val child = current?.children[part]
                    current = child ?: throw IllegalArgumentException(
                        "No child '$part' under '${current?.name}' for path '$path'"
                    )
                }
            }
        }

        return current as? T ?: throw IllegalArgumentException(
            "Node at path '$path' is not of expected type"
        )
    }

    /** Marks this node as a prefab (not active until instantiated) */
    fun asPrefab(): N {
        isPrefab = true
        return this as N
    }

    /** Self-remove from parent */
    fun queueFree() {
        parent?.removeChild(this)
    }

    /** Reparent a child to another node */
    fun reparent(child: Node<*>, newParent: Node<*>) {
        removeChild(child)
        newParent.addChild(child)
    }

    /** Checks if any child is of a given type */
    fun hasChildType(type: KClass<out Node<*>>) = children.values.any { it::class == type }

    // ===============================
    //         GROUP MANAGEMENT
    // ===============================

    /** Checks if the node is in a group */
    fun inGroup(group: String) = group in groups

    /** Adds the node to a group */
    fun addGroup(group: String) = groups.add(group).also { sceneManager.addToGroup(group, this) }

    /** Removes the node from a group */
    fun removeGroup(group: String) = groups.remove(group).also { sceneManager.removeFromGroup(group, this) }

    // ===============================
    //       SCENE TREE BUILDING
    // ===============================

    /** Builds the tree and calls lifecycle methods */
    fun buildTree() {
        nodeEnterTree() // top-down attach
        nodeReady() // bottom-up initialization
    }

    // ===============================
    //        LIFECYCLE METHODS
    // ===============================

    /** Called after the node and its children are fully initialized */
    open fun nodeReady() {
        children.values.forEach { it.nodeReady() }
        behavior?.onReady()
    }

    /** Called when node enters the tree */
    open fun nodeEnterTree() {
        // Update initial groups
        groups.forEach { sceneManager.addToGroup(it, this) }
        // Traverse tree
        behavior?.onEnterTree()
        children.values.forEach { it.nodeEnterTree() }
    }

    /** Called when node exits the tree */
    open fun nodeExitTree() {
        children.values.forEach { it.nodeExitTree() }
        behavior?.onExitTree()
    }

    // ===============================
    //          UPDATES
    // ===============================

    /** Called every frame */
    open fun nodeUpdate(delta: Float) {
        children.values.forEach { it.nodeUpdate(delta) }
        behavior?.onUpdate(delta)
    }

    /** Called every physics tick */
    open fun nodePhysicsUpdate(delta: Float) {
        children.values.forEach { it.nodePhysicsUpdate(delta) }
        behavior?.onPhysicsUpdate(delta)
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

/*
* Utility function to automatically replace the scene root with this node.
* Useful for cleaner DSL scene building without needing to reference the scene manager directly.
*/
fun Node<*>.asSceneRoot(): Node<*> {
    val sceneManager = ManagersRegistry.get(SceneManager::class)
    sceneManager.currScene = this
    return this
}
