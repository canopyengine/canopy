package anchors.framework.nodes.core

import anchors.framework.input.InputSystem
import anchors.framework.managers.ManagersRegistry
import anchors.framework.nodes.SceneManager
import kotlin.reflect.KClass

// ===============================
//        UPDATE PHASE ENUM
// ===============================

/**
 * Defines when a global node system should be executed.
 */
enum class UpdatePhase {
    /** Runs before any event */
    Input,

    /** Runs before physics is processed for the scene */
    PhysicsBeforeScene,

    /** Runs after physics is processed for the scene */
    PhysicsAfterScene,

    /** Runs before scene frame updates */
    FrameBeforeScene,

    /** Runs after scene frame updates */
    FrameAfterScene,

    AnimationBeforeScene,
    AnimationAfterScene,
}

// ===============================
//      GLOBAL NODE SYSTEM BASE
// ===============================

/**
 * Base class for systems operating on all nodes of a scene that match certain types.
 *
 * @param requiredTypes The types of nodes this system should operate on.
 */
abstract class GlobalNodeSystem(
    internal val phase: UpdatePhase,
    vararg val requiredTypes: KClass<out Node<*>>,
) {
    // ===============================
    //        CORE PROPERTIES
    // ===============================

    /** Reference to the scene manager (set automatically on init) */
    protected val sceneManager: SceneManager by lazy { ManagersRegistry.get(SceneManager::class) }
    protected val inputManager: InputSystem by lazy { sceneManager.getSystem(InputSystem::class) }

    /** Nodes currently matching the system's type requirements */
    protected val matchingNodes = mutableListOf<Node<*>>()

    init {
        val parent =
            SceneManager.currentParent.get()
                ?: error("Key must be inside a track")

        parent.addSystem(this)
    }

    // ===============================
    //         LIFECYCLE HOOKS
    // ===============================

    /**
     * Called once before updates begin.
     * Automatically sets up the scene manager reference.
     */
    open fun onSystemInit() {}

    /** Called when the system is being closed / disposed. Override if needed. */
    open fun onSystemClose() {}

    // ===============================
    //         NODE REGISTRATION
    // ===============================

    /**
     * Registers a node with the system.
     * Called by tree lifecycle when nodes enter the scene.
     */
    fun register(node: Node<*>) {
        if (acceptsNode(node)) {
            matchingNodes += node
            onNodeAdded(node)
        }
    }

    /**
     * Unregisters a node from the system.
     * Called by tree lifecycle when nodes exit the scene.
     */
    fun unregister(node: Node<*>) {
        if (matchingNodes.remove(node)) {
            onNodeRemoved(node)
        }
    }

    /** Called when a node is added to the system. Override to add custom logic. */
    protected open fun onNodeAdded(node: Node<*>) {}

    /** Called when a node is removed from the system. Override to add custom logic. */
    protected open fun onNodeRemoved(node: Node<*>) {}

    /** Checks whether a node (or any of its children) matches the required types */
    private fun acceptsNode(node: Node<*>) =
        requiredTypes.any { type ->
            type.isInstance(node) ||
                node.hasChildType(type)
        }

    // ===============================
    //           TICK PROCESSING
    // ===============================

    /**
     * Called every frame or physics tick by the scheduler.
     * Executes optional hooks before/after node processing.
     */
    fun tick(delta: Float) {
        beforeProcess(delta)
        matchingNodes.forEach { processNode(it, delta) }
        afterProcess(delta)
    }

    /**
     * Optional hook executed before iterating nodes.
     * Override to implement pre-processing logic.
     */
    protected open fun beforeProcess(delta: Float) {}

    /**
     * Optional hook executed after iterating nodes.
     * Override to implement post-processing logic.
     */
    protected open fun afterProcess(delta: Float) {}

    /**
     * Override to define logic applied per node during each tick.
     */
    protected open fun processNode(
        node: Node<*>,
        delta: Float,
    ) {}
}
