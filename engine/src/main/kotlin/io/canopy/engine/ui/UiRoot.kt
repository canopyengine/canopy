package io.canopy.engine.ui

import kotlin.math.max
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.ui.components.Button

/**
 * An extensible retained declarative tree initialized once. Top-level roots present independently;
 * roots beneath another UI node remain local to that presentation.
 * @param N Concrete root type exposed to its initialization block.
 * @param name Optional explicit name; otherwise the concrete class and its sibling count determine the name.
 * @param block Configures the root once after onSetup, before composing content and entering the tree.
 * @param content Retained child composition, separate from root configuration.
 */
open class UiRoot<N : UiRoot<N>>(name: String? = null, block: N.() -> Unit = {}, content: UiScope.() -> Unit = {}) :
    UiNode<N>(name, UiStyle(width = UiLength.Fill, height = UiLength.Fill), block = {
        block()
        if (this.content == null) ui(content)
    }) {
    private var viewportBounds by nodeProperty(UiRect(0.0, 0.0, 0.0, 0.0))

    /** Common presentation layer; overlays paint above content regardless of content zIndex. */
    var layer by nodeProperty(UiLayer.Content)

    /** Root vertical anchoring, including bottom overlays through End. */
    var verticalAlignment by nodeProperty(CrossAlign.Start)

    /** Root horizontal anchoring. */
    var horizontalAlignment by nodeProperty(CrossAlign.Start)

    /** Painter order for overlays; higher values paint above lower values. */
    var zIndex by nodeProperty(0)

    /** Focus is retained by element identity across keyed reorder. */
    var focusedElement by nodeProperty<UiNode<*>?>(null)
        private set
    private var lastLayout by nodeProperty<List<Any?>>(emptyList())
    init {
        var lease: AutoCloseable? = null
        observeActivity(
            enter = {
                getManager<UiManager>().mount(this)
                lease = getManager<InputFocus>().register(
                    owner = this,
                    priority = zIndex.coerceAtMost(Int.MAX_VALUE - 1),
                    capturesGameplay = {
                        validateFocus()
                        focusedElement != null
                    },
                    handler = ::input
                )
            },
            exit = {
                lease?.close()
                lease = null
                if (isValid) focus(null)
                getManager<UiManager>().unmount(this)
            }
        )
    }

    /** Moves keyboard focus to a visible entered button in this tree, or releases focus. */
    fun focus(element: UiNode<*>?) {
        require(element == null || element is Button<*>)
        val target = element?.takeIf { it.isInsideTree && it.isVisibleInTree && it.actionEnabled }
        require(target == null || target.explicitRoot === this)
        if (target != null) getManager<UiManager>().releaseOtherFocus(this)
        focusedElement = target
    }
    internal fun validateFocus() {
        val focused = focusedElement ?: return
        if (explicitRoot !== this ||
            !focused.isValid ||
            !focused.actionEnabled ||
            !focused.isInsideTree ||
            !focused.isVisibleInTree ||
            focused.explicitRoot !== this
        ) {
            focus(null)
        }
    }
    internal fun input(event: InputEvent): Boolean {
        validateFocus()
        if (explicitRoot !== this ||
            !isVisibleInTree ||
            event.state != InputState.JustPressed ||
            getManager<InputFocus>().capturesAbove(Int.MAX_VALUE - 1)
        ) {
            return false
        }
        if (event !is KeyInputEvent) return false
        val buttons = content?.elements()?.flatMap { UiTreeInput.descendants(it) }
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
                (it as Button<*>).activate()
                true
            } ?: false
            else -> false
        }
    }
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
    }
}
