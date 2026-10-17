package io.canopy.engine.ui

import kotlin.math.max
import io.canopy.engine.core.flows.events.DependencySubscriptions
import io.canopy.engine.core.flows.events.TrackingContext
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.input.events.MouseMoveEvent
import io.canopy.engine.input.events.MouseScrollEvent
import io.canopy.engine.math.Vector2
import io.canopy.engine.ui.components.Button

/** Marks a declarative builder receiver for the Canopy compiler's selective expression lowering. */
@Target(AnnotationTarget.CLASS)
annotation class DeclarativeUi

internal class UiRegion(root: UiRoot, name: String) : Node<UiRegion>(name, skipOnSearch = true) {
    internal val root by nodeProperty(root)
    internal var content by nodeProperty<UiScope?>(null)
    override fun nodeInit() {
        markChildrenManaged()
    }
}

/** An extensible retained declarative tree initialized once; viewport changes recompute its universal layout. */
open class UiRoot(name: String = "UiRoot", block: UiScope.() -> Unit) :
    UiNode<UiRoot>(name, UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
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

    /** Focus is retained by element identity across keyed reorder. */
    var focusedElement by nodeProperty<UiNode<*>?>(null)
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
    fun focus(element: UiNode<*>?) {
        require(element == null || element is Button)
        val target = element?.takeIf { it.isInsideTree && it.isVisibleInTree && it.actionEnabled }
        require(target == null || target.root === this)
        if (target != null) manager<UiManager>().releaseOtherFocus(this)
        focusedElement = target
        dirty = true
    }
    internal fun validateFocus() {
        val focused = focusedElement ?: return
        if (!focused.isValid || !focused.actionEnabled || !focused.isInsideTree || !focused.isVisibleInTree) focus(null)
    }
    private fun input(event: InputEvent): Boolean {
        validateFocus()
        if (!isVisibleInTree || event.state != InputState.JustPressed) return false
        if (event !is KeyInputEvent) return false
        val buttons = content?.elements()?.flatMap { descendants(it) }
            ?.filter { it.kind == UiKind.Button && it.actionEnabled && it.isInsideTree && it.isVisibleInTree }.orEmpty()
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
                (it as Button).activate()
                true
            } ?: false
            else -> false
        }
    }
    internal fun mouseInput(event: InputEvent): Boolean {
        if (!isVisibleInTree) return false
        val position = when (event) {
            is MouseButtonEvent -> event.screenPos
            is MouseMoveEvent -> event.screenPos
            is MouseScrollEvent -> event.screenPos
            else -> return false
        }
        // Snapshot before user callbacks, which may change or destroy the tree.
        val candidates = listOf(this) + content?.elements().orEmpty().flatMap(::descendants)
        for (node in candidates.asReversed()) {
            if (!isValid || !isInsideTree) return false
            if (!node.isValid || !node.isInsideTree || !node.isVisibleInTree) continue
            if (node.root !== this) continue
            val filter = node.mouseFilter
            if (filter == UiMouseFilter.Ignore || !hit(node, position.x.toDouble(), position.y.toDouble())) continue
            node.dispatchMouseInput(event)
            if (event.isHandled || filter == UiMouseFilter.Stop) {
                event.consume()
                return true
            }
        }
        return false
    }
    internal fun pointerTargets(position: Vector2): List<UiNode<*>> = if (!isVisibleInTree) {
        emptyList()
    } else {
        (listOf(this) + content?.elements().orEmpty().flatMap(::descendants)).asReversed().filter {
            it.isValid &&
                it.isInsideTree &&
                it.isVisibleInTree &&
                it.mouseFilter != UiMouseFilter.Ignore &&
                hit(it, position.x.toDouble(), position.y.toDouble())
        }
    }

    internal fun hasPointerTargets(): Boolean = isVisibleInTree &&
        (listOf(this) + content?.elements().orEmpty().flatMap(::descendants)).any {
            it.isValid && it.isInsideTree && it.isVisibleInTree && it.mouseFilter != UiMouseFilter.Ignore
        }

    private fun hit(element: UiNode<*>, x: Double, y: Double): Boolean {
        var clip = element.bounds.intersect(viewportBounds)
        var ancestor = if (element === this) null else element.parent
        while (ancestor != null) {
            if (ancestor is UiNode<*> && ancestor.style.clip) {
                clip =
                    clip.intersect(
                        if (ancestor.resolvedBorder ==
                            UiBorder.None
                        ) {
                            ancestor.bounds
                        } else {
                            ancestor.contentBounds
                        }
                    )
            }
            if (ancestor === this) break
            ancestor = ancestor.parent
        }
        return x >= clip.x && x < clip.x + clip.width && y >= clip.y && y < clip.y + clip.height
    }
    private fun descendants(element: UiNode<*>): List<UiNode<*>> =
        listOf(element) + element.content?.elements().orEmpty().flatMap(::descendants)

    private fun layoutSnapshot(element: UiNode<*>, snapshot: MutableList<Any?>) {
        snapshot.add(element)
        snapshot.add(element.style.layoutKey)
        snapshot.add(element.resolvedBorder)
        snapshot.add(element.contentText)
        snapshot.add(element.contentMeasureKey)
        element.content?.forEachElement { layoutSnapshot(it, snapshot) }
    }

    internal fun prepareLayout(backend: UiBackend, viewport: UiSize) {
        validateFocus()
        if (!isVisibleInTree) return
        viewportBounds = UiRect(0.0, 0.0, viewport.width, viewport.height)
        val layoutKey =
            buildList {
                add(backend)
                add(viewport)
                add(style.layoutKey)
                add(resolvedBorder)
                add(verticalAlignment)
                add(horizontalAlignment)
                content?.forEachElement { layoutSnapshot(it, this) }
            }
        if (layoutKey != lastLayout) {
            UiLayoutEngine.layout(this, backend, viewport)
            lastLayout = layoutKey
        }
    }

    /** Layouts and paints using backend metrics, independently of gameplay pause eligibility. */
    fun render(backend: UiBackend, viewport: UiSize) {
        prepareLayout(backend, viewport)
        paint(backend)
    }

    internal fun paint(backend: UiBackend) {
        if (!isVisibleInTree) return
        val rootClip = if (style.clip) viewportBounds.intersect(bounds) else viewportBounds
        val appearance = appearance()
        backend.drawBox(bounds, rootClip, appearance, resolvedBorder)
        val area = UiRect(
            bounds.x + contentInset,
            bounds.y + contentInset,
            max(0.0, bounds.width - 2 * contentInset),
            max(0.0, bounds.height - 2 * contentInset)
        )
        val contentClip = if (style.clip) rootClip.intersect(area) else rootClip
        content?.nodes()?.forEach {
            UiLayoutEngine.paintNode(
                it,
                backend,
                area,
                if (it is UiNode<*> && resolvedBorder == UiBorder.None) rootClip else contentClip,
                focusedElement,
                appearance
            )
        }
        dirty = false
    }
}

