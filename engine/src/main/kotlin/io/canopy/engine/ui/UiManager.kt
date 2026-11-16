package io.canopy.engine.ui

import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node
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

/**
 * UI work committed after app and manager updates, including paused frames, on the engine thread.
 * Rootless UI in the current scene uses viewport layout and paints before explicit roots and overlays.
 * App binds the host backend before startup callbacks; standalone drivers supply it explicitly when rendering is needed.
 * Explicit backend replacement is supported. Native backend resources remain owned by the host.
 */
open class UiManager(var backend: UiBackend? = null) : Manager {
    private val roots = linkedSetOf<UiRoot<*>>()
    private val pending = linkedSetOf<UiObserver>()
    private var implicitScene: Node<*>? = null
    private var implicitHierarchyGeneration = -1L
    private var cachedImplicitRoots: List<UiNode<*>> = emptyList()
    private var implicitFocusedElement: UiNode<*>? = null
    private var implicitFocusOwner: UiNode<*>? = null

    // Buffers swap only after a successful end. Equal/failed frames clear scratch references.
    private var lastFrame = ArrayList<Any?>()
    private var nextFrame = ArrayList<Any?>()
    private var hasFrame = false
    internal var presentationEnabled = true
        set(value) {
            if (field != value) hasFrame = false
            field = value
        }

    internal fun clearPointer() {
        pointer = null
        val previous = hovered.toList()
        hovered.clear()
        previous.forEach { if (it.isValid) it.hover(false) }
    }
    private var updating = false
    private var mouseLease: AutoCloseable? = null
    private var keyboardLease: AutoCloseable? = null
    private var pointer: Vector2? = null
    private var hovered = linkedSetOf<UiNode<*>>()

    /** Whether the host should report pointer movement for visible participating UI elements. */
    val needsMouseMovement: Boolean
        get() {
            ensureRoutes()
            return !getManager<InputFocus>().capturesAbove(Int.MAX_VALUE - 1) &&
                activePresentations().any { UiTreeInput.hasPointerTargets(it) }
        }

