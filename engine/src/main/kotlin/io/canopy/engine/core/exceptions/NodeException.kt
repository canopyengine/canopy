package io.canopy.engine.core.exceptions

import java.util.Collections

/** Base for contextual Canopy failures. Original failures are preserved as causes. */
open class CanopyException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Immutable diagnostics. Never retains a node, its state, resources or callbacks. */
data class NodeDiagnostic(
    val nodeId: Long,
    val nodeType: String,
    val lastPath: String,
    val state: String,
    val operation: String,
    val phase: String? = null,
)

/** Base for engine node failures with diagnostics that survive state disposal. */
open class NodeException(val diagnostic: NodeDiagnostic, message: String, cause: Throwable? = null) :
    CanopyException(
        "$message [nodeId=${diagnostic.nodeId}, type=${diagnostic.nodeType}, " +
            "path=${diagnostic.lastPath}, state=${diagnostic.state}, operation=${diagnostic.operation}, " +
            "phase=${diagnostic.phase}]",
        cause
    )

/** Access to permanently invalidated node state. */
class NodeDestroyedException(diagnostic: NodeDiagnostic) : NodeException(diagnostic, "Node is destroyed")

/** A throwing lookup could not resolve the requested path. */
class NodeNotFoundException(diagnostic: NodeDiagnostic, val requestedPath: String) :
    NodeException(diagnostic, "Node path '$requestedPath' was not found")

/** Invalid hierarchy, lifecycle, ownership or typed access. */
class InvalidNodeOperationException(diagnostic: NodeDiagnostic, message: String) : NodeException(diagnostic, message)

/** A user lifecycle, behavior or system callback failed. */
class NodeCallbackException(diagnostic: NodeDiagnostic, cause: Throwable) :
    NodeException(diagnostic, "Node callback failed", cause)

/** An exit, unregister or resource disposal operation failed. */
class NodeCleanupException(diagnostic: NodeDiagnostic, cause: Throwable) :
    NodeException(diagnostic, "Node cleanup failed", cause)

/** Unsafe custom node definition detected before allocation or registration. */
class InvalidNodeDefinitionException(val nodeType: String, fields: List<String>) :
    CanopyException(
        "CANOPY_UNMANAGED_NODE_STATE: $nodeType contains unmanaged fields ${fields.joinToString()}; " +
            "use 'by nodeProperty(...)' and apply the io.github.canopyengine.compiler Gradle plugin"
    ) {
    /** Immutable field names; mutating diagnostics cannot alter the cached validation result. */
    val fields: List<String> = Collections.unmodifiableList(fields.toList())
}