/**
 * UI work committed after app and manager updates, including paused frames, on the engine thread.
 * App binds the host backend before startup callbacks; standalone drivers supply it explicitly when rendering is needed.
 * Explicit backend replacement is supported. Native backend resources remain owned by the host.
 */
open class UiManager(var backend: UiBackend? = null) : Manager {
    private val roots = linkedSetOf<UiRoot>()
    private val pending = linkedSetOf<UiObserver>()

    // Buffers swap only after a successful end. Equal/failed frames clear scratch references.
    private var lastFrame = ArrayList<Any?>()
    private var nextFrame = ArrayList<Any?>()
    private var hasFrame = false
    private var updating = false
    private var mouseLease: AutoCloseable? = null
    private var pointer: Vector2? = null
    private var hovered = linkedSetOf<UiNode<*>>()

    /** Whether the host should report pointer movement for visible participating UI elements. */
    val needsMouseMovement: Boolean
        get() = !manager<InputFocus>().capturesAbove(Int.MAX_VALUE - 1) && activeRoots().any { it.hasPointerTargets() }

    private fun updateHover(active: List<UiRoot>) {
        if (manager<InputFocus>().capturesAbove(Int.MAX_VALUE - 1)) pointer = null
        val next = linkedSetOf<UiNode<*>>()
        pointer?.let { position ->
            for (root in active.asReversed()) {
                for (node in root.pointerTargets(position)) {
                    next += node
                    if (node.mouseFilter == UiMouseFilter.Stop) break
                }
                if (next.lastOrNull()?.mouseFilter == UiMouseFilter.Stop) break
            }
        }
        val previous = hovered
        hovered = next
        previous.filter { it !in next }.forEach { it.hover(false) }
        next.filter { it !in previous }.forEach {
            if (it.isValid && it.isInsideTree && it.isVisibleInTree) it.hover(true)
        }
    }

    private fun ensureMouseRoute() {
        if (mouseLease != null) return
        mouseLease = manager<InputFocus>().register(priority = Int.MAX_VALUE - 1) { event ->
            if (event !is MouseButtonEvent && event !is MouseMoveEvent && event !is MouseScrollEvent) {
                false
            } else {
                pointer = when (event) {
                    is MouseButtonEvent -> event.screenPos
                    is MouseMoveEvent -> event.screenPos
                    is MouseScrollEvent -> event.screenPos
                    else -> null
                }
                val active = activeRoots()
                backend?.let { renderer -> active.forEach { it.prepareLayout(renderer, renderer.viewport(viewport)) } }
                val handled = active.asReversed().any { it.isValid && it.isInsideTree && it.mouseInput(event) }
                updateHover(activeRoots())
                handled
            }
        }
    }

