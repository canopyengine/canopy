package io.canopy.engine.core.nodes

import kotlin.reflect.KClass
import java.util.Collections
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext

/**
 * System that spans the whole node tree and processes [Node]s accordingly
 */
abstract class TreeSystem(
    internal val phase: UpdatePhase,
    val priority: Int = 0,
    vararg val requiredTypes: KClass<out Node<*>>,
) {
    // Engine subsystem logger for systems
    private val log = EngineLogs.subsystem("system")

    /** Nodes currently matching the system's type requirements */
    private val matches = linkedSetOf<Node<*>>()
    private var snapshot: List<Node<*>>? = null

    /** Read-only ordered snapshot; changes become visible on the next access. */
    protected val matchingNodes: List<Node<*>>
        get() = snapshot ?: Collections.unmodifiableList(matches.toList()).also { snapshot = it }

    private val removalRegistrations = mutableMapOf<Node<*>, () -> Unit>()

    private val systemName: String = this::class.simpleName ?: "AnonymousTreeSystem"

    // ===============================
    //         LIFECYCLE HOOKS
    // ===============================

    /** Called once when the owning scene manager enters, or immediately when added to an entered manager. */
    open fun onRegister() = Unit

    /** Called after matching nodes are removed when this system is removed or its manager exits. */
    open fun onUnregister() = Unit

    // ===============================
    //         NODE REGISTRATION
    // ===============================

    /** Adds an accepted node once; repeat registrations do not repeat [onNodeAdded]. */
    fun register(node: Node<*>) {
        node.requireValid("register system")
        if (node in matches || !acceptsNode(node)) return

        matches += node
        snapshot = null
        removalRegistrations[node] = node.onRemoval { unregisterInternal(node) }

        LogContext.with(
            "system" to systemName,
            "phase" to phase.name,
            "nodePath" to node.internalPath()
        ) {
            log.trace("event" to "system.node_added") { "Node added to system" }
        }

        runHook("onNodeAdded", node = node) { onNodeAdded(node) }
    }

    /** Releases a match and its lifetime registration before invoking [onNodeRemoved]. */
    fun unregister(node: Node<*>) {
        node.requireValid("unregister system")
        unregisterInternal(node)
    }

    @JvmSynthetic
    internal fun unregisterInternal(node: Node<*>) {
        if (!matches.remove(node)) return
        snapshot = null
        removalRegistrations.remove(node)?.invoke()

        LogContext.with(
            "system" to systemName,
            "phase" to phase.name,
            "nodePath" to node.internalPath()
        ) {
            log.trace("event" to "system.node_removed") { "Node removed from system" }
        }

        runHook("onNodeRemoved", node = node) { onNodeRemoved(node) }
    }

    /** Releases current matches through the normal removal hook, leaving this system reusable. */
    internal fun clearNodes() {
        var failure: Throwable? = null
        matchingNodes.forEach { node ->
            try {
                unregisterInternal(node)
            } catch (error: Throwable) {
                val previous = failure
                if (previous == null) {
                    failure = error
                } else if (previous !== error) {
                    previous.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }

    protected open fun onNodeAdded(node: Node<*>) {}
    protected open fun onNodeRemoved(node: Node<*>) {}

    private fun acceptsNode(node: Node<*>) = requiredTypes.any { type ->
        type.isInstance(node) || node.hasChildType(type)
    }

    // ===============================
    //           TICK PROCESSING
    // ===============================

    /**
     * Runs per-node processing only for eligible [Node.processMode] values.
     * Before/after hooks still run, including while paused, so input and rendering remain available.
     * [matchingNodes] retains inactive nodes; hooks that process that list directly must apply
     * [Node.canProcess] themselves when implementing gameplay logic.
     */
    fun tick(delta: Float) {
        LogContext.with(
            "system" to systemName,
            "phase" to phase.name,
            "delta" to delta
        ) {
            // Very low-noise: you can enable TRACE to see these
            log.trace("event" to "system.tick", "matchingCount" to matchingNodes.size) {
                "Tick"
            }

            runHook("beforeProcess", delta = delta) { beforeProcess(delta) }

            // No automatic per-node logging (too spammy). Use subclass logging if needed.
            matchingNodes.forEach { node ->
                if (node in matches && node.isInsideTree && node.canProcess()) {
                    runHook("processNode", delta = delta, node = node) { processNode(node, delta) }
                }
            }

            runHook("afterProcess", delta = delta) { afterProcess(delta) }
        }
    }

    protected open fun beforeProcess(delta: Float) {}
    protected open fun afterProcess(delta: Float) {}
    protected open fun processNode(node: Node<*>, delta: Float) {}

    // ===============================
    //           SAFE HOOK RUNNER
    // ===============================

    private fun runHook(hook: String, delta: Float? = null, node: Node<*>? = null, block: () -> Unit) {
        try {
            if (node != null && node.isValid) {
                node.callback(hook, block)
            } else {
                NodeLifetime.withOwner(null, block)
            }
        } catch (t: Throwable) {
            val fields = buildMap<String, Any?> {
                put("event", "system.hook_error")
                put("hook", hook)
                put("system", systemName)
                put("phase", phase.name)
                put("priority", priority)
                put("requiredTypes", requiredTypes.joinToString { it.simpleName ?: it.toString() })
                put("matchingCount", matchingNodes.size)
                if (delta != null) put("delta", delta)
                if (node != null) put("nodePath", node.internalPath())
            }.map { Pair(it.key, it.value) }

            log.error(t = t, *fields.toTypedArray()) { "System hook threw" }
            if (node != null &&
                t !is io.canopy.engine.core.exceptions.CanopyException &&
                t !is Error &&
                t !is kotlinx.coroutines.CancellationException
            ) {
                throw io.canopy.engine.core.exceptions.NodeCleanupException(node.diagnostic("system", hook), t)
            }
            throw t
        }
    }

    enum class UpdatePhase {
        PhysicsPre,
        PhysicsPost,
        FramePre,
        FramePost,
    }
}

/**
 * Helper method that helps to create tree systems in-place
 */
fun createTreeSystem(
    phase: TreeSystem.UpdatePhase,
    priority: Int = 0,
    vararg requiredTypes: KClass<out Node<*>>,
    onRegister: TreeSystem.() -> Unit = {},
    onUnregister: TreeSystem.() -> Unit = {},
    beforeProcess: TreeSystem.(delta: Float) -> Unit = {},
    afterProcess: TreeSystem.(delta: Float) -> Unit = {},
    processNode: TreeSystem.(node: Node<*>, delta: Float) -> Unit = { _, _ -> },
): TreeSystem = object : TreeSystem(phase, priority, *requiredTypes) {
    override fun onRegister() = onRegister.invoke(this)
    override fun onUnregister() = onUnregister.invoke(this)
    override fun beforeProcess(delta: Float) = beforeProcess.invoke(this, delta)
    override fun afterProcess(delta: Float) = afterProcess.invoke(this, delta)
    override fun processNode(node: Node<*>, delta: Float) = processNode.invoke(this, node, delta)
}

inline fun <reified T : TreeSystem> treeSystem(): T = manager<SceneManager>().getSystem(T::class)

inline fun <reified T : TreeSystem> lazyTreeSystem() = lazy { treeSystem<T>() }
