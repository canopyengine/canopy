package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/**
 * Marks a read-only Node primary-constructor property as a reactive input.
 * The matching compiler captures explicit call-site expressions; omitted literal defaults remain static.
 * A caller built without the matching compiler does not install reactive tracking.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class Input

/**
 * Transient, subscription-free construction snapshot retained until installation on the receiver.
 * Reactive dependencies and expressions are owned by the resulting Node input after installation.
 * Captures without dependencies retain only their evaluated value.
 * Capture, installation and source updates must use the serialized game thread.
 */
class InputCapture<T> internal constructor(
    /** Value passed to the constructor before any later source resynchronization. */
    val initialValue: T,
    internal val evaluate: () -> T,
    internal val dependencies: Set<Signal<*>>,
    internal val revisions: Map<Signal<*>, Long>,
)

private class InstalledInput<T>(val source: Computed<T>)

/**
 * Compiler ABI for guarded, receiver-owned Node inputs. This is not an application binding API.
 * Input observation suspends on reusable tree exit and is released on Node destruction.
 * Calls and reactive source updates must use the serialized game thread.
 */
object InputRuntime {
    /**
     * Evaluates once without installing input subscriptions or inheriting the caller's Node ownership.
     * Previously entered, detached sources are rejected rather than captured as static values.
     */
    fun <T> capture(evaluate: () -> T): InputCapture<T> {
        val frame = TrackingContext.pushRecording()
        try {
            val initialValue = TrackingContext.captureInput { NodeLifetime.withOwner(null, evaluate) }
            val action: () -> T = if (frame.isEmpty()) {
                { initialValue }
            } else {
                evaluate
            }
            return InputCapture(initialValue, action, frame.toSet(), frame.revisions.toMap())
        } finally {
            TrackingContext.pop()
        }
    }

    /**
     * Replaces a constructed property's raw value with one receiver-owned derived source.
     * Rejects duplicate installation; pre-entry reads resynchronize changed sources without subscribing.
     */
    fun <T> install(owner: Node<*>, key: String, capture: InputCapture<T>) {
        check(owner.inputStoredValue(key, null) !is InstalledInput<*>) { "Input already installed: $key" }
        val source = Computed(capture, owner)
        try {
            owner.installInputValue(key, InstalledInput(source))
        } catch (failure: Throwable) {
            try {
                source.dispose()
            } catch (cleanup: Throwable) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    /**
     * Returns the raw constructor value before installation, then a tracked derived value.
     * Detached reactive reads calculate afresh without subscriptions; static inputs retain their captured value.
     * Active reads use the receiver-owned cache.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> read(owner: Node<*>, key: String, default: T): T {
        val stored = owner.inputStoredValue(key, default)
        return if (stored is InstalledInput<*>) (stored.source as Computed<T>)() else stored as T
    }
}