    private fun activeRoots(): List<UiRoot> = roots.toList().filter { it.isValid && it.isInsideTree }
        .sortedWith(compareBy<UiRoot> { it.layer.ordinal }.thenBy { it.zIndex })

    /** Defers explicit painting until all application update callbacks have completed successfully. */
    internal fun afterUpdates(block: () -> Unit) {
        check(!updating) { "Cannot nest UI frame updates" }
        updating = true
        try {
            block()
        } finally {
            updating = false
        }
        updateFrame()
    }

    /** Current host dimensions in the backend's logical units. */
    var viewport = UiSize(0.0, 0.0)
        private set
    internal fun releaseOtherFocus(root: UiRoot) {
        roots.filter { it !== root && it.isValid }.forEach { it.focus(null) }
    }
    internal fun mount(root: UiRoot) {
        ensureMouseRoute()
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

    /**
     * Processes one batch of reactive work and paints after all simulation and service updates.
     * [io.canopy.engine.app.App] invokes this boundary automatically. Custom hosts invoke it once
     * after updates; work enqueued while processing the batch remains pending for the next frame.
     */
    fun updateFrame() {
        check(!updating) { "UI work must run after application updates" }
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
        if (updating) return
        backend?.let { renderer ->
            val scene = manager<SceneManager>()
            val logicalViewport = renderer.viewport(viewport)
            var activeRoots = activeRoots()
            activeRoots.forEach {
                it.validateFocus()
                if (pointer != null) it.prepareLayout(renderer, logicalViewport)
            }
            updateHover(activeRoots)
            activeRoots = activeRoots()
            val draws = scene.hasDrawables
            try {
                nextFrame.add(renderer)
                nextFrame.add(logicalViewport)
                nextFrame.add(draws)
                activeRoots.forEach { snapshot(it, nextFrame) }
                if (!draws && hasFrame && nextFrame == lastFrame) return
                try {
                    renderer.begin(logicalViewport)
                    val area = UiRect(0.0, 0.0, logicalViewport.width, logicalViewport.height)
                    val context = DrawContext(renderer, area, area)
                    try {
                        scene.currScene?.drawSubtree(context) { it is UiRoot }
                    } finally {
                        context.close()
                    }
                    activeRoots.forEach {
                        if (it.isValid && it.isInsideTree) {
                            it.prepareLayout(renderer, logicalViewport)
                            it.paint(renderer)
                        }
                    }
                    renderer.end()
                } catch (failure: Throwable) {
                    try {
                        renderer.abort()
                    } catch (cleanup: Throwable) {
                        if (cleanup !== failure) failure.addSuppressed(cleanup)
                    }
                    throw failure
                }
                val previous = lastFrame
                lastFrame = nextFrame
                nextFrame = previous
                hasFrame = true
            } finally {
                nextFrame.clear()
            }
        }
    }
    private fun snapshot(root: UiRoot, snapshot: MutableList<Any?>) {
        snapshot.add(root)
        snapshot.add(root.isVisibleInTree)
        snapshot.add(root.style)
        snapshot.add(root.theme)
        snapshot.add(root.verticalAlignment)
        snapshot.add(root.horizontalAlignment)
        snapshot.add(root.focusedElement)
        snapshot.add(root.layer)
        snapshot.add(root.zIndex)
        snapshot.add(root.isHovered)
        root.content?.forEachElement { snapshot(it, snapshot) }
    }
    private fun snapshot(element: UiNode<*>, snapshot: MutableList<Any?>) {
        snapshot.add(element)
        snapshot.add(element.isVisibleInTree)
        snapshot.add(element.style)
        snapshot.add(element.theme)
        snapshot.add(element.contentText)
        snapshot.add(element.actionEnabled)
        snapshot.add(element.presentationKey)
        snapshot.add(element.isHovered)
        element.content?.forEachElement { snapshot(it, snapshot) }
    }
    override fun onExit() {
        mouseLease?.close()
        mouseLease = null
        pointer = null
        hovered.toList().forEach { it.hover(false) }
        hovered.clear()
        hasFrame = false
        lastFrame.clear()
        lastFrame.trimToSize()
        nextFrame.clear()
        nextFrame.trimToSize()
        pending.toList().forEach { it.dispose() }
        pending.clear()
        roots.clear()
    }
}

internal class UiObserver(private val owner: Node<*>, private var action: (() -> Unit)?) {
    private val subscriptions = DependencySubscriptions()
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
                subscriptions.reconcile(frame) { manager<UiManager>().enqueue(this) }
            }
        }
    }
    fun suspend() {
        if (disposed || suspended) return
        suspended = true
        cancelRemoval?.invoke()
        cancelRemoval = null
        subscriptions.clear()
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
        subscriptions.clear()
        manager<UiManager>().cancel(this)
    }
}
