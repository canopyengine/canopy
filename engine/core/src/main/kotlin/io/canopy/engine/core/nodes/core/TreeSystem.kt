package io.canopy.engine.core.nodes.core

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.engine.EngineLogs

abstract class TreeSystem(
    internal val phase: UpdatePhase,
    val priority: Int = 0,
    vararg val requiredTypes: KClass<out Node<*>>,
) {
    // Engine subsystem logger for systems
    private val log = EngineLogs.subsystem("system")

    protected val sceneManager: SceneManager by lazyManager<SceneManager>()

    /** Nodes currently matching the system's type requirements */
    protected val matchingNodes = mutableListOf<Node<*>>()

    private val systemName: String = this::class.simpleName ?: "AnonymousTreeSystem"

    // ===============================
    //         LIFECYCLE HOOKS
    // ===============================

    open fun onRegister() {
        // default: nothing
    }

    open fun onUnregister() {
        // default: nothing
    }

    // ===============================
    //         NODE REGISTRATION
    // ===============================

    fun register(node: Node<*>) {
        if (!acceptsNode(node)) return

        matchingNodes += node

        LogContext.with(
            "system" to systemName,
            "phase" to phase.name,
            "nodePath" to node.path
        ) {
            log.trace("event" to "system.node_added") { "Node added to system" }
        }

        runHook("onNodeAdded", node = node) { onNodeAdded(node) }
    }

    fun unregister(node: Node<*>) {
        if (!matchingNodes.remove(node)) return

        LogContext.with(
            "system" to systemName,
            "phase" to phase.name,
            "nodePath" to node.path
        ) {
            log.trace("event" to "system.node_removed") { "Node removed from system" }
        }

        runHook("onNodeRemoved", node = node) { onNodeRemoved(node) }
    }

    protected open fun onNodeAdded(node: Node<*>) {}
    protected open fun onNodeRemoved(node: Node<*>) {}

    private fun acceptsNode(node: Node<*>) = requiredTypes.any { type ->
        type.isInstance(node) || node.hasChildType(type)
    }

    // ===============================
    //           TICK PROCESSING
    // ===============================

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
                runHook("processNode", delta = delta, node = node) { processNode(node, delta) }
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

    private inline fun runHook(hook: String, delta: Float? = null, node: Node<*>? = null, block: () -> Unit) {
        try {
            block()
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
                if (node != null) put("nodePath", node.path)
            }.map { Pair(it.key, it.value) }

            log.error(t = t, *fields.toTypedArray()) { "System hook threw" }
            throw t // fail fast; change to 'return' if you prefer resilience
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
) {
    object : TreeSystem(phase, priority, *requiredTypes) {
        override fun onRegister() = onRegister.invoke(this)
        override fun onUnregister() = onUnregister.invoke(this)
        override fun beforeProcess(delta: Float) = beforeProcess.invoke(this, delta)
        override fun afterProcess(delta: Float) = afterProcess.invoke(this, delta)
        override fun processNode(node: Node<*>, delta: Float) = processNode.invoke(this, node, delta)
    }
}