    private fun updateHover(active: List<UiNode<*>>) {
        if (getManager<InputFocus>().capturesAbove(Int.MAX_VALUE - 1)) pointer = null
        val next = linkedSetOf<UiNode<*>>()
        pointer?.let { position ->
            val viewportBounds = viewportRect()
            for (root in active.asReversed()) {
                for (node in UiTreeInput.pointerTargets(root, viewportBounds, position)) {
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

    internal fun ensureRoutes() {
        if (mouseLease != null) return
        keyboardLease = getManager<InputFocus>().register(
            priority = Int.MIN_VALUE,
            capturesGameplay = { implicitFocusEligible() },
            handler = ::inputImplicit
        )
        mouseLease = getManager<InputFocus>().register(priority = Int.MAX_VALUE - 1) { event ->
            if (event !is MouseButtonEvent && event !is MouseMoveEvent && event !is MouseScrollEvent) {
                false
            } else if (getManager<InputFocus>().capturesAbove(Int.MAX_VALUE - 1)) {
                clearPointer()
                false
            } else {
                val position = when (event) {
                    is MouseButtonEvent -> event.screenPos
                    is MouseMoveEvent -> event.screenPos
                    is MouseScrollEvent -> event.screenPos
                    else -> null
                }
                pointer = position
                val active = activePresentations()
                backend?.let { renderer ->
                    val logicalViewport = renderer.viewport(viewport)
                    active.forEach { prepareLayout(it, renderer, logicalViewport) }
                }
                val viewportBounds = viewportRect()
                val handled = active.asReversed().any {
                    it.isValid &&
                        it.isInsideTree &&
                        position != null &&
                        UiTreeInput.mouseInput(it, viewportBounds, event, position)
                }
                updateHover(activePresentations())
                handled
            }
        }
    }

    private fun activeRoots(): List<UiRoot<*>> {
        val entered = roots.filter { it.isValid && it.isInsideTree }
        entered.forEach { it.validateFocus() }
        return entered.filter { it.explicitRoot === it }
            .sortedWith(compareBy<UiRoot<*>> { it.layer.ordinal }.thenBy { it.zIndex })
    }

    private fun implicitRoots(): List<UiNode<*>> {
        val scenes = getManager<SceneManager>()
        val scene = scenes.currScene
        val generation = scenes.hierarchyGeneration
        if (scene === implicitScene && generation == implicitHierarchyGeneration) return cachedImplicitRoots
        val result = mutableListOf<UiNode<*>>()
        val pending = ArrayDeque<Node<*>>()
        if (scene != null) pending.addLast(scene)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (!node.isValid || !node.isInsideTree) continue
            if (node is UiNode<*>) {
                if (node !is UiRoot<*>) result += node
                continue
            }
            val children = node.childSnapshot()
            for (index in children.indices.reversed()) pending.addLast(children[index])
        }
        implicitScene = scene
        implicitHierarchyGeneration = generation
        cachedImplicitRoots = result
        return result
    }

    private fun activePresentations(): List<UiNode<*>> = implicitRoots() + activeRoots()

    private fun viewportRect(): UiRect {
        val logical = backend?.viewport(viewport) ?: viewport
        return UiRect(0.0, 0.0, logical.width, logical.height)
    }

    private fun prepareLayout(element: UiNode<*>, renderer: UiBackend, logicalViewport: UiSize) {
        if (element is UiRoot<*>) {
            element.prepareLayout(renderer, logicalViewport)
        } else if (element.isVisibleInTree) {
            UiLayoutEngine.layoutViewport(element, renderer, logicalViewport)
        }
    }

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
    internal fun releaseOtherFocus(root: UiRoot<*>) {
        implicitFocusedElement = null
        implicitFocusOwner = null
        roots.filter { it !== root && it.isValid }.forEach { it.focus(null) }
    }

    internal fun focus(element: UiNode<*>) {
        val root = element.explicitRoot
        if (root != null) {
            root.focus(element)
        } else if (element is Button<*> && element.isInsideTree && element.isVisibleInTree && element.actionEnabled) {
            val owner = implicitOwner(element)
            if (owner != null) {
                roots.filter { it.isValid }.forEach { it.focus(null) }
                implicitFocusedElement = element
                implicitFocusOwner = owner
            }
        }
    }

    private fun implicitOwner(element: UiNode<*>): UiNode<*>? = implicitRoots().firstOrNull {
        element === it || isDescendantOf(element, it)
    }

    private fun isDescendantOf(element: Node<*>, root: Node<*>): Boolean {
        var ancestor: Node<*>? = element.parent
        while (ancestor != null) {
            if (ancestor === root) return true
            ancestor = ancestor.parent
        }
        return false
    }

    private fun implicitFocusEligible(): Boolean {
        val focused = implicitFocusedElement ?: return false
        val owner = implicitFocusOwner ?: return false
        val eligible = focused.isValid &&
            focused.isInsideTree &&
            focused.isVisibleInTree &&
            focused.actionEnabled &&
            focused.explicitRoot == null &&
            owner === implicitOwner(focused)
        if (!eligible) {
            implicitFocusedElement = null
            implicitFocusOwner = null
        }
        return eligible
    }

    private fun inputImplicit(event: InputEvent): Boolean {
        implicitFocusEligible()
        if (getManager<InputFocus>().capturesAbove(Int.MIN_VALUE)) return false
        if (event.state != InputState.JustPressed || event !is KeyInputEvent) return false
        val buttons = implicitRoots().flatMap(UiTreeInput::descendants).filter {
            it is Button<*> && it.actionEnabled && it.isInsideTree && it.isVisibleInTree
        }
        return when (event.key) {
            Key.DOWN, Key.RIGHT -> if (buttons.isEmpty()) {
                false
            } else {
                focus(buttons[(buttons.indexOf(implicitFocusedElement) + 1) % buttons.size])
                true
            }
            Key.UP, Key.LEFT -> if (buttons.isEmpty()) {
                false
            } else {
                focus(buttons[(buttons.indexOf(implicitFocusedElement) - 1 + buttons.size) % buttons.size])
                true
            }
            Key.ESCAPE -> if (implicitFocusedElement != null) {
                implicitFocusedElement = null
                implicitFocusOwner = null
                true
            } else {
                false
            }
            Key.ENTER, Key.SPACE -> implicitFocusedElement?.let {
                (it as Button<*>).activate()
                true
            } ?: false
            else -> false
        }
    }
    internal fun mount(root: UiRoot<*>) {
        ensureRoutes()
        roots += root
    }
    internal fun unmount(root: UiRoot<*>) {
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
    }

    override fun onEnter() {
        ensureRoutes()
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
        if (updating || !presentationEnabled) return
        ensureRoutes()
        backend?.let { renderer ->
            val scene = getManager<SceneManager>()
            val logicalViewport = renderer.viewport(viewport)
            implicitFocusEligible()
            var active = activePresentations()
            active.forEach {
                if (it is UiRoot<*>) it.validateFocus()
                if (pointer != null) prepareLayout(it, renderer, logicalViewport)
            }
            updateHover(active)
            active = activePresentations()
            val draws = scene.hasDrawables
            try {
                nextFrame.add(renderer)
                nextFrame.add(logicalViewport)
                nextFrame.add(draws)
                nextFrame.add(implicitFocusedElement)
                active.forEach { element ->
                    if (element is UiRoot<*>) {
                        snapshotRoot(element, nextFrame)
                    } else {
                        snapshot(element, nextFrame)
                    }
                }
                if (!draws && hasFrame && nextFrame == lastFrame) return
                try {
                    renderer.begin(logicalViewport)
                    val area = UiRect(0.0, 0.0, logicalViewport.width, logicalViewport.height)
                    if (draws) {
                        val context = DrawContext(renderer, area, area)
                        try {
                            scene.currScene?.drawSubtree(context) { it is UiNode<*> }
                        } finally {
                            context.close()
                        }
                    }
                    active.forEach {
                        if (it.isValid && it.isInsideTree && UiTreeInput.isPresentationRoot(it)) {
                            prepareLayout(it, renderer, logicalViewport)
                            if (it is UiRoot<*>) {
                                it.paint(renderer)
                            } else {
                                UiLayoutEngine.paint(it, renderer, area, implicitFocusedElement)
                            }
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
    private fun snapshotRoot(root: UiRoot<*>, snapshot: MutableList<Any?>) {
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
        presentationEnabled = true
        mouseLease?.close()
        mouseLease = null
        keyboardLease?.close()
        keyboardLease = null
        implicitFocusedElement = null
        implicitFocusOwner = null
        implicitScene = null
        implicitHierarchyGeneration = -1L
        cachedImplicitRoots = emptyList()
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
