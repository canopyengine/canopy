package io.canopy.engine.core.flows.events

import kotlin.reflect.KProperty
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/**
 * Lazily evaluated cached computation. Creation ownership is independent of the first reader.
 * Declare `val hungry by computed { hunger < 20 }` and read it as an ordinary property.
 * Detached reads calculate without retaining subscriptions; reentered reads renew observation.
 * Node-local observation suspends on tree exit; later entered reads renew it. Destruction releases the calculation.
 * Initial source writes are reconciled before caching, up to 1024 evaluations. Derivations suppress ambient ownership
 * and should be pure. In-place edits to returned Canopy observable collections propagate through the derived cache.
 * Upstream source disconnection clears downstream listeners; read again after source recovery to rearm.
 * All operations are game-thread confined.
 */
class Computed<T> private constructor(block: () -> T, owner: Node<*>?, seed: InputCapture<T>?) {
    constructor(block: () -> T, owner: Node<*>? = NodeLifetime.current()) : this(block, owner, null)

    internal constructor(capture: InputCapture<T>, owner: Node<*>) : this(capture.evaluate, owner, capture)

    private val lifetime = SourceLifetime(owner)
    private val seededInput = seed != null
    private var action: (() -> T)? = block
    private var output: Signal<T>? = seed?.let { Signal(it.initialValue, owner = null) }
    private var pendingSeed = seed
    private val subscriptions = DependencySubscriptions()
    private var recomputing = false
    private var evaluating = false
    private var rerunRequested = false
    private var failed = false
    private var sourceDisconnected = false
    private var seedActivatedBeforeResume = false
    private var readyResyncPending = false

