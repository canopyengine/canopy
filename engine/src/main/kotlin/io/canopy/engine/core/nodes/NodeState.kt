package io.canopy.engine.core.nodes

import java.util.Collections
import io.canopy.engine.core.managers.SceneManager

/** Engine-owned payload. Public facades reference this weakly and cannot keep it alive after destruction. */
internal class NodeState(val owner: SceneManager, var name: String, var builder: (() -> Unit)?) {
    var parent: Node<*>? = null
    var parentState: NodeState? = null
    val children = linkedMapOf<String, Node<*>>()
    var childSnapshot: List<Node<*>>? = null
    var publicChildren: Map<String, Node<*>>? = null
    val groups = linkedSetOf<String>()
    val groupView: Set<String> = Collections.unmodifiableSet(groups)
    val properties = mutableMapOf<Any, Any?>()
    val removal = linkedSetOf<CleanupRegistration>()
    val destruction = linkedSetOf<CleanupRegistration>()
    var behavior: Behavior<*>? = null
    var initializing = false
    var built = false
    var entered = false
    var prefab = false
    var mode = ProcessMode.Inherit
    var exiting = false

    fun snapshot(): List<Node<*>> = childSnapshot ?: children.values.toList().also { childSnapshot = it }
    fun publicSnapshot(): Map<String, Node<*>> = publicChildren
        ?: Collections.unmodifiableMap(LinkedHashMap(children)).also { publicChildren = it }
    fun changed() {
        childSnapshot = null
        publicChildren = null
    }
}

/** A retained cancellation function does not retain a disposed callback or the owning payload. */
internal class CleanupRegistration(private var callback: (() -> Unit)?) {
    fun take(): (() -> Unit)? = callback.also { callback = null }
    fun cancel() {
        callback = null
    }
}

/** Immutable information available to exit hooks, including after gameplay access is invalidated. */
data class NodeExitMetadata(val nodeId: Long, val name: String, val path: String, val destroying: Boolean)
