package io.canopy.engine.ui

/**
 * Platform-agnostic screen-space presentation subtree, above gameplay by default.
 * [CanvasSize.Viewport] follows host geometry; fixed sizes use host logical units without scaling.
 * Nested canvases are local layout areas, not independently mounted overlays. Input passes through unless
 * an eligible control consumes it. Configuration runs once before retained [content] and tree entry.
 */
open class Canvas<N : Canvas<N>>(
    size: CanvasSize = CanvasSize.Viewport,
    anchor: Anchor = Anchor.TopLeft,
    order: Int = 0,
    name: String = "Canvas",
    block: N.() -> Unit = {},
    content: UiScope.() -> Unit = {},
) : UiRoot<N>(name, block = {
    layer = UiLayer.Overlay
    zIndex = order
    style = style.copy(
        width = if (size is CanvasSize.Fixed) UiLength.Fixed(size.width) else UiLength.Fill,
        height = if (size is CanvasSize.Fixed) UiLength.Fixed(size.height) else UiLength.Fill,
        place = Place.At(anchor)
    )
    block()
}, content = content)
