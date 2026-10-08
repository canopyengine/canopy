package io.canopy.engine.ui

import io.canopy.engine.core.flows.events.EventDisconnectHandler
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.TrackingContext
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.MouseButtonEvent

/** Marks a declarative builder receiver for the Canopy compiler's selective expression lowering. */
@Target(AnnotationTarget.CLASS)
annotation class DeclarativeUi

/** Retained UI node; state and lifecycle use ordinary guarded engine nodes. */
class UiElement internal constructor(kind: UiKind, root: UiRoot, name: String) : Node<UiElement>(name) {
    /** Primitive or universal layout container. */
    val kind by nodeProperty(kind)
    internal val root by nodeProperty(root)

    /** Universal sizing and arrangement policy. */
    private var storedStyle by nodeProperty(UiStyle())
    var style: UiStyle
        get() = storedStyle
        set(value) {
            if (storedStyle != value) {
                storedStyle = value
                root.dirty = true
            }
        }

    /** Accessible semantic role derived from the shared primitive. */
    val role: String get() = if (kind == UiKind.Button) {
        "button"
    } else if (kind == UiKind.Text) {
        "text"
    } else {
        "container"
    }

    /** Accessible label shared by terminal and graphical backends. */
    val accessibleLabel: String get() = text

    /** Current text or button label. */
    private var storedText by nodeProperty("")
    var text: String
        get() = storedText
        set(value) {
            if (storedText != value) {
                storedText = value
                root.dirty = true
            }
        }

    /** Disabled actions retain layout but cannot receive focus or activate. */
    var enabled by nodeProperty(true)

    /** Most recent logical layout bounds. */
    var bounds by nodeProperty(UiRect(0.0, 0.0, 0.0, 0.0))
        internal set
    internal var measurementKey by nodeProperty<List<Any?>>(emptyList())
    internal var measured by nodeProperty(UiSize(0.0, 0.0))
    internal var action by nodeProperty<(() -> Unit)?>(null)
    internal var content by nodeProperty<UiScope?>(null)
    override fun nodeInit() {
        markChildrenManaged()
    }

    /** Invokes an enabled visible action without changing simulation pause state. */
    fun activate() {
        if (enabled &&
            isInsideTree &&
            isVisibleInTree
        ) {
            io.canopy.engine.core.flows.events.untrack { NodeLifetime.withOwner(this) { action?.invoke() } }
        }
    }
}

internal class UiRegion(root: UiRoot, name: String) : Node<UiRegion>(name, skipOnSearch = true) {
    internal val root by nodeProperty(root)
    internal var content by nodeProperty<UiScope?>(null)
    override fun nodeInit() {
        markChildrenManaged()
    }
}

/** A retained declarative tree initialized once; viewport changes recompute its universal layout. */
class UiRoot(name: String = "UiRoot", block: UiScope.() -> Unit) : Node<UiRoot>(name) {
    /** Root sizing policy; defaults to filling the current viewport. */
    var style by nodeProperty(UiStyle(width = UiLength.Fill, height = UiLength.Fill))

    /** Most recent root bounds, used consistently by rendering and pointer clipping. */
    var bounds by nodeProperty(UiRect(0.0, 0.0, 0.0, 0.0))
        internal set
    private var viewportBounds by nodeProperty(UiRect(0.0, 0.0, 0.0, 0.0))

    /** Common presentation layer; overlays paint above content regardless of content zIndex. */
    var layer by nodeProperty(UiLayer.Content)

    /** Root vertical anchoring, including bottom overlays through End. */
    var verticalAlignment by nodeProperty(UiAlignment.Start)

    /** Root horizontal anchoring. */
    var horizontalAlignment by nodeProperty(UiAlignment.Start)

    /** Painter order for overlays; higher values paint above lower values. */
    var zIndex by nodeProperty(0)
    private var initializer by nodeProperty<(UiScope.() -> Unit)?>(block)
    internal var content by nodeProperty<UiScope?>(null)

    /** Focus is retained by element identity across keyed reorder. */
    var focusedElement by nodeProperty<UiElement?>(null)
        private set
    internal var dirty by nodeProperty(true)
    private var lastLayout by nodeProperty<List<Any?>>(emptyList())
    private var focusLease by nodeProperty<AutoCloseable?>(null)

