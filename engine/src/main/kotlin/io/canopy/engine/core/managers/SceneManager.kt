package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import io.canopy.engine.app.App
import io.canopy.engine.core.CleanupFailures
import io.canopy.engine.core.flows.events.event
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.core.nodes.NodeState
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
 * - Physics ticks run at a fixed time step ([physicsStep]) supplied by [io.canopy.engine.app.EngineLoop]
 * - Frame ticks run every frame with variable delta
 *
 * NOTE:
 * This class does not currently enforce thread-safety. Scene mutation is expected
 * to happen on the main/game thread. The scene index, system indexes, and group
 * maps follow the same thread-confinement rule.
 */
class SceneManager(val physicsStep: Float = 1f / 60f, private val block: SceneManager.() -> Unit = {}) : Manager {

    private var sceneManagerBuilder: SceneManager.() -> Unit = {}

    /** Dedicated subsystem logger (routable + consistent). */
    private val log = EngineLogs.subsystem("scene")

    /**
     * Flat index of nodes keyed by their path.
     * This is updated when scenes are registered/unregistered.
     */
    private val flatTree = mutableMapOf<String, Node<*>>()
    private val pathsByNode = mutableMapOf<Node<*>, String>()
    private val ownedStates = mutableMapOf<Long, NodeState>()
    internal val retainedStateCount: Int get() = ownedStates.size
    internal val indexedNodeCount: Int get() = pathsByNode.size
    internal var indexRemovalCount: Long = 0
        private set

    @JvmSynthetic
    internal fun retainState(id: Long, state: NodeState) {
        ownedStates[id] = state
    }

    @JvmSynthetic
    internal fun releaseState(id: Long) {
        ownedStates.remove(id)
    }

    @JvmSynthetic
    internal fun reindex(node: Node<*>) {
        val previous = pathsByNode[node] ?: return
        if (flatTree[previous] === node) flatTree.remove(previous)
        pathsByNode[node] = node.internalPath()
        flatTree[node.internalPath()] = node
    }
    private val phaseSnapshots = mutableMapOf<TreeSystem.UpdatePhase, List<TreeSystem>>()
    private fun phaseSnapshot(phase: TreeSystem.UpdatePhase): List<TreeSystem> =
        phaseSnapshots.getOrPut(phase) { systems[phase]?.toList().orEmpty() }

    /* ============================================================
     * Signals / events
     * ============================================================ */

    /** Latest host dimensions; updated before [onResize] listeners run. Initially zero. */
    val sceneSize = io.canopy.engine.core.flows.events.signal(owner = null, value = Vector2.Zero)

    /** Emitted for every host resize, after [sceneSize] has been updated. */
    val onResize = event<Int, Int>(owner = null)

    /** Emitted after the scene root is replaced. Payload is the new root (or null). */
    val onSceneReplaced = event<Node<*>?>(owner = null)

    /* ============================================================
     * Scene state
     * ============================================================ */

    private val deletionQueue = linkedSetOf<Node<*>>()
    private var updateDepth = 0
    private var flushing = false
    internal var pauseState: () -> Boolean = { false }

    /** Application pause state. Standalone scene managers default to running. */
    val isPaused: Boolean get() = pauseState()

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

    /* ============================================================
     * Systems
     * ============================================================ */

    /**
     * Systems grouped by update phase. Each list is kept sorted by system priority.
     */
    private val systems: MutableMap<TreeSystem.UpdatePhase, MutableList<TreeSystem>> = mutableMapOf()

    private var entered = false
    private var configured = false
    private val initializedSystems = linkedSetOf<TreeSystem>()

    /** Direct lookup by system class (useful for get/remove). */
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
    private val groupsByNode = mutableMapOf<Node<*>, MutableSet<String>>()

    /**
     * Maps a group to all their nodes
     */
    private val groups = mutableMapOf<String, MutableSet<Node<*>>>()

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
        newScene?.requireValid("replace scene")
        val oldScene = _currScene
        if (oldScene === newScene) return

        log.info(
            "event" to "scene.replace",
            "oldScene" to oldScene?.name,
            "newScene" to newScene?.name
        ) { "Replacing scene" }

