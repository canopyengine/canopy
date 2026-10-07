package io.canopy.engine.core.nodes

import kotlin.reflect.KClass
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import io.canopy.engine.core.exceptions.*
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.input.events.InputEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/**
 * Typed, guarded node facade preserving class-named Kotlin construction and concrete DSL receivers.
 * Engine-owned state is referenced weakly. Destruction invalidates state access, releases owned work,
 * and leaves only immutable identity/diagnostics in retained facades. All operations are game-thread confined.
 * Custom stored values must use [nodeProperty]; runtime queries use the concrete
 * [io.canopy.engine.core.queries.NodeDependency] or [io.canopy.engine.core.queries.GlobalDependency] delegates.
 * The Canopy compiler plugin rejects unmanaged fields.
 */
@CanopyDsl
@Suppress("UNCHECKED_CAST")
abstract class Node<N : Node<N>> protected constructor(
    name: String,
    protected val skipOnSearch: Boolean = false,
    block: N.() -> Unit = {},
) {
    final override fun equals(other: Any?): Boolean = this === other
    final override fun hashCode(): Int = System.identityHashCode(this)

    /** Immutable identity, never reused and available after destruction. */
    val nodeId: Long = identities.incrementAndGet()
    private enum class Lifecycle(val diagnosticText: String) {
        Detached("Detached"),
        Active("Active"),
        Destroying("Destroying"),
        Destroyed("Destroyed"),
    }

    private enum class DispatchKind(val phase: String) {
        Frame("frame"),
        Physics("physics"),
        Input("input"),
    }

    private var lastPath = "/$name"
    private var lifecycle = Lifecycle.Detached
    private var queued = false
    private var metadata = NodeExitMetadata(nodeId, name, lastPath, false)
    private val reference: WeakReference<NodeState>

    companion object {
        private val identities = AtomicLong()
        private val currentParent = ThreadLocal<Node<*>?>()
    }

    init {
        NodeDefinition.validate(javaClass)
        val owner = manager<SceneManager>()
        val payload = NodeState(owner, name) { block(this as N) }
        reference = WeakReference(payload)
        owner.retainState(nodeId, payload)
        try {
            currentParent.get()?.attach(this)
        } catch (failure: Throwable) {
            payload.builder = null
            owner.releaseState(nodeId)
            reference.clear()
            lifecycle = Lifecycle.Destroyed
            throw failure
        }
    }

    /** True while this facade can access its engine state, including reusable detachment. */
    val isValid: Boolean get() = lifecycle != Lifecycle.Destroying &&
        lifecycle != Lifecycle.Destroyed &&
        reference.get() != null

    /** True after permanent destruction starts. */
    val isFreed: Boolean get() = lifecycle == Lifecycle.Destroying || lifecycle == Lifecycle.Destroyed

    /** Whether a safe-boundary deletion is pending. */
    val isQueuedForDeletion: Boolean get() = queued

    /** Immutable exit information; never resolves disposed state. */
    val exitMetadata: NodeExitMetadata get() = metadata

    /** Whether this node currently belongs to an entered tree. */
    val isInsideTree: Boolean get() = isValid && reference.get()?.entered == true

    @PublishedApi
    @JvmSynthetic
    internal fun diagnostic(operation: String, phase: String? = null) =
        NodeDiagnostic(nodeId, javaClass.simpleName, lastPath, lifecycle.diagnosticText, operation, phase)

    @JvmSynthetic
    internal fun state(operation: String): NodeState {
        val current = reference.get()
        if (current == null || lifecycle == Lifecycle.Destroying || lifecycle == Lifecycle.Destroyed) {
            throw NodeDestroyedException(diagnostic(operation))
        }
        return current
    }

    @JvmSynthetic
    internal fun propertyState(property: kotlin.reflect.KProperty<*>, writing: Boolean): NodeState {
        val current = reference.get()
        if (current == null || lifecycle == Lifecycle.Destroying || lifecycle == Lifecycle.Destroyed) {
            val operation = if (writing) "write" else "read"
            throw NodeDestroyedException(diagnostic("$operation property '${property.name}'"))
        }
        return current
    }

    private fun payload(): NodeState = reference.get() ?: throw NodeDestroyedException(diagnostic("cleanup"))

    @JvmSynthetic
    internal fun requireValid(operation: String) {
        state(operation)
    }

    @JvmSynthetic
    internal fun fail(operation: String, message: String): Nothing =
        throw InvalidNodeOperationException(diagnostic(operation), message)

    /** Owning scene manager, available only while the node is valid. */
    protected val sceneManager: SceneManager get() = state("sceneManager").owner

    /** Renaming updates descendant paths and all engine index keys. */
    var name: String
        get() = state("read name").name
        set(value) {
            val s = state("rename")
            if (s.name == value) return
            val p = s.parent?.state("rename child")
            if (p != null && value in p.children) fail("rename", "Sibling '$value' already exists")
            p?.children?.remove(s.name)
            s.name = value
            p?.children?.set(value, this)
            p?.changed()
            refreshPaths()
        }

    /** Absolute path; unavailable after destruction. Diagnostics retain its last value. */
    val path: String get() {
        state("read path")
        return lastPath
    }

    /** Parent facade, or null for a root or reusable detached node. */
    val parent: Node<*>? get() = state("read parent").parent

    /** Read-only membership snapshot, rebuilt only when children change. */
    val children: Map<String, Node<*>> get() = state("read children").publicSnapshot()

    /** Read-only view of local group membership. */
    val groups: Set<String> get() = state("read groups").groupView

    /** Pause eligibility, inherited through actual parents including context wrappers. */
    var processMode: ProcessMode
        get() = state("read processMode").mode
        set(value) {
            state("write processMode").mode = value
        }

    /** Queries pause eligibility. Tree dispatch independently requires entered membership. */
    fun canProcess(paused: Boolean = sceneManager.isPaused): Boolean = eligible(state("canProcess"), paused)

    private fun eligible(start: NodeState, paused: Boolean): Boolean {
        var cursor: NodeState? = start
        while (cursor != null) {
            when (cursor.mode) {
                ProcessMode.Inherit -> cursor = cursor.parentState
                ProcessMode.Pausable -> return !paused
                ProcessMode.WhenPaused -> return paused
                ProcessMode.Always -> return true
                ProcessMode.Disabled -> return false
            }
        }
        return !paused
    }

    /** Adds a local group; only entered nodes are mirrored into manager memberships. */
    fun addGroup(group: String) {
        val s = state("addGroup")
        if (s.groups.add(group) && s.entered) s.owner.addToGroup(group, this)
    }

    /** Removes a local and, when entered, manager group membership. */
    fun removeGroup(group: String) {
        val s = state("removeGroup")
        if (s.groups.remove(group) && s.entered) s.owner.removeFromGroup(group, this)
    }

    /** Applies a group diff without exposing the engine's mutable membership set. */
    fun updateGroups(block: MutableSet<String>.() -> Unit) {
        val s = state("updateGroups")
        val result = s.groups.toMutableSet().apply(block)
        s.groups.clear()
        s.groups.addAll(result)
        if (s.entered) s.owner.updateGroups(this)
    }

    internal var behavior: Behavior<N>?
        get() = state("read behavior").behavior as Behavior<N>?
        set(value) {
            val s = state("attach behavior")
            if (s.behavior === value) return
            if (s.entered && !s.initializing) callback("behavior exit") { s.behavior?.onExitTree() }
            s.behavior = value
            if (s.entered && !s.initializing) callback("behavior enter") { value?.onEnterTree() }
        }

    /** Stores custom state outside the facade. Reads/writes fail with NodeDestroyedException after disposal. */
    protected fun <T> nodeProperty(initial: T): NodeProperty<T> {
        val key = Any()
        state("create property").properties[key] = initial
        return NodeProperty.create(key)
    }

    /** Generic internal ownership boundary; facade delegates retain only immutable lookup metadata. */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : AutoCloseable> lifetimeSlot(key: Any, acquire: () -> T): T {
        val initial = state("read owned resource")
        initial.lifetimeSlots[key]?.let { return it as T }
        check(initial.entered && !initial.exiting) {
            "Node $nodeId must be entered and not exiting to acquire resources"
        }
        val generation = initial.entryGeneration
        val handle = acquire()
        try {
            val current = state("install owned resource")
            check(current.entered && !current.exiting && current.entryGeneration == generation) {
                "Node $nodeId left its entry while acquiring resources"
            }
            current.lifetimeSlots[key] = handle
            return handle
        } catch (failure: Throwable) {
            try {
                handle.close()
            } catch (error: Throwable) {
                if (error !== failure) failure.addSuppressed(error)
            }
            throw failure
        }
    }

    private fun attach(child: Node<*>) {
        val s = state("attach")
        val c = child.state("attach")
        if (s.owner !== c.owner) fail("attach", "Nodes belong to different scene managers")
        if (c.parent != null) fail("attach", "Child already has a parent")
        if (c.name in s.children) fail("attach", "Child '${c.name}' already exists")
        var ancestor: Node<*>? = this
        while (ancestor != null) {
            if (ancestor === child) fail("attach", "Cannot create a hierarchy cycle")
            ancestor = ancestor.state("attach").parent
        }
        s.children[c.name] = child
        s.changed()
        c.parent = this
        c.parentState = s
        child.refreshPaths()
        if (s.entered) s.owner.registerSubtree(child)
    }

    /** Attaches a valid child, entering it only when its parent is entered and it is not a prefab. */
    fun addChild(child: Node<*>) {
        attach(child)
        if (state("addChild").entered && !child.state("addChild").prefab) child.buildTree()
    }

    /** Immediate reusable detachment; completes despite exit or unregister failures. */
    fun removeChild(child: Node<*>) {
        val s = state("removeChild")
        if (child.state("removeChild").parent !== this) fail("removeChild", "Node is not a child")
        val failures = CleanupFailures()
        failures.attempt { child.nodeExitTree() }
        failures.attempt { s.owner.unregisterSubtree(child) }
        child.detachFromParent()
        failures.rethrow()
    }

    /** Removes a direct child resolved by path. */
    fun removeChild(path: String) {
        val child = resolve(path) ?: throw NodeNotFoundException(diagnostic("removeChild"), path)
        removeChild(child)
    }

    /** Class-named DSL attachment operator. */
    operator fun Node<*>.unaryPlus() = addChild(this)

    /** Runtime DSL attachment operator. */
    operator fun plusAssign(child: Node<*>) = addChild(child)

    /** Class-named DSL reusable detachment operator. */
    operator fun Node<*>.unaryMinus() = removeChild(this)

    /** Runtime reusable detachment operator. */
    operator fun minusAssign(child: Node<*>) = removeChild(child)

    /** Attaches a child and returns its parent facade. */
    operator fun Node<*>.plus(node: Node<*>): Node<*> {
        addChild(node)
        return this
    }

    /** Named attachment helper. */
    infix fun child(node: Node<*>) = addChild(node)

    private fun resolve(request: String): Node<*>? {
        state("lookup '$request'")
        var cursor: Node<*>? = if (request.startsWith("$/")) sceneManager.currScene else this
        fun visible(node: Node<*>): Node<*>? = if (!node.skipOnSearch) {
            node
        } else {
            node.payload().snapshot().firstNotNullOfOrNull { visible(it) }
        }
        fun find(node: Node<*>, part: String): Node<*>? {
            val s = node.state("lookup '$request'")
            s.children[part]?.let { return visible(it) }
            return s.snapshot().filter { it.skipOnSearch }.firstNotNullOfOrNull { find(it, part) }
        }
        for (part in request.split('/')) {
            cursor = when (part) {
                "", ".", "$" -> cursor
                ".." -> cursor?.parent?.let { p ->
                    var result: Node<*>? = p
                    while (result?.skipOnSearch == true) result = result.parent
                    result
                }
                else -> cursor?.let { find(it, part) }
            }
            if (cursor == null) return null
        }
        return cursor?.takeIf { it.isValid }
    }

    /** Resolves typed facades through context-transparent paths; invalid receivers always fail. */
    inline fun <reified T : Node<T>> getNode(path: String): T =
        lookup(path, T::class) ?: throw NodeNotFoundException(diagnostic("getNode"), path)

    /** Missing nodes return null; invalid receivers and incompatible requested types still fail. */
    inline fun <reified T : Node<T>> getNodeOrNull(path: String): T? = lookup(path, T::class)

    @PublishedApi
    @JvmSynthetic
    internal fun <T : Node<T>> lookup(path: String, type: KClass<T>): T? {
        val result = resolve(path) ?: return null
        if (!type.isInstance(
                result
            )
        ) {
            fail("lookup '$path'", "Requested ${type.simpleName}, found ${result.javaClass.simpleName}")
        }
        return result as T
    }

    /** Typed indexing shorthand. */
    inline operator fun <reified T : Node<T>> get(path: String): T = getNode(path)

    /** Applies a typed patch without exposing private engine state. */
    inline fun <reified T : Node<T>> patch(path: String, handler: T.() -> Unit) = getNode<T>(path).apply(handler)

    /** Marks this facade as a reusable prefab; skips automatic entry during attachment. */
    fun asPrefab(): N {
        state("asPrefab").prefab = true
        return this as N
    }

    /** Idempotently queues a valid subtree until the next outermost frame/physics boundary. */
    fun queueFree() {
        val s = state("queueFree")
        if (!queued) s.owner.queueFree(this)
    }

    /** Moves an existing child. Same-tree moves preserve lifetimes and system registrations. */
    fun reparent(child: Node<*>, newParent: Node<*>) {
        val s = state("reparent")
        val c = child.state("reparent")
        val destination = newParent.state("reparent")
        if (c.parent !== this) fail("reparent", "Node is not a child")
        if (newParent === this) return
        var ancestor: Node<*>? = newParent
        while (ancestor != null) {
            if (ancestor === child) fail("reparent", "Cannot create a hierarchy cycle")
            ancestor = ancestor.state("reparent").parent
        }
        if (c.name in destination.children || s.owner !== destination.owner) fail("reparent", "Invalid destination")
        val sameTree = s.entered && destination.entered
        if (!sameTree) {
            val failures = CleanupFailures()
            failures.attempt { child.nodeExitTree() }
            failures.attempt { s.owner.unregisterSubtree(child) }
            child.detachFromParent()
            newParent.addChild(child)
            failures.rethrow()
        } else {
            child.detachFromParent()
            newParent.attach(child)
        }
    }

    /** Whether an immediate child has the exact public facade type. */
    fun hasChildType(type: KClass<out Node<*>>) = state("hasChildType").snapshot().any { it::class == type }

    /** Sets this valid facade as its manager's current root. */
    fun asSceneRoot(): Node<*> {
        state("asSceneRoot").owner.currScene = this
        return this
    }

    private fun register(cleanup: () -> Unit, permanent: Boolean): () -> Unit {
        val s = state("register cleanup")
        val callbacks = if (permanent) s.destruction else s.removal
        val entry = CleanupRegistration(cleanup)
        callbacks += entry
        val weak = WeakReference(s)
        return {
            entry.cancel()
            weak.get()?.let { (if (permanent) it.destruction else it.removal).remove(entry) }
        }
    }

    /** Registers once-per-entry cleanup; the returned function cancels ownership without executing it. */
    fun onRemoval(cleanup: () -> Unit): () -> Unit = register(cleanup, false)

    /** Registers cooperative cancellation before starting an owned coroutine job. */
    fun onRemoval(job: Job): () -> Unit = onRemoval { job.cancel() }

    /** Registers exclusive resource disposal on permanent destruction, not reusable detachment. */
    fun onDestroy(cleanup: () -> Unit): () -> Unit = register(cleanup, true)

    /** One-time configuration hook. */
    open fun nodeInit() {}

    /** Custom entry hook; engine traversal is independent of this hook. */
    protected open fun onEnterTree() {}

    /** Custom readiness hook, after children are ready. */
    protected open fun onReady() {}

    /** Custom exit hook; use [exitMetadata] when permanent destruction has invalidated gameplay access. */
    protected open fun onExitTree() {}

    /** Custom eligible frame hook, delta in seconds. */
    protected open fun onUpdate(delta: Float) {}

    /** Custom eligible fixed-step hook, delta in seconds. */
    protected open fun onPhysicsUpdate(delta: Float) {}

    /** Custom eligible input hook. Consuming the event stops remaining descendants and behavior callbacks. */
    protected open fun onInput(event: InputEvent) {}

    /** Builds and enters the class-named DSL once, then runs readiness. */
    fun buildTree() {
        nodeEnterTree()
        nodeReady()
    }

    /** Engine-controlled parent-first entry. */
    fun nodeEnterTree() {
        val s = state("enter tree")
        if (s.entered) return
        s.entryGeneration++
        s.entered = true
        lifecycle = Lifecycle.Active
        if (!s.built) {
            s.built = true
            val previous = currentParent.get()
            currentParent.set(this)
            s.initializing = true
            try {
                callback("initialize") {
                    nodeInit()
                    s.builder?.invoke()
                }
            } finally {
                currentParent.set(previous)
                s.builder = null
                s.initializing = false
            }
        }
        s.owner.registerSubtree(this)
        s.groups.forEach { s.owner.addToGroup(it, this) }
        callback("enter") {
            onEnterTree()
            s.behavior?.onEnterTree()
        }
        for (child in s.snapshot()) if (child.isValid && child.payload().parent === this) child.nodeEnterTree()
    }

    /** Engine-controlled child-first readiness. */
    fun nodeReady() {
        val s = state("ready")
        for (child in s.snapshot()) if (child.isInsideTree) child.nodeReady()
        callback("ready") {
            onReady()
            s.behavior?.onReady()
        }
    }

    /** Engine-controlled child-first exit. All descendants and registrations are attempted on failure. */
    fun nodeExitTree() {
        state("exit tree")
        exitInternal()
    }

    @JvmSynthetic
    internal fun exitInternal() {
        val s = payload()
        if (s.exiting) return
        s.exiting = true
        val failures = CleanupFailures()
        try {
            for (child in s.snapshot()) failures.attempt { child.exitInternal() }
            metadata = NodeExitMetadata(nodeId, s.name, lastPath, isFreed)
            val wasEntered = s.entered
            s.entered = false
            if (!isFreed) lifecycle = Lifecycle.Detached
            if (wasEntered) {
                failures.attempt { cleanup("exit hook") { onExitTree() } }
                failures.attempt { cleanup("behavior exit") { s.behavior?.onExitTree() } }
            }
            drain(s.removal, failures)
            drainSlots(s, failures)
        } finally {
            s.exiting = false
        }
        failures.rethrow()
    }

    @JvmSynthetic
    internal fun releaseResources() {
        val failures = CleanupFailures()
        for (child in payload().snapshot()) failures.attempt { child.releaseResources() }
        drain(payload().removal, failures)
        drainSlots(payload(), failures)
        failures.rethrow()
    }
    private fun drainSlots(state: NodeState, failures: CleanupFailures) {
        val handles = state.lifetimeSlots.values.toList().asReversed()
        state.lifetimeSlots.clear()
        handles.forEach { handle -> failures.attempt { cleanup("release owned resource") { handle.close() } } }
    }
    private fun drain(callbacks: MutableSet<CleanupRegistration>, failures: CleanupFailures) {
        while (callbacks.isNotEmpty()) {
            val batch = callbacks.toList()
            callbacks.clear()
            for (entry in batch) entry.take()?.let { action -> failures.attempt { cleanup("dispose") { action() } } }
        }
    }

    @JvmSynthetic
    internal fun markQueued() {
        state("queueFree")
        queued = true
    }

    @JvmSynthetic
    internal fun beginDestruction() {
        val s = payload()
        metadata = NodeExitMetadata(nodeId, s.name, lastPath, true)
        lifecycle = Lifecycle.Destroying
        queued = false
    }

    @JvmSynthetic
    internal fun finishDestruction() {
        val s = payload()
        val failures = CleanupFailures()
        drainSlots(s, failures)
        drain(s.destruction, failures)
        s.children.clear()
        s.changed()
        s.groups.clear()
        s.properties.clear()
        s.builder = null
        s.behavior = null
        s.parent = null
        s.parentState = null
        s.owner.releaseState(nodeId)
        reference.clear()
        lifecycle = Lifecycle.Destroyed
        failures.rethrow()
    }

    @JvmSynthetic
    internal fun detachFromParent() {
        val s = payload()
        s.parent?.payload()?.let { p ->
            p.children.remove(s.name)
            p.changed()
        }
        s.parent = null
        s.parentState = null
        if (isValid) refreshPaths()
    }

    @JvmSynthetic
    internal fun owningManager(): SceneManager = payload().owner

    internal fun isSearchTransparent(): Boolean = skipOnSearch

    @JvmSynthetic
    internal fun childSnapshot(): List<Node<*>> = payload().snapshot()

    @JvmSynthetic
    internal fun internalGroups(): Set<String> = payload().groups

    @JvmSynthetic
    internal fun internalPath(): String = lastPath

    @JvmSynthetic
    internal fun refreshPaths() {
        val s = payload()
        lastPath = s.parent?.let { "${it.internalPath()}/${s.name}" } ?: "/${s.name}"
        s.owner.reindex(this)
        for (child in s.snapshot()) child.refreshPaths()
    }

    /** Validated frame entrypoint. Engine processing is not overridable. */
    fun nodeUpdate(delta: Float) {
        state("frame update")
        dispatchUpdate(delta)
    }

    /** Validated physics entrypoint. */
    fun nodePhysicsUpdate(delta: Float) {
        state("physics update")
        dispatchPhysicsUpdate(delta)
    }

    /**
     * Validated input entrypoint. Traverses node, children in tree order, then behavior. A consumed event stops
     * the remaining traversal; use a fresh event for each independent dispatch.
     */
    fun nodeInput(event: InputEvent) {
        state("input")
        dispatchInput(event)
    }

    @JvmSynthetic
    internal fun dispatchUpdate(delta: Float) = dispatch(DispatchKind.Frame, delta, null)

    @JvmSynthetic
    internal fun dispatchPhysicsUpdate(delta: Float) = dispatch(DispatchKind.Physics, delta, null)

    @JvmSynthetic
    internal fun dispatchInput(event: InputEvent) = dispatch(DispatchKind.Input, 0f, event)
    private fun dispatch(kind: DispatchKind, delta: Float, event: InputEvent?) {
        val s = reference.get() ?: return
        dispatchState(kind, delta, event, s)
    }

    private fun dispatchState(kind: DispatchKind, delta: Float, event: InputEvent?, s: NodeState) {
        if (!s.entered || isFreed || (kind == DispatchKind.Input && event?.isHandled == true)) return
        val phase = kind.phase
        if (eligible(s, s.owner.isPaused)) {
            callback(phase) {
                when (kind) {
                    DispatchKind.Frame -> onUpdate(delta)
                    DispatchKind.Physics -> onPhysicsUpdate(delta)
                    DispatchKind.Input -> onInput(event!!)
                }
            }
        }
        for (child in s.snapshot()) {
            if (kind == DispatchKind.Input && event?.isHandled == true) return
            val childState = child.reference.get() ?: continue
            if (childState.parent === this) child.dispatchState(kind, delta, event, childState)
        }
        if (!s.entered ||
            isFreed ||
            !eligible(s, s.owner.isPaused) ||
            (kind == DispatchKind.Input && event?.isHandled == true)
        ) {
            return
        }
        val behavior = s.behavior ?: return
        callback(phase) {
            when (kind) {
                DispatchKind.Frame -> behavior.onUpdate(delta)
                DispatchKind.Physics -> behavior.onPhysicsUpdate(delta)
                DispatchKind.Input -> behavior.onInput(event!!)
            }
        }
    }

    @JvmSynthetic
    internal inline fun callback(phase: String, action: () -> Unit) {
        try {
            NodeLifetime.withOwner(this, action)
        } catch (error: Throwable) {
            if (error is Error || error is CancellationException || error is CanopyException) throw error
            throw NodeCallbackException(diagnostic("callback", phase), error)
        }
    }
    private inline fun cleanup(phase: String, action: () -> Unit) {
        try {
            NodeLifetime.withOwner(null, action)
        } catch (error: Throwable) {
            if (error is Error || error is CancellationException || error is CanopyException) throw error
            throw NodeCleanupException(diagnostic("cleanup", phase), error)
        }
    }
}
