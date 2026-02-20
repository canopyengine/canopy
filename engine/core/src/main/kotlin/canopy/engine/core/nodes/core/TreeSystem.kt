package canopy.engine.core.nodes.core

import kotlin.reflect.KClass
import canopy.engine.core.managers.InjectionManager
import canopy.engine.core.managers.ManagersRegistry
import canopy.engine.core.managers.SceneManager

// ===============================
//      GLOBAL NODE SYSTEM BASE
// ===============================

/**
 * Base class for systems that span the whole tree, and process nodes that match the required types
 *
 * See more [here](https://github.com/canopyengine/canopy-docs/blob/main/docs/manuals/core/node-system.md).
 *
 * @param requiredTypes The types of nodes this system should operate on.
 */
abstract class TreeSystem(
    internal val phase: UpdatePhase,
    val priority: Int = 0,
    vararg val requiredTypes: KClass<out Node<*>>,
) {
    // ===============================
    //        CORE PROPERTIES
    // ===============================

    protected val sceneManager: SceneManager by lazy { ManagersRegistry.get(SceneManager::class) }
    protected val injectionManager: InjectionManager by lazy { ManagersRegistry.get(InjectionManager::class) }

    /** Nodes currently matching the system's type requirements */
    protected val matchingNodes = mutableListOf<Node<*>>()

    // ===============================
    //         LIFECYCLE HOOKS
    // ===============================

    /**
     * Called once before updates begin.
     * Automatically sets up the scene manager reference.
     */
    open fun onRegister() {}

    /** Called when the system is being closed / disposed. Override if needed. */
    open fun onUnregister() {}

    // ===============================
    //         NODE REGISTRATION
    // ===============================

    /**
     * Registers a node with the system.
     * Called by tree lifecycle when nodes enter the scene.
     */
    fun register(node: Node<*>) {
        if (!acceptsNode(node)) return

        matchingNodes += node
        onNodeAdded(node)
    }

    /**
     * Unregisters a node from the system.
     * Called by tree lifecycle when nodes exit the scene.
     */
    fun unregister(node: Node<*>) {
        if (!matchingNodes.remove(node)) return
        onNodeRemoved(node)
    }

    /** Called when a node is added to the system. Override to add custom logic. */
    protected open fun onNodeAdded(node: Node<*>) {}

    /** Called when a node is removed from the system. Override to add custom logic. */
    protected open fun onNodeRemoved(node: Node<*>) {}

    /** Checks whether a node (or any of its children) matches the required types */
    private fun acceptsNode(node: Node<*>) = requiredTypes.any { type ->
        type.isInstance(node) || node.hasChildType(type)
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
    protected open fun processNode(node: Node<*>, delta: Float) {}

    // ===============================
//        UPDATE PHASE ENUM
// ===============================

    /**
     * Defines when a global node system should be executed.
     */
    enum class UpdatePhase {
        /** Runs before physics is processed for the scene */
        PhysicsPre,

        /** Runs after physics is processed for the scene */
        PhysicsPost,

        /** Runs before scene frame updates */
        FramePre,

        /** Runs after scene frame updates */
        FramePost,
    }
}

/**
 * Helper method that helps to create tree systems in-place
 */
fun treeSystem(
    phase: TreeSystem.UpdatePhase,
    priority: Int = 0,
    vararg requiredTypes: KClass<out Node<*>>,
    onRegister: TreeSystem.() -> Unit = {},
    onUnregister: TreeSystem.() -> Unit = {},
    beforeProcess: TreeSystem.(delta: Float) -> Unit = {},
    afterProcess: TreeSystem.(delta: Float) -> Unit = {},
    processNode: TreeSystem.(node: Node<*>, delta: Float) -> Unit = { _, _ -> },
) {
    object : TreeSystem(phase, priority, *requiredTypes) {
        override fun onRegister() {
            onRegister.invoke(this)
        }

        override fun onUnregister() {
            onUnregister.invoke(this)
        }

        override fun beforeProcess(delta: Float) {
            beforeProcess.invoke(this, delta)
        }

        override fun afterProcess(delta: Float) {
            afterProcess.invoke(this, delta)
        }

        override fun processNode(node: Node<*>, delta: Float) {
            processNode.invoke(this, node, delta)
        }
    }
}