        oldScene?.let { scene ->
            LogContext.with("scene" to scene.name) {
                log.debug("event" to "scene.exit_tree") { "Exiting old scene tree" }
                val failures = CleanupFailures()
                failures.attempt { NodeLifetime.withOwner(scene) { scene.nodeExitTree() } }
                failures.attempt { scene.releaseResources() }
                failures.attempt { unregisterSubtree(scene) }
                _currScene = null
                failures.rethrow()
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
    @JvmSynthetic
    internal fun registerSubtree(root: Node<*>? = currScene) {
        root ?: return

        traverseNodes(root) { node ->
            // Flat lookup by path (assumes node paths are unique within a scene).
            node.requireValid("register subtree")
            if (!node.isInsideTree) return@traverseNodes
            pathsByNode[node]?.let { old -> if (flatTree[old] === node) flatTree.remove(old) }
            pathsByNode[node] = node.path
            flatTree[node.path] = node

            // Register node into systems interested in its type.
            systemsFor(node).toList().forEach { sys ->
                LogContext.with(
                    "scene" to root.name,
                    "nodePath" to node.path,
                    "system" to sys::class.simpleName
                ) {
                    log.trace("event" to "system.register_node") { "Registering node in system" }
                }
                if (entered && sys in initializedSystems && systemsByClass[sys::class] === sys) {
                    sys.register(node)
                }
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
    @JvmSynthetic
    internal fun unregisterSubtree(root: Node<*>? = currScene) {
        root ?: return
        val nodes = mutableListOf<Node<*>>()
        traverseNodes(root) { nodes += it }
        val failures = CleanupFailures()
        for (node in nodes) {
            pathsByNode.remove(node)?.let { key ->
                if (flatTree[key] === node) flatTree.remove(key)
                indexRemovalCount++
            }
            groupsByNode.remove(node)?.forEach { group ->
                groups[group]?.remove(node)
                if (groups[group].isNullOrEmpty()) groups.remove(group)
            }
            systemsFor(node).toList().forEach { system -> failures.attempt { system.unregisterInternal(node) } }
        }
        failures.rethrow()
    }

    private fun traverseNodes(node: Node<*>, action: (Node<*>) -> Unit) {
        val pending = ArrayDeque<Node<*>>()
        pending.addLast(node)
        while (pending.isNotEmpty()) {
            val next = pending.removeLast()
            val children = next.childSnapshot()
            for (i in children.indices.reversed()) pending.addLast(children[i])
            action(next)
        }
    }

    /** Returns systems indexed by any node type that the concrete node inherits. */
    private fun systemsFor(node: Node<*>): Sequence<TreeSystem> = systemsByNodeTypes.asSequence()
        .filter { (nodeType, _) -> nodeType.isInstance(node) }
        .flatMap { (_, indexedSystems) -> indexedSystems.asSequence() }
        .distinct()

    /* ============================================================
     * System management
     * ============================================================ */

    /**
     * Registers a [TreeSystem] into the manager.
     *
     * Also indexes the system by:
     * - phase ([TreeSystem.phase]) and priority
     * - required node types ([TreeSystem.requiredTypes]) for fast node registration
     *
     * When this manager has entered, [TreeSystem.onRegister] runs before existing indexed nodes are added.
     * Otherwise initialization and node backfilling are deferred until [onEnter].
     */
    fun <T : TreeSystem> addSystem(system: T) {
        require(!hasSystem(system::class)) {
            "System ${system::class.simpleName} is already registered"
        }

        phaseSnapshots.remove(system.phase)
        systemsByClass[system::class] = system

        systems.getOrPut(system.phase) { mutableListOf() }.let { list ->
            list += system
            list.sortBy(TreeSystem::priority)
        }

        system.requiredTypes.forEach { type ->
            systemsByNodeTypes.computeIfAbsent(type) { mutableListOf() }.add(system)
        }

        if (entered) {
            initializeSystem(system)
            backfillSystem(system)
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
     * Removes a system from all indexes, releases matching nodes, then calls [TreeSystem.onUnregister]
     * if it was initialized. The same instance can subsequently be registered again.
     */
    fun <T : TreeSystem> removeSystem(kClass: KClass<T>) {
        val systemName = kClass.simpleName ?: "UnknownSystem"

        require(hasSystem(kClass)) { "System ${kClass.simpleName} is not registered" }
        val system = systemsByClass[kClass] ?: return

        systems[system.phase]?.apply {
            remove(system)
            system.requiredTypes.forEach { type -> systemsByNodeTypes[type]?.remove(system) }
        }
        phaseSnapshots.remove(system.phase)
        systemsByClass.remove(kClass)
        releaseSystem(system)

        log.info(
            "event" to "system.unregister",
            "system" to systemName,
            "phase" to system.phase.name
        ) { "Unregistered system" }
    }

    private fun initializeSystem(system: TreeSystem) {
        if (systemsByClass[system::class] === system && initializedSystems.add(system)) {
            system.onRegister()
        }
    }

    private fun backfillSystem(system: TreeSystem) {
        // Hooks may change scene membership or remove the system while registration is in progress.
        flatTree.values.toList().forEach { node ->
            if (
                entered &&
                system in initializedSystems &&
                systemsByClass[system::class] === system &&
                flatTree[node.path] === node &&
                system.acceptsNode(node)
            ) {
                system.register(node)
            }
        }
    }

    private fun releaseSystem(system: TreeSystem) {
        val initialized = initializedSystems.remove(system)
        val failures = CleanupFailures()
        failures.attempt { system.clearNodes() }
        if (initialized) {
            failures.attempt { system.onUnregister() }
        }
        failures.rethrow()
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

    internal fun queryGroup(group: String): List<Node<*>> = groups[group].orEmpty().filter { it.isInsideTree }

    fun addToGroup(group: String, node: Node<*>) {
        node.requireValid("add group")
        if (!node.isInsideTree || node in groups[group].orEmpty()) return
        groups.computeIfAbsent(group) { linkedSetOf() }.add(node)
        groupsByNode.computeIfAbsent(node) { linkedSetOf() }.add(group)

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
        val oldGroups = groupsByNode.computeIfAbsent(node) { linkedSetOf() }

        // Remove old entries
        oldGroups.forEach { group ->
            groups[group]?.remove(node)
        }
        oldGroups.clear()

        // Add new entries
        oldGroups.addAll(node.groups)
        node.groups.forEach { group ->
            groups.computeIfAbsent(group) { linkedSetOf() }.add(node)
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
            groupNodes.toList().forEach { node ->
                if (node.isInsideTree &&
                    node in groups[group].orEmpty()
                ) {
                    node.callback("group $group") { callback(node) }
                }
            }
        }
    }

    /* ============================================================
     * Tick / update loop
     * ============================================================ */

    /**
     * Drives one variable-step scene update. Fixed-step physics is dispatched
     * separately by [io.canopy.engine.app.EngineLoop].
     *
     * Frame order:
     * - FramePre systems
     * - nodeUpdate(delta)
     * - FramePost systems
     * - drain queued node destruction (also after callback failures)
     */
    override fun onUpdate(delta: Float) = updateBoundary {
        val root = currScene ?: return@updateBoundary

        LogContext.with(
            "scene" to root.name,
            "delta" to delta,
            "physicsStep" to physicsStep
        ) {
            runPhase(TreeSystem.UpdatePhase.FramePre, delta)

            NodeLifetime.withOwner(root) { root.dispatchUpdate(delta) }

            runPhase(TreeSystem.UpdatePhase.FramePost, delta)
        }
    }

    /** Dispatches a complete physics traversal, then drains queued destruction even if callbacks fail. */
    override fun onPhysicsUpdate(delta: Float) = updateBoundary {
        val root = currScene ?: return@updateBoundary

        LogContext.with("scene" to root.name, "delta" to delta) {
            log.trace("event" to "tick.physics") { "Physics tick" }

            runPhase(TreeSystem.UpdatePhase.PhysicsPre, delta)

            NodeLifetime.withOwner(root) { root.dispatchPhysicsUpdate(delta) }

            runPhase(TreeSystem.UpdatePhase.PhysicsPost, delta)
        }
    }

    private fun runPhase(phase: TreeSystem.UpdatePhase, delta: Float) {
        phaseSnapshot(phase).forEach { system ->
            LogContext.with("system" to (system::class.simpleName ?: "UnknownSystem"), "phase" to phase.name) {
                system.tick(delta)
            }
        }
    }

    @JvmSynthetic
    internal fun queueFree(node: Node<*>) {
        node.markQueued()
        deletionQueue += node
    }

    private fun updateBoundary(block: () -> Unit) {
        updateDepth++
        val failures = CleanupFailures()
        failures.attempt(block)
        updateDepth--
        if (updateDepth == 0) failures.attempt { flushDeletionQueue() }
        failures.rethrow()
    }

    private fun flushDeletionQueue() {
        if (flushing) return
        flushing = true
        val failures = CleanupFailures()
        try {
            while (deletionQueue.isNotEmpty()) {
                val pending = deletionQueue.toList()
                deletionQueue.clear()
                for (root in pending) {
                    if (root.isFreed) continue
                    val nodes = mutableListOf<Node<*>>()
                    traverseNodes(root) { nodes += it }
                    nodes.forEach { it.beginDestruction() }
                    failures.attempt { root.exitInternal() }
                    failures.attempt { unregisterSubtree(root) }
                    root.detachFromParent()
                    if (_currScene === root) {
                        _currScene = null
                        failures.attempt { onSceneReplaced.emit(null) }
                    }
                    for (node in nodes.asReversed()) failures.attempt { node.finishDestruction() }
                }
            }
        } finally {
            flushing = false
        }
        failures.rethrow()
    }

    /**
     * Stores host dimensions before emitting the resize event. Equal dimensions do not emit a signal change,
     * but every call still emits [onResize].
     */
    override fun onResize(width: Int, height: Int) {
        sceneSize.update { Vector2(width.toFloat(), height.toFloat()) }
        onResize.emit(width, height)
        log.debug("event" to "scene.resize", "width" to width, "height" to height) { "Resize" }
    }

    /* ============================================================
     * Manager lifecycle
     * ============================================================ */

    /** Initializes systems before matching nodes. Configuration blocks run only on the first entry. */
    override fun onEnter() {
        if (entered) return
        entered = true
        systemsByClass.values.toList().forEach { initializeSystem(it) }
        currScene?.let { NodeLifetime.withOwner(it) { it.nodeEnterTree() } }
        systemsByClass.values.toList().forEach { backfillSystem(it) }

        log.info("event" to "sceneManager.setup", "physicsStep" to physicsStep) { "Setup" }

        // Allow callers to register systems, groups, initial scene, etc.
        if (!configured) {
            configured = true
            sceneManagerBuilder()
            this.block()
        }
    }

    /**
     * Exits node lifetimes and releases matches and initialized systems once, including on failure.
     * Retains scene structure and system configuration. Re-entry calls node entry callbacks without
     * rebuilding DSL blocks; recreate resources in entry callbacks when they must survive re-entry.
     */
    override fun onExit() {
        if (!entered) return
        entered = false
        log.info("event" to "sceneManager.teardown") { "Teardown" }
        // One failing cleanup hook must not leave the other systems holding scene nodes.
        val sceneFailures = CleanupFailures()
        sceneFailures.attempt { currScene?.let { NodeLifetime.withOwner(it) { it.nodeExitTree() } } }
        sceneFailures.attempt { currScene?.releaseResources() }
        val systemFailures = CleanupFailures()
        systemsByClass.values.toList().forEach { system ->
            systemFailures.attempt { releaseSystem(system) }
        }
        sceneFailures.attempt { systemFailures.rethrow() }
        sceneFailures.attempt { flushDeletionQueue() }
        sceneFailures.rethrow()
    }

    fun App<*>.sceneManager(handler: SceneManager.() -> Unit) {
        sceneManagerBuilder = handler
    }
}