    override fun nodeInit() {
        markChildrenManaged()
        content = UiScope(this, this).also { scope -> scope.compose { initializer?.invoke(this) } }
        initializer = null
        val scope = content
        onDestroy { scope?.dispose() }
    }
    override fun onEnterTree() {
        manager<UiManager>().mount(this)
        val scope = content
        val lease = manager<InputFocus>().register(
            owner = this,
            priority = zIndex.coerceAtMost(Int.MAX_VALUE - 1),
            capturesGameplay = { focusedElement != null && isVisibleInTree },
            handler = ::input
        )
        focusLease = lease
        scope?.resume()
        onRemoval {
            scope?.suspend()
            lease.close()
        }
    }
    override fun onExitTree() {
        if (isValid) focus(null)
        manager<UiManager>().unmount(this)
    }

    /** Moves keyboard focus to a visible entered button in this tree, or releases focus. */
    fun focus(element: UiElement?) {
        require(element == null || element.root === this && element.kind == UiKind.Button)
        if (element != null) manager<UiManager>().releaseOtherFocus(this)
        focusedElement = element?.takeIf { it.enabled && it.isInsideTree && it.isVisibleInTree }
        dirty = true
    }
    internal fun validateFocus() {
        val focused = focusedElement ?: return
        if (!focused.isValid || !focused.enabled || !focused.isInsideTree || !focused.isVisibleInTree) focus(null)
    }
    private fun input(event: InputEvent): Boolean {
        validateFocus()
        if (!isVisibleInTree || event.state != InputState.JustPressed) return false
        if (event is MouseButtonEvent) {
            val hit = content?.elements()?.flatMap { descendants(it) }?.lastOrNull {
                it.kind == UiKind.Button &&
                    it.enabled &&
                    it.isInsideTree &&
                    it.isVisibleInTree &&
                    hit(it, event.screenPos.x.toDouble(), event.screenPos.y.toDouble())
            } ?: return false
            focus(hit)
            hit.activate()
            return true
        }
        if (event !is KeyInputEvent) return false
        val buttons = content?.elements()?.flatMap { descendants(it) }
            ?.filter { it.kind == UiKind.Button && it.enabled && it.isInsideTree && it.isVisibleInTree }.orEmpty()
        return when (event.key) {
            Key.DOWN, Key.RIGHT -> {
                if (buttons.isEmpty()) {
                    false
                } else {
                    focus(buttons[(buttons.indexOf(focusedElement) + 1) % buttons.size])
                    true
                }
            }
            Key.UP, Key.LEFT -> {
                if (buttons.isEmpty()) {
                    false
                } else {
                    focus(buttons[(buttons.indexOf(focusedElement) - 1 + buttons.size) % buttons.size])
                    true
                }
            }
            Key.ESCAPE -> if (focusedElement != null) {
                focus(null)
                true
            } else {
                false
            }
            Key.ENTER, Key.SPACE -> focusedElement?.let {
                it.activate()
                true
            } ?: false
            else -> false
        }
    }
    private fun hit(element: UiElement, x: Double, y: Double): Boolean {
        var clip = element.bounds.intersect(viewportBounds)
        if (style.clip) clip = clip.intersect(bounds)
        var ancestor = element.parent
        while (ancestor != null) {
            if (ancestor is UiElement && ancestor.style.clip) clip = clip.intersect(ancestor.bounds)
            ancestor = ancestor.parent
        }
        return x >= clip.x && x < clip.x + clip.width && y >= clip.y && y < clip.y + clip.height
    }
    private fun descendants(element: UiElement): List<UiElement> =
        listOf(element) + element.content?.elements().orEmpty().flatMap(::descendants)

    private fun layoutSnapshot(element: UiElement): List<Any?> = listOf(element.nodeId, element.style, element.text) +
        element.content?.elements().orEmpty().flatMap(::layoutSnapshot)

    /** Layouts and paints using backend metrics, independently of gameplay pause eligibility. */
    fun render(backend: UiBackend, viewport: UiSize) {
        validateFocus()
        if (!isVisibleInTree) return
        viewportBounds = UiRect(0.0, 0.0, viewport.width, viewport.height)
        val layoutKey = listOf(backend, viewport, style, verticalAlignment, horizontalAlignment) +
            content?.elements().orEmpty().flatMap(::layoutSnapshot)
        if (layoutKey != lastLayout) {
            UiLayoutEngine.layout(this, backend, viewport)
            lastLayout = layoutKey
        }
        val rootClip = if (style.clip) viewportBounds.intersect(bounds) else viewportBounds
        content?.elements()?.forEach {
            UiLayoutEngine.paint(
                it,
                backend,
                rootClip,
                focusedElement
            )
        }
        dirty = false
    }
}

/** Shared safe-boundary scheduling and rendering, driven on every app frame including paused frames. */
class UiManager(var backend: UiBackend? = null) : Manager {
    private val roots = linkedSetOf<UiRoot>()
    private val pending = linkedSetOf<UiObserver>()
    private var lastFrame: List<Any?>? = null

