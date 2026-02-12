package canopy.core.nodes

import canopy.core.managers.Manager
import canopy.core.nodes.core.GlobalNodeSystem
import canopy.core.nodes.core.Node
import canopy.core.nodes.core.UpdatePhase
import canopy.core.signals.asSignalVal
import canopy.core.signals.createSignal
import com.badlogic.gdx.math.Vector2
import ktx.log.logger
import kotlin.reflect.KClass

/**
 * SceneManager is responsible for managing a scene tree, systems, groups, and active camera.
 *
 * Responsibilities:
 * - Scene replacement and lifecycle
 * - Fixed-step physics and frame updates
 * - Global systems execution
 * - Node grouping and signaling
 * - Active camera tracking and resize notifications
 */
class SceneManager(
    private var physicsStep: Float = 1f / 60f,
    block: SceneManager.() -> Unit = {},
) : Manager {
    private val logger = logger<SceneManager>()

    // =============================
    //      Dependency Injection
    // =============================
    private val dependenciesMap = mutableMapOf<KClass<*>, () -> Any?>()

    private val flatTree = mutableMapOf<String, Node<*>>()

    companion object {
        internal val currentParent = ThreadLocal.withInitial<SceneManager> { null }
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
    private val systems: MutableMap<UpdatePhase, MutableList<GlobalNodeSystem>> = mutableMapOf()

    private val systemsByType = mutableMapOf<KClass<out Node<*>>, MutableList<GlobalNodeSystem>>()

    // ===============================
    //          GROUPS
    // ===============================
    val groups: MutableMap<String, MutableList<Node<*>>> = mutableMapOf()

    init {
        block(this)
    }

    // ===============================
    //      DEPENDENCY INJECTION
    // ===============================
    fun <T : Any> registerInjectable(
        kClass: KClass<T>,
        injectable: () -> T?,
    ) {
        require(kClass !in dependenciesMap) {}
        dependenciesMap[kClass] = injectable
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inject(kClass: KClass<T>): T {
        val entry = dependenciesMap[kClass]
        requireNotNull(entry) { "" }
        return entry() as T
    }

    // ===============================
    //      SCENE MANAGEMENT
    // ===============================
    private fun replaceScene(newScene: Node<*>?) {
        val oldScene = _currScene

        oldScene?.let {
            it.exitTree()
            unregisterSubtree(it)
        }

        _currScene = newScene
        onSceneReplaced.emit(_currScene)
        newScene?.let {
            registerSubtree(it)
            it.buildTree()
        }
    }

    internal fun registerSubtree(root: Node<*>? = currScene) {
        root ?: return
        traverseNodes(root) { node ->
            flatTree
            systemsByType[node::class]?.forEach { sys -> sys.register(node) }
        }
    }

    internal fun unregisterSubtree(root: Node<*>? = currScene) {
        root ?: return
        traverseNodes(root) { node ->
            flatTree.remove(node.name)
            systemsByType[node::class]?.forEach { sys -> sys.unregister(node) }
        }
    }

    private fun traverseNodes(
        node: Node<*>,
        action: (Node<*>) -> Unit,
    ) {
        action(node)
        node.children.values.forEach { traverseNodes(it, action) }
    }

    // ===============================
    //      SYSTEM MANAGEMENT
    // ===============================
    fun addSystem(system: GlobalNodeSystem) {
        systems.getOrPut(system.phase) { mutableListOf() }.add(system)
        system.requiredTypes.forEach { type ->
            systemsByType.computeIfAbsent(type) { mutableListOf() }.add(system)
        }
    }

    fun removeSystem(system: GlobalNodeSystem) {
        systems[system.phase]?.remove(system)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : GlobalNodeSystem> getSystem(clazz: KClass<T>): T =
        systems.values.flatten().firstOrNull { clazz.isInstance(it) } as? T
            ?: throw IllegalStateException(
                """

                [SCENE MANAGER]
                The system ${clazz.simpleName} isn't registered
                To fix it: register it into a Scene Manager!

                """.trimIndent(),
            )

    fun <T : GlobalNodeSystem> hasSystem(clazz: KClass<T>): Boolean =
        systems.values.flatten().firstOrNull { clazz.isInstance(it) } != null

    // ===============================
    //      GROUP MANAGEMENT
    // ===============================
    fun addToGroup(
        group: String,
        node: Node<*>,
    ) {
        val groupNodes = groups.computeIfAbsent(group) { mutableListOf() }
        groupNodes += node
    }

    fun removeFromGroup(
        group: String,
        node: Node<*>,
    ) {
        val groupNodes = groups[group] ?: error("Group $group does not exist")
        groupNodes -= node
    }

    fun signalGroup(
        group: String,
        callback: (node: Node<*>) -> Unit,
    ) {
        val groupNodes = groups[group] ?: error("Group $group does not exist")
        groupNodes.forEach(callback)
    }

    // ===============================
    //             TICK
    // ===============================
    fun tick(delta: Float) {
        val root = currScene ?: return

        systems[UpdatePhase.Input]?.forEach { it.tick(delta) }

        if (isPhysicsFrame(delta)) {
            systems[UpdatePhase.PhysicsBeforeScene]?.forEach { it.tick(physicsStep) }
            root.nodePhysicsUpdate(physicsStep)
            systems[UpdatePhase.PhysicsAfterScene]?.forEach { it.tick(physicsStep) }
        }

        systems[UpdatePhase.AnimationBeforeScene]?.forEach { it.tick(delta) }
        systems[UpdatePhase.FrameBeforeScene]?.forEach { it.tick(delta) }
        root.nodeUpdate(delta)
        systems[UpdatePhase.AnimationAfterScene]?.forEach { it.tick(delta) }
        systems[UpdatePhase.FrameAfterScene]?.forEach { it.tick(delta) }
    }

    fun resize(
        width: Int,
        height: Int,
    ) {
        onResize.emit(width, height)
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
        systems.values.flatten().forEach(GlobalNodeSystem::onSystemInit)
    }

    override fun teardown() {
        systems.values.flatten().forEach(GlobalNodeSystem::onSystemClose)
    }
}
