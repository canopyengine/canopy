package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import io.canopy.engine.core.flows.events.asSignal
import io.canopy.engine.core.flows.events.event
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext
import io.canopy.engine.math.Vector2

/**
 * Manages the active scene tree and drives update systems.
 *
 * Responsibilities:
 * - Own the current scene root ([currScene]) and handle scene replacement
 * - Maintain a flat lookup table of nodes by path (useful for queries/debugging)
 * - Register/unregister nodes into [TreeSystem]s based on node type
 * - Maintain named node groups for broadcasting operations (e.g. "enemies", "ui")
 * - Drive the update loop via [tick] with deterministic phase ordering
 *
 * Update flow:
 * - Physics ticks run at a fixed time step ([physicsStep]) using an accumulator
 * - Frame ticks run every frame with variable delta
 *
 * NOTE:
 * This class does not currently enforce thread-safety. Scene mutation is expected
 * to happen on the main/game thread.
 */
class SceneManager(private var physicsStep: Float = 1f / 60f, private val block: SceneManager.() -> Unit = {}) :
    Manager {

    /** Dedicated subsystem logger (routable + consistent). */
    private val log = EngineLogs.subsystem("scene")

    /**
     * Flat index of nodes keyed by their path.
     * This is updated when scenes are registered/unregistered.
     */
    private val flatTree = mutableMapOf<String, Node<*>>()

    companion object {
        /**
         * Thread-local pointer to the "current" SceneManager.
         *
         * This is typically used by builders/DSLs that want implicit access to the active
         * SceneManager during construction.
         */
        internal val currentParent = ThreadLocal.withInitial<SceneManager?> { null }
    }

    init {
        currentParent.set(this)
    }

    /* ============================================================
     * Signals / events
     * ============================================================ */

    /** Current scene size signal (e.g., for UI/layout systems). */
    val sceneSize = Vector2.Zero.asSignal()

    /** Emitted when the window/viewport is resized. */
    val onResize = event<Int, Int>()

    /** Emitted after the scene root is replaced. Payload is the new root (or null). */
    val onSceneReplaced = event<Node<*>?>()

    /* ============================================================
     * Scene state
     * ============================================================ */

    private var _currScene: Node<*>? = null

    /**
     * Active scene root. Assigning to this property replaces the scene and triggers:
     * - exit/unregister on the previous scene subtree
     * - register/build on the new scene subtree
     * - [onSceneReplaced] emission
     */
    var currScene: Node<*>?
        get() = _currScene
        set(value) = replaceScene(value)

    /** Accumulator used to determine when to run fixed-step physics ticks. */
    private var physicsAccumulator = 0f

    /* ============================================================
     * Systems
     * ============================================================ */

    /**
     * Systems grouped by update phase. Each list is kept sorted by system priority.
     */
    private val systems: MutableMap<TreeSystem.UpdatePhase, MutableList<TreeSystem>> = mutableMapOf()

    /**
     * Direct lookup by system class (useful for get/remove).
     */
    private val systemsByClass = mutableMapOf<KClass<out TreeSystem>, TreeSystem>()

    /**
     * Systems indexed by node type they care about. Used for registering/unregistering nodes.
     */
    private val systemsByNodeTypes = mutableMapOf<KClass<out Node<*>>, MutableList<TreeSystem>>()

    /* ============================================================
     * Groups
     * ============================================================ */

    /**
     * Named groups of nodes.
     * Maps a node to its list of groups
     */
    private val groupsByNode = mutableMapOf<Node<*>, MutableList<String>>()

    /**
     * Maps a group to all their nodes
     */
    private val groups = mutableMapOf<String, MutableList<Node<*>>>()

    /* ============================================================
     * Scene replacement
     * ============================================================ */

    /**
     * Replaces the active scene.
     *
     * Order:
     * 1) Exit + unregister old subtree
     * 2) Swap pointer and emit [onSceneReplaced]
     * 3) Register + build new subtree
     */
    private fun replaceScene(newScene: Node<*>?) {
        val oldScene = _currScene

        log.info(
            "event" to "scene.replace",
            "oldScene" to oldScene?.name,
            "newScene" to newScene?.name
        ) { "Replacing scene" }

        oldScene?.let { scene ->
            LogContext.with("scene" to scene.name) {
                log.debug("event" to "scene.exit_tree") { "Exiting old scene tree" }
                scene.nodeExitTree()
                unregisterSubtree(scene)
            }
        }

        _currScene = newScene
        onSceneReplaced.emit(_currScene)

        newScene?.let { scene ->
            LogContext.with("scene" to scene.name) {
                log.debug("event" to "scene.register_subtree") { "Registering new scene subtree" }
                registerSubtree(scene)

                log.debug("event" to "scene.build_tree") { "Building new scene tree" }
                scene.buildTree()
            }
        }
    }

    /**
     * Registers all nodes in [root] into:
     * - [flatTree] lookup table
     * - any systems that declared interest in the node's type
     */
    internal fun registerSubtree(root: Node<*>? = currScene) {
        root ?: return

        traverseNodes(root) { node ->
            // Flat lookup by path (assumes node paths are unique within a scene).
            flatTree[node.path] = node

            // Register node into systems interested in its type.
            systemsByNodeTypes[node::class]?.forEach { sys ->
                LogContext.with(
                    "scene" to root.name,
                    "nodePath" to node.path,
                    "system" to sys::class.simpleName
                ) {
                    log.trace("event" to "system.register_node") { "Registering node in system" }
                }
                sys.register(node)
            }
        }

        log.debug(
            "event" to "scene.subtree_registered",
            "scene" to root.name,
            "flatTreeSize" to flatTree.size
        ) { "Subtree registered" }
    }

    /**
     * Unregisters all nodes in [root] from:
     * - [flatTree]
     * - any systems that declared interest in the node's type
     */
    internal fun unregisterSubtree(root: Node<*>? = currScene) {
        root ?: return

        traverseNodes(root) { node ->
            flatTree.remove(node.path)

            systemsByNodeTypes[node::class]?.forEach { sys ->
                LogContext.with(
                    "scene" to root.name,
                    "nodePath" to node.path,
                    "system" to sys::class.simpleName
                ) {
                    log.trace("event" to "system.unregister_node") { "Unregistering node from system" }
                }
                sys.unregister(node)
            }
        }

        log.debug(
            "event" to "scene.subtree_unregistered",
            "scene" to root.name,
            "flatTreeSize" to flatTree.size
        ) { "Subtree unregistered" }
    }

    /**
     * Depth-first traversal of the node tree.
     * Used for register/unregister operations.
     */
    private fun traverseNodes(node: Node<*>, action: (Node<*>) -> Unit) {
        action(node)
        node.children.values.forEach { traverseNodes(it, action) }
    }

    /* ============================================================
     * System management
     * ============================================================ */

    /**
     * Registers a [TreeSystem] into the manager.
     *
     * Also indexes the system by:
     * - phase ([TreeSystem.phase]) and priority
     * - required node types ([TreeSystem.requiredTypes]) for fast node registration
     */
    fun <T : TreeSystem> addSystem(system: T) {
        require(!hasSystem(system::class)) {
            "System ${system::class.simpleName} is already registered"
        }

        systemsByClass[system::class] = system

        systems.getOrPut(system.phase) { mutableListOf() }.let { list ->
            list += system
            list.sortBy(TreeSystem::priority)
        }

        system.requiredTypes.forEach { type ->
            systemsByNodeTypes.computeIfAbsent(type) { mutableListOf() }.add(system)
        }

        log.info(
            "event" to "system.register",
            "system" to system::class.simpleName,
            "phase" to system.phase.name,
            "priority" to system.priority,
            "requiredTypes" to system.requiredTypes.joinToString { it.simpleName ?: it.toString() }
        ) { "Registered system" }
    }

    /** DSL helper: `+MySystem()` */
    inline operator fun <reified T : TreeSystem> T.unaryPlus() = addSystem(this)

    /**
     * Unregisters a system type and removes it from all internal indexes.
     */
    fun <T : TreeSystem> removeSystem(kClass: KClass<T>) {
        val systemName = kClass.simpleName ?: "UnknownSystem"

        require(hasSystem(kClass)) { "System ${kClass.simpleName} is not registered" }
        val system = systemsByClass[kClass] ?: return

        systems[system.phase]?.apply {
            remove(system)
            system.requiredTypes.forEach { type -> systemsByNodeTypes[type]?.remove(system) }
            sortBy(TreeSystem::priority)
        }
        systemsByClass.remove(kClass)

        log.info(
            "event" to "system.unregister",
            "system" to systemName,
            "phase" to system.phase.name
        ) { "Unregistered system" }
    }

    /** DSL helper: `-MySystem::class` */
    inline operator fun <reified T : TreeSystem> (KClass<T>).unaryMinus() = removeSystem(this)

    @Suppress("UNCHECKED_CAST")
    fun <T : TreeSystem> getSystem(clazz: KClass<T>): T = systemsByClass[clazz] as? T
        ?: throw IllegalStateException(
            """
                [SCENE MANAGER]
                The system ${clazz.simpleName} isn't registered
                To fix it: register it into a Scene Manager!
            """.trimIndent()
        )

    fun <T : TreeSystem> hasSystem(clazz: KClass<T>): Boolean = clazz in systemsByClass.keys
    operator fun contains(clazz: KClass<*>) = clazz in systemsByClass

    /* ============================================================
     * Group management
     * ============================================================ */

    fun addToGroup(group: String, node: Node<*>) {
        groups.computeIfAbsent(group) { mutableListOf() }.add(node)
        groupsByNode.computeIfAbsent(node) { mutableListOf() }.add(group)

        log.trace("event" to "group.add", "group" to group, "nodePath" to node.path) {
            "Added node to group"
        }
    }

    fun removeFromGroup(group: String, node: Node<*>) {
        groups[group]?.remove(node) ?: error("Node $node does not exist in group $group")
        groupsByNode[node]?.remove(group) ?: error("Group $group does not exist")

        log.trace("event" to "group.remove", "group" to group, "nodePath" to node.path) {
            "Removed node from group"
        }
    }

    fun updateGroups(node: Node<*>) {
        // Get or create entry
        val oldGroups = groupsByNode.computeIfAbsent(node) { mutableListOf() }

        // Remove old entries
        oldGroups.forEach { group ->
            groups[group]?.remove(node)
        }
        oldGroups.clear()

        // Add new entries
        oldGroups.addAll(node.groups)
        node.groups.forEach { group ->
            groups.computeIfAbsent(group) { mutableListOf() }.add(node)
        }
    }

    /**
     * Applies [callback] to all nodes in the group.
     * Useful for "broadcast" operations without scanning the whole tree.
     */
    fun signalGroup(group: String, callback: (node: Node<*>) -> Unit) {
        val groupNodes = groups[group] ?: error("Group $group does not exist")
        LogContext.with("group" to group) {
            log.debug("event" to "group.signal", "count" to groupNodes.size) { "Signaling group" }
            groupNodes.forEach(callback)
        }
    }

    /* ============================================================
     * Tick / update loop
     * ============================================================ */

    /**
     * Drives the scene update loop.
     *
     * Order (per tick):
     * - If a physics step is due:
     *   - PhysicsPre systems
     *   - nodePhysicsUpdate(physicsStep)
     *   - PhysicsPost systems
     * - FramePre systems
     * - nodeUpdate(delta)
     * - FramePost systems
     */
    fun tick(delta: Float) {
        val root = currScene ?: return

        LogContext.with(
            "scene" to root.name,
            "delta" to delta,
            "physicsStep" to physicsStep
        ) {
            val physicsFrame = isPhysicsFrame(delta)

            if (physicsFrame) {
                log.trace("event" to "tick.physics") { "Physics tick" }

                systems[TreeSystem.UpdatePhase.PhysicsPre]?.forEach { sys ->
                    LogContext.with("system" to (sys::class.simpleName ?: "UnknownSystem"), "phase" to "PhysicsPre") {
                        sys.tick(physicsStep)
                    }
                }

                root.nodePhysicsUpdate(physicsStep)

                systems[TreeSystem.UpdatePhase.PhysicsPost]?.forEach { sys ->
                    LogContext.with("system" to (sys::class.simpleName ?: "UnknownSystem"), "phase" to "PhysicsPost") {
                        sys.tick(physicsStep)
                    }
                }
            }

            systems[TreeSystem.UpdatePhase.FramePre]?.forEach { sys ->
                LogContext.with("system" to (sys::class.simpleName ?: "UnknownSystem"), "phase" to "FramePre") {
                    sys.tick(delta)
                }
            }

            root.nodeUpdate(delta)

            systems[TreeSystem.UpdatePhase.FramePost]?.forEach { sys ->
                LogContext.with("system" to (sys::class.simpleName ?: "UnknownSystem"), "phase" to "FramePost") {
                    sys.tick(delta)
                }
            }
        }
    }

    /**
     * Emits resize event for listeners (UI/layout/camera systems).
     */
    fun resize(width: Int, height: Int) {
        onResize.emit(width, height)
        log.debug("event" to "scene.resize", "width" to width, "height" to height) { "Resize" }
    }

    /**
     * Fixed time-step accumulator.
     * Returns true when we should run a physics step.
     */
    private fun isPhysicsFrame(delta: Float): Boolean {
        physicsAccumulator += delta
        if (physicsAccumulator >= physicsStep) {
            physicsAccumulator -= physicsStep
            return true
        }
        return false
    }

    /* ============================================================
     * Manager lifecycle
     * ============================================================ */

    override fun setup() {
        log.info("event" to "sceneManager.setup", "physicsStep" to physicsStep) { "Setup" }

        // Allow callers to register systems, groups, initial scene, etc.
        this.block()

        // Notify systems that they've been registered with the scene manager.
        systems.values.flatten().forEach(TreeSystem::onRegister)
    }

    override fun teardown() {
        log.info("event" to "sceneManager.teardown") { "Teardown" }
        systems.values.flatten().forEach(TreeSystem::onUnregister)
    }
}