    /** Current host dimensions in the backend's logical units. */
    var viewport = UiSize(0.0, 0.0)
        private set
    internal fun releaseOtherFocus(root: UiRoot) {
        roots.filter { it !== root && it.isValid }.forEach { it.focus(null) }
    }
    internal fun mount(root: UiRoot) {
        roots += root
    }
    internal fun unmount(root: UiRoot) {
        roots -= root
    }
    internal fun enqueue(observer: UiObserver) {
        pending += observer
    }
    internal fun cancel(observer: UiObserver) {
        pending -= observer
    }
    override fun onResize(width: Int, height: Int) {
        viewport = UiSize(width.coerceAtLeast(0).toDouble(), height.coerceAtLeast(0).toDouble())
        roots.filter { it.isValid }.forEach { it.dirty = true }
    }
    override fun onUpdate(delta: Float) {
        val batch = pending.toList()
        pending.clear()
        var failure: Throwable? = null
        batch.forEach { observer ->
            try {
                observer.run()
            } catch (error: Throwable) {
                if (failure == null) {
                    failure = error
                } else if (failure !== error) {
                    failure!!.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
        renderNow()
    }

    /** Paints the current retained tree immediately without dispatching gameplay or reactive updates. */
    fun renderNow() {
        backend?.let { renderer ->
            val logicalViewport = renderer.viewport(viewport)
            val activeRoots = roots.toList().filter {
                it.isValid && it.isInsideTree
            }.sortedWith(compareBy<UiRoot> { it.layer.ordinal }.thenBy { it.zIndex })
            activeRoots.forEach { it.validateFocus() }
            val snapshot = listOf(renderer, logicalViewport) + activeRoots.flatMap(::snapshot)
            if (snapshot == lastFrame) return
            renderer.begin(logicalViewport)
            try {
                activeRoots.forEach { it.render(renderer, logicalViewport) }
            } finally {
                renderer.end()
            }
            lastFrame = snapshot
        }
    }
    private fun snapshot(root: UiRoot): List<Any?> = listOf(
        root.nodeId,
        root.isVisibleInTree,
        root.style,
        root.verticalAlignment,
        root.horizontalAlignment,
        root.focusedElement?.nodeId,
        root.zIndex
    ) + root.content?.elements().orEmpty().flatMap(::snapshot)
    private fun snapshot(element: UiElement): List<Any?> =
        listOf(element.nodeId, element.isVisibleInTree, element.style, element.text, element.enabled) +
            element.content?.elements().orEmpty().flatMap(::snapshot)
    override fun onExit() {
        lastFrame = null
        pending.toList().forEach { it.dispose() }
        pending.clear()
        roots.clear()
    }
}

internal class UiObserver(private val owner: Node<*>, private var action: (() -> Unit)?) {
    private var dependencies = emptySet<Signal<*>>()
    private val handlers = mutableMapOf<Signal<*>, EventDisconnectHandler>()
    private var disposed = false
    private var suspended = false
    private var cancelRemoval: (() -> Unit)? = null
    private var cancelDestruction: (() -> Unit)? = null
    init {
        cancelRemoval = owner.onRemoval(::suspend)
        cancelDestruction = owner.onDestroy(::dispose)
        try {
            run()
        } catch (failure: Throwable) {
            dispose()
            throw failure
        }
    }
    fun run() {
        if (disposed || suspended || !owner.isValid) return
        val frame = TrackingContext.push()
        try {
            NodeLifetime.withOwner(owner) { action?.invoke() }
        } finally {
            TrackingContext.pop()
            if (!disposed && !suspended && owner.isValid) {
                for (dep in dependencies - frame) handlers.remove(dep)?.disconnect()
                for (dep in frame - dependencies) {
                    handlers[dep] = NodeLifetime.withOwner(null) { dep.connect { manager<UiManager>().enqueue(this) } }
                }
                dependencies = frame
            }
        }
    }
    fun suspend() {
        if (disposed || suspended) return
        suspended = true
        cancelRemoval?.invoke()
        cancelRemoval = null
        handlers.values.forEach { it.disconnect() }
        handlers.clear()
        dependencies = emptySet()
        manager<UiManager>().cancel(this)
    }
    fun resume() {
        if (!disposed && suspended) {
            suspended = false
            cancelRemoval = owner.onRemoval(::suspend)
            run()
        }
    }
    fun dispose() {
        if (disposed) return
        disposed = true
        cancelRemoval?.invoke()
        cancelRemoval = null
        cancelDestruction?.invoke()
        cancelDestruction = null
        action = null
        handlers.values.forEach { it.disconnect() }
        handlers.clear()
        manager<UiManager>().cancel(this)
    }
}
