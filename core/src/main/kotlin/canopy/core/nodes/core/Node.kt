package anchors.framework.nodes.core

import anchors.framework.data.assets.AssetsManager
import anchors.framework.input.InputEvent
import anchors.framework.input.InputSystem
import anchors.framework.managers.ManagersRegistry
import anchors.framework.nodes.SceneManager
import com.badlogic.gdx.math.Vector2
import ktx.math.plus
import kotlin.reflect.KClass

/**
 * DSL marker for Scene DSL usage.
 */
@DslMarker
annotation class NodeDSL

/**
 * Base node class for 2D scene graph system.
 *
 * @param N Type of the node (for generics / DSL chaining)
 */
@NodeDSL
@Suppress("UNCHECKED_CAST")
abstract class Node<N : Node<N>> protected constructor(
    // ===============================
    //            CORE PROPERTIES
    // ===============================
    /** Node name (unique among siblings) */
    val name: String,
    /** Optional behavior script attached to the node */
    script: (node: N) -> Behavior<N>?,
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
    /** Reference to the scene manager (set automatically on init) */
    protected val sceneManager: SceneManager by lazy { ManagersRegistry.get(SceneManager::class) }
    val inputManager: InputSystem by lazy {
        check(sceneManager.hasSystem(InputSystem::class)) {
            """

            [NODE]
            No Input System Found!

            To fix it: register it into a Scene Manager!

            """.trimIndent()
        }
        sceneManager.getSystem(InputSystem::class)
    }
    protected val assetsManager: AssetsManager by lazy { ManagersRegistry.get(AssetsManager::class) }

    /** Whether this node is a prefab (not active until instantiated) */
    private var isPrefab: Boolean = false

    /** Behavior script instance */
    private val script: Behavior<N>? = script(this as N)

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
            """

            [NODE]
            You're trying to create nodes without a Scene Manager!
            This will cause critical errors along the way!

            To fix it: Register the Scene Manager on 'ManagersRegistry'.

            """.trimIndent()
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
        check(child.name !in children) { "Child with name '${child.name}' already exists" }
        _children[child.name] = child
        child._parent = this
        sceneManager.registerSubtree(child)
    }

    /** Adds a child node at runtime */
    fun addChild(child: Node<*>) {
        check(child.parent == null) { "Node '${child.name}' already has a parent!" }
        addChildInternal(child)

        if (child.isPrefab) return

        // Lifecycle setup for runtime node
        child.enterTree()
        child.nodeReady()
    }

    /** Removes a child node */
    fun removeChild(child: Node<*>) {
        check(child.parent == this) { "Node '${child.name}' is not a child of '$name'!" }

        // Lifecycle teardown
        child.exitTree()
        _children.remove(child.name)
        child._parent = null

        sceneManager.unregisterSubtree(this)

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
                        "No child '$part' under '${current?.name}' for path '$path'",
                    )
                }
            }
        }

        return current as? T ?: throw IllegalArgumentException(
            "Node at path '$path' is not of expected type",
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
    fun reparent(
        child: Node<*>,
        newParent: Node<*>,
    ) {
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
        enterTree() // top-down attach
        nodeReady() // bottom-up initialization
    }

    // ===============================
    //        LIFECYCLE METHODS
    // ===============================

    /** Called after the node and its children are fully initialized */
    open fun nodeReady() {
        children.values.forEach { it.nodeReady() }
        script?.onReady()
    }

    /** Called when node enters the tree */
    open fun enterTree() {
        // Update initial groups
        groups.forEach { sceneManager.addToGroup(it, this) }
        // Traverse tree
        script?.onEnterTree()
        children.values.forEach { it.enterTree() }
    }

    /** Called when node exits the tree */
    open fun exitTree() {
        children.values.forEach { it.exitTree() }
        script?.onExitTree()
    }

    // ===============================
    //          UPDATES
    // ===============================

    /** Called every frame */
    open fun nodeUpdate(delta: Float) {
        children.values.forEach { it.nodeUpdate(delta) }
        script?.onUpdate(delta)
    }

    /** Called every physics tick */
    open fun nodePhysicsUpdate(delta: Float) {
        children.values.forEach { it.nodePhysicsUpdate(delta) }
        script?.onPhysicsUpdate(delta)
    }

    // ===============================
    //           INPUT
    // ===============================

    /** Handles input events and propagates to children */
    open fun nodeInput(
        event: InputEvent,
        delta: Float = 0F,
    ) {
        if (event.isHandled) return
        script?.onInput(event, delta)
        if (event.isHandled) return
        // Propagate ito children
        children.values.forEach { it.nodeInput(event, delta) }
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
