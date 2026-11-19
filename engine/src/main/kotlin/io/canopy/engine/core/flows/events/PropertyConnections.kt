package io.canopy.engine.core.flows.events

import kotlin.reflect.KProperty0
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/**
 * Subscribes to a reactive property through a bound Kotlin reference, for example
 * `(aquarium::fishNum).connect { count -> ... }`. The reference retains its normal Kotlin value type.
 *
 * Reads once to establish dependencies, without calling [listener]. Subsequent reactive invalidations
 * reread the property, then notify synchronously when its result differs. Supported observable collections
 * notify for identity switches or edits, not unrelated invalidations. Same-reference results of unknown
 * mutability still notify. Listener reads do not become dependencies.
 * Getter-side feedback is bounded at 1024 evaluations.
 * Ordinary stored, non-reactive properties reject subscriptions.
 *
 * Inside a Node, the matching compiler infers the listening Node, including ordinary methods and callbacks.
 * Outside Node code, the current managed callback supplies ownership; otherwise retain the handle. Disconnect
 * to release it sooner. A source exiting, clearing its callbacks or being disposed also disconnects it;
 * reconnect after reentry. Use the explicit-owner overload to subscribe outside a Node callback.
 */
infix fun <T> KProperty0<T>.connect(listener: (T) -> Unit): EventDisconnectHandler =
    connect(NodeLifetime.current(), listener)

/**
 * Subscribes with cleanup when [owner] exits, or shared ownership when it is null.
 * The source must permit observation; previously entered, detached sources cannot be connected.
 * All operations, including disconnection, are game-thread confined.
 */
fun <T> KProperty0<T>.connect(owner: Node<*>?, listener: (T) -> Unit): EventDisconnectHandler {
    owner?.requireValid("connect property '$name'")
    val observer = PropertyConnection(this, listener)
    val connection = EventDisconnectHandler(observer::dispose, observer)
    try {
        observer.start(connection::disconnect)
        return NodeLifetime.withOwner(owner) { connection.owned() }
    } catch (failure: Throwable) {
        connection.disconnect()
        throw failure
    }
}

/** Reuses dependency tracking rather than reflection, source registries or compiler-generated accessors. */
private class PropertyConnection<T>(property: KProperty0<T>, listener: (T) -> Unit) {
    private object UnreadResult

    private var property: KProperty0<T>? = property
    private var listener: ((T) -> Unit)? = listener
    private var disconnected: (() -> Unit)? = null
    private var lastValue: Any? = UnreadResult
    private var lastCollectionRevision: Long? = null
    private val subscriptions = DependencySubscriptions()
    private var running = false
    private var pending = false

    fun start(disconnect: () -> Unit) {
        disconnected = disconnect
        pending = true
        drain(notify = false)
    }

    private fun evaluate(notify: Boolean) {
        val reference = property ?: return
        val frame = TrackingContext.pushRecording()
        val value = try {
            NodeLifetime.withOwner(null) { reference.get() }
        } finally {
            TrackingContext.pop()
        }
        if (property == null) return
        require(frame.isNotEmpty()) {
            "Property '${reference.name}' has no observable dependencies. " +
                "Use a signal/computed property and enter its source Node before reconnecting after exit."
        }
        subscriptions.reconcile(frame, ::changed, disconnected)
        if (property == null) return
        if (frame.revisions.any { (source, revision) -> source.revision != revision }) {
            pending = true
            return
        }
        val previous = lastValue
        val collectionRevision = (value as? ObservableCollectionVersion)?.mutationRevision
        val shouldNotify = notify &&
            (previous === UnreadResult || resultsDiffer(previous, value, lastCollectionRevision, collectionRevision))
        lastValue = value
        lastCollectionRevision = collectionRevision
        if (shouldNotify) untrack { listener?.invoke(value) }
    }

    private fun resultsDiffer(previous: Any?, current: Any?, previousRevision: Long?, currentRevision: Long?): Boolean {
        if (previous === current) {
            if (current is ObservableCollectionVersion) return previousRevision != currentRevision
            return !isStableScalar(current)
        }
        if (previous is ObservableCollectionVersion || current is ObservableCollectionVersion) return true
        return untrack { previous != current }
    }

    private fun isStableScalar(value: Any?): Boolean = when (value) {
        null, Unit -> true
        is Boolean, is Char, is Byte, is Short, is Int, is Long -> true
        is Float, is Double, is String, is Enum<*> -> true
        else -> false
    }

    private fun changed() {
        if (property == null) return
        pending = true
        drain(notify = true)
    }

    private fun drain(notify: Boolean) {
        if (running) return
        running = true
        try {
            var passes = 0
            while (pending && property != null) {
                pending = false
                check(++passes <= 1024) { "Property connection did not converge after 1024 evaluations" }
                evaluate(notify)
            }
        } catch (failure: Throwable) {
            pending = false
            throw failure
        } finally {
            running = false
        }
    }

    fun dispose() {
        property = null
        listener = null
        disconnected = null
        lastValue = UnreadResult
        lastCollectionRevision = null
        pending = false
        subscriptions.clear()
    }
}
