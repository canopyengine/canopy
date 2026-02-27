package io.canopy.engine.core.managers

import kotlin.reflect.KClass
import com.badlogic.gdx.math.Vector2
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.nodes.core.TreeSystem
import io.canopy.engine.core.signals.asSignalVal
import io.canopy.engine.core.signals.createSignal
import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.engine.EngineLogs

/**
 * SceneManager is responsible for managing a scene tree, systems, groups, and active camera.
 */
class SceneManager(private var physicsStep: Float = 1f / 60f, private val block: SceneManager.() -> Unit = {}) :
    Manager {

    // Use a dedicated subsystem logger for scene management (routable + consistent)
    private val log = EngineLogs.subsystem("scene")

    private val flatTree = mutableMapOf<String, Node<*>>()

    companion object {
        internal val currentParent = ThreadLocal.withInitial<SceneManager?> { null }
    }

    init {
        currentParent.set(this)
    }

    // ===============================
    //         SIGNALS
    // ===============================

    /** Emitted when the window or viewport is resized */
    val sceneSize = Vector2.Zero.asSignalVal()
    val onResize = createSignal<Int, Int>()

    /** Emitted when scene is replaced **/
    val onSceneReplaced = createSignal<Node<*>?>()

    // ===============================
    //         SCENE STATE
    // ===============================
    private var _currScene: Node<*>? = null
    var currScene: Node<*>?
        get() = _currScene
        set(value) = replaceScene(value)

    private var physicsAccumulator = 0f

    // ===============================
    //         SYSTEMS
    // ===============================
    private val systems: MutableMap<TreeSystem.UpdatePhase, MutableList<TreeSystem>> = mutableMapOf()
    private val systemsByClass = mutableMapOf<KClass<out TreeSystem>, TreeSystem>()

    private val systemsByNodeTypes = mutableMapOf<KClass<out Node<*>>, MutableList<TreeSystem>>()

    // ===============================
    //          GROUPS
    // ===============================
    val groups = mutableMapOf<String, MutableList<Node<*>>>()

    // ===============================
    //      SCENE MANAGEMENT
    // ===============================
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

    internal fun registerSubtree(root: Node<*>? = currScene) {
        root ?: return
        traverseNodes(root) { node ->
            // keep a flat lookup by name (assuming unique names; if not, revisit this key)
            flatTree[node.path] = node

            // Register node into systems interested in its type
            systemsByNodeTypes[node::class]?.forEach { sys ->
                LogContext.with(
                    "scene" to (root.name),
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

    private fun traverseNodes(node: Node<*>, action: (Node<*>) -> Unit) {
        action(node)
        node.children.values.forEach { traverseNodes(it, action) }
    }

    // ===============================
    //      SYSTEM MANAGEMENT
    // ===============================

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

    inline operator fun <reified T : TreeSystem> T.unaryPlus() = addSystem(this)

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

    // ===============================
    //      GROUP MANAGEMENT
    // ===============================
    fun addToGroup(group: String, node: Node<*>) {
        groups.computeIfAbsent(group) { mutableListOf() }.add(node)
        log.trace("event" to "group.add", "group" to group, "nodePath" to node.path) {
            "Added node to group"
        }
    }

    fun removeFromGroup(group: String, node: Node<*>) {
        val groupNodes = groups[group] ?: error("Group $group does not exist")
        groupNodes -= node
        log.trace("event" to "group.remove", "group" to group, "nodePath" to node.path) {
            "Removed node from group"
        }
    }

    fun signalGroup(group: String, callback: (node: Node<*>) -> Unit) {
        val groupNodes = groups[group] ?: error("Group $group does not exist")
        LogContext.with("group" to group) {
            log.debug("event" to "group.signal", "count" to groupNodes.size) { "Signaling group" }
            groupNodes.forEach(callback)
        }
    }

    // ===============================
    //             TICK
    // ===============================
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

    fun resize(width: Int, height: Int) {
        onResize.emit(width, height)
        log.debug("event" to "scene.resize", "width" to width, "height" to height) { "Resize" }
    }

    private fun isPhysicsFrame(delta: Float): Boolean {
        physicsAccumulator += delta
        if (physicsAccumulator >= physicsStep) {
            physicsAccumulator -= physicsStep
            return true
        }
        return false
    }

    // ===============================
    //        LIFECYCLE HOOKS
    // ===============================
    override fun setup() {
        log.info("event" to "sceneManager.setup", "physicsStep" to physicsStep) { "Setup" }
        this.block()
        systems.values.flatten().forEach(TreeSystem::onRegister)
    }

    override fun teardown() {
        log.info("event" to "sceneManager.teardown") { "Teardown" }
        systems.values.flatten().forEach(TreeSystem::onUnregister)
    }
}

inline fun <reified T : TreeSystem> treeSystem(): T =
    ManagersRegistry.getManager(SceneManager::class).getSystem(T::class)

inline fun <reified T : TreeSystem> lazyTreeSystem() = lazy { treeSystem<T>() }
