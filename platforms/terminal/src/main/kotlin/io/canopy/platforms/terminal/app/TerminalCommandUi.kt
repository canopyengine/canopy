package io.canopy.platforms.terminal.app

import kotlin.math.ceil
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptSnapshot
import io.canopy.engine.core.managers.manager
import io.canopy.engine.ui.UiAlignment
import io.canopy.engine.ui.UiElement
import io.canopy.engine.ui.UiLayer
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiManager
import io.canopy.engine.ui.UiRoot
import io.canopy.engine.ui.UiStyle

/** Prompt-owned retained UI uses the same layout, clipping and terminal primitives as application screens. */
internal class TerminalCommandUi(
    private val terminal: Terminal,
    private val viewport: () -> Size,
    private val surface: TerminalSurface,
    private val panelRows: () -> Int,
    private val panelFraction: () -> Double,
) {
    private var owner: CommandPrompt? = null
    private var root: UiRoot? = null
    private var transcript: UiElement? = null
    private var editor: UiElement? = null

    fun bind(prompt: CommandPrompt?) {
        if (owner === prompt) return
        root?.takeIf { it.isValid }?.let { old ->
            old.hide()
            if (owner?.isValid == true && old.parent === owner) owner!!.removeChild(old)
            old.queueFree()
        }
        owner = prompt
        root = null
        transcript = null
        editor = null
        if (prompt == null) return
        val manager = manager<UiManager>()
        if (manager.backend == null) manager.backend = TerminalUiBackend(terminal, surface::renderUi)
        val size = viewport()
        manager.onResize(size.width.coerceAtLeast(0), size.height.coerceAtLeast(0))
        val created = UiRoot {
            Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                transcript = Text("")
                editor = Text("")
            }
        }
        created.zIndex = 1000
        created.layer = UiLayer.Overlay
        created.verticalAlignment = UiAlignment.End
        created.hide()
        root = created
        prompt.addChild(created)
    }

    fun render(snapshot: CommandPromptSnapshot) {
        val ui = root ?: return
        val size = viewport()
        val manager = manager<UiManager>()
        if (manager.viewport.width != size.width.toDouble() || manager.viewport.height != size.height.toDouble()) {
            manager.onResize(size.width.coerceAtLeast(0), size.height.coerceAtLeast(0))
        }
        val width = (size.width - 1).coerceAtLeast(0)
        val height = size.height.coerceAtLeast(0)
        val panelHeight = if (height == 0) {
            0
        } else {
            ceil(height * panelFraction()).toInt()
                .coerceIn(1, minOf(panelRows(), maxOf(1, height - 1)))
        }
        ui.style = UiStyle(width = UiLength.Fill, height = UiLength.Fixed(panelHeight.toDouble()))
        val transcriptHeight = (panelHeight - 1).coerceAtLeast(0)
        val lines = snapshot.transcript.flatMap { it.split('\n') }.takeLast(transcriptHeight)
        val padded = List(transcriptHeight) { index -> pad(lines.getOrNull(index).orEmpty(), width) }
        transcript?.let {
            it.style =
                UiStyle(width = UiLength.Fill, height = UiLength.Fixed(transcriptHeight.toDouble()), wrap = false)
            it.text = padded.joinToString("\n")
        }
        editor?.let {
            it.style =
                UiStyle(width = UiLength.Fill, height = UiLength.Fixed(minOf(1, panelHeight).toDouble()), wrap = false)
            it.text = pad(surface.editorRow(snapshot, width), width)
        }
        ui.show()
        surface.renderPrompt(snapshot)
        manager<UiManager>().renderNow()
    }

    fun hide() {
        root?.takeIf { it.isValid }?.hide()
        surface.hidePrompt(redraw = false)
        manager<UiManager>().renderNow()
    }

    private fun pad(text: String, width: Int): String {
        val cells = Text(safeTerminalText(text), whitespace = Whitespace.PRE).measure(terminal, Int.MAX_VALUE).max
        return text + " ".repeat((width - cells).coerceAtLeast(0))
    }
}