    init {
        try {
            lifetime.bind(
                ::dispose,
                ::suspendObservation,
                ::resumeObservation,
                if (seededInput) ::readyObservation else null
            )
            if (seed != null && lifetime.active) activateSeed()
        } catch (failure: Throwable) {
            try {
                dispose()
            } catch (cleanup: Throwable) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    private fun signal(): Signal<T> {
        lifetime.check("read computed")
        check(lifetime.canObserve) { "Enter the owning Node before observing a computed flow or connection" }
        if (pendingSeed != null && lifetime.active) activateSeed()
        if (pendingSeed != null && !lifetime.active) refreshPendingSeed()
        if (failed || sourceDisconnected) recompute()
        output?.let { return it }
        val result = initialValue()
        lifetime.check("finish computed")
        check(lifetime.canObserve) { "The owning Node exited during its calculation" }
        return Signal(result, owner = null).also { output = it }
    }

    /** Read-only changes stream; cancel owned collector jobs on removal. */
    val flow get() = signal().flow

    /** Installs read-only derived state in guarded Node storage. */
    operator fun provideDelegate(thisRef: Node<*>, property: KProperty<*>): ComputedProperty<T> =
        ComputedProperty(thisRef.reactiveSlot(this))

    /** Native read-only delegation outside Nodes preserves the source's explicit lifetime. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = invoke()

    /** Reads tracked derived state while active; detached reads are fresh, pure, unobserved calculations. */
    operator fun invoke(): T {
        lifetime.check("read computed")
        check(!evaluating) { "Circular computed dependency" }
        if (lifetime.canObserve) return signal()()
        check(!TrackingContext.isCapturingInput) { "Cannot capture an inactive reactive source" }
        evaluating = true
        return try {
            untrack { action!!.invoke() }
        } finally {
            evaluating = false
        }
    }

    private fun suspendObservation() {
        subscriptions.clear()
        output?.dispose()
        output = null
        pendingSeed = null
        sourceDisconnected = false
        failed = false
        seedActivatedBeforeResume = false
        readyResyncPending = false
    }

    private fun resumeObservation() {
        if (pendingSeed != null) {
            activateSeed()
        } else if (output != null && !seedActivatedBeforeResume) {
            recompute()
        } else if (seededInput && !seedActivatedBeforeResume) {
            readyResyncPending = true
        }
        seedActivatedBeforeResume = false
    }

    private fun readyObservation() {
        if (!readyResyncPending || !lifetime.active) return
        readyResyncPending = false
        if (output != null) return
        val result = initialValue()
        lifetime.check("finish computed")
        check(lifetime.canObserve) { "The owning Node exited during its calculation" }
        output = Signal(result, owner = null)
    }

    private fun activateSeed() {
        val seed = pendingSeed ?: return
        pendingSeed = null
        seedActivatedBeforeResume = true
        if (output == null) output = Signal(seed.initialValue, owner = null)
        try {
            subscriptions.reconcile(seed.dependencies, ::recompute, ::dependencyDisconnected)
            if (seed.revisions.any { (source, revision) -> source.revision != revision }) recompute()
        } catch (failure: Throwable) {
            failed = true
            throw failure
        }
    }

    private fun refreshPendingSeed() {
        var seed = pendingSeed ?: return
        var passes = 0
        while (seed.revisions.any { (source, revision) -> source.revision != revision }) {
            check(++passes <= 1024) { "Computed did not converge after 1024 recomputations" }
            val frame = TrackingContext.pushRecording()
            val result = try {
                TrackingContext.captureInput { evaluateAction() }
            } finally {
                TrackingContext.pop()
            }
            lifetime.check("finish computed")
            seed = InputCapture(result, action!!, frame.toSet(), frame.revisions.toMap())
            pendingSeed = seed
            output?.update { result }
        }
    }

    private fun dependencyDisconnected() {
        sourceDisconnected = true
        output?.clear()
    }

    private fun initialValue(): T {
        var passes = 0
        while (true) {
            check(++passes <= 1024) { "Computed did not converge after 1024 recomputations" }
            val frame = TrackingContext.pushRecording()
            val result = try {
                evaluateAction()
            } finally {
                TrackingContext.pop()
            }
            if (frame.revisions.all { (source, revision) -> source.revision == revision }) {
                updateDependencies(frame)
                return result
            }
        }
    }

    /** Connects using inferred lexical Node ownership, or the ambient scope outside Node code. */
    infix fun connect(listener: (T) -> Unit) = signal().connect(listener)

    /** Connects using explicit tree-lifetime ownership. */
    fun connect(owner: Node<*>?, listener: (T) -> Unit) = signal().connect(owner, listener)

    /** Disconnects callback storage and ownership registrations. */
    infix fun disconnect(listener: (T) -> Unit) = signal().disconnect(listener)
    private fun recompute() {
        if (lifetime.disposed || !lifetime.active) return
        if (recomputing) {
            rerunRequested = true
            return
        }
        recomputing = true
        try {
            var passes = 0
            do {
                rerunRequested = false
                check(++passes <= 1024) { "Computed did not converge after 1024 recomputations" }
                sourceDisconnected = false
                val frame = TrackingContext.pushRecording()
                val result = runBlock(frame)
                if (rerunRequested || frame.revisions.any { (source, revision) -> source.revision != revision }) {
                    rerunRequested = true
                    continue
                }
                output?.update { result }
                failed = false
            } while (rerunRequested)
        } catch (failure: Throwable) {
            failed = true
            throw failure
        } finally {
            recomputing = false
        }
    }
    private fun runBlock(frame: TrackingContext.RecordedFrame): T {
        var failure: Throwable? = null
        try {
            return evaluateAction()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            TrackingContext.pop()
            try {
                updateDependencies(frame)
            } catch (error: Throwable) {
                if (failure == null) throw error
                if (error !== failure) failure.addSuppressed(error)
            }
        }
    }
    private fun evaluateAction(): T {
        lifetime.check("compute")
        check(!evaluating) { "Circular computed dependency" }
        evaluating = true
        return try {
            TrackingContext.captureInput { NodeLifetime.withOwner(null) { action!!.invoke() } }
        } finally {
            evaluating = false
        }
    }
    private fun updateDependencies(next: Set<Signal<*>>) {
        if (lifetime.disposed || !lifetime.active) return
        subscriptions.reconcile(next, ::recompute, ::dependencyDisconnected)
    }

    /** Idempotently releases dependency callbacks, cached output and captured calculation closure. */
    fun dispose() {
        if (!lifetime.dispose()) return
        suspendObservation()
        action = null
    }
}

/** Creates a computation capturing its creation owner. */
fun <T> computed(block: () -> T) = Computed(block)

/** Creates a computation with explicit ownership; null means shared. */
fun <T> computed(owner: Node<*>?, block: () -> T) = Computed(block, owner)
