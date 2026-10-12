package io.canopy.platforms.terminal.app

import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.PauseCommand
import io.canopy.engine.commands.ResumeCommand
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.ui.UiAppearance
import io.canopy.engine.ui.UiBorder
import io.canopy.engine.ui.UiColor
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiRoot
import io.canopy.engine.ui.UiStyle
import io.canopy.engine.ui.UiTheme

/** Real terminal smoke: responsive declarative cells, reactive text, actions, keyed list and command overlay. */
fun main() {
    terminalApp {
        onEnter {
            val population = signal(owner = null, value = 3)
            val details = signal(owner = null, value = true)
            val inhabitants = signal(owner = null, value = listOf("fox", "wolf", "deer"))
            onExit {
                population.dispose()
                details.dispose()
                inhabitants.dispose()
            }
            val screen = UiRoot {
                Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                    Text("Canopy ecosystem 森林 👩‍💻 — resize the terminal; Escape opens commands").also {
                        it.style =
                            UiStyle(appearance = UiAppearance(foreground = UiColor.Rgb(120, 220, 160), bold = true))
                    }
                    ColoredMap()
                    Row(UiStyle(width = UiLength.Fill, height = UiLength.Fraction(0.5), gap = 1.0)) {
                        Column(UiStyle(width = UiLength.Fraction(0.5), height = UiLength.Fill)) {
                            Text("Population: ${population()}")
                            if (details()) Text("Simulation continues while commands capture keys")
                            Button("Add inhabitant") {
                                population.update { it + 1 }
                                inhabitants.update { it + "animal ${population()}" }
                            }
                            Button("Toggle details") { details.update { !it } }
                        }
                        Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                            for (inhabitant in inhabitants()) {
                                key(inhabitant) { Text(inhabitant) }
                            }
                        }
                    }
                    Text("Arrow keys focus buttons; Enter activates. Commands: add, pause, resume, quit")
                }
            }
            screen.theme = UiTheme(button = UiAppearance(foreground = UiColor.Rgb(120, 190, 255)))
            val scene = TerminalUiSmokeScene()
            scene.addChild(screen)
            scene.addChild(
                CommandPrompt("Console") {
                    command<PauseCommand>()
                    command<ResumeCommand>()
                    command("add") {
                        execute {
                            population.update { it + 1 }
                            inhabitants.update { it + "animal ${population()}" }
                            reply("Population: ${population()}")
                        }
                    }
                    command("quit") { execute { app.handle.requestExit() } }
                }
            )
            manager<SceneManager>().currScene = scene
        }
    }.launch()
}

private class TerminalUiSmokeScene : Node<TerminalUiSmokeScene>("Ecosystem")

/** Small moving grid: the UI host supplies bounds; each agent supplies its own color. */
private class ColoredMap :
    UiNode<ColoredMap>(
        "Map",
        UiStyle(
            width = UiLength.Fill,
            height = UiLength.Fixed(7.0),
            border = UiBorder.Rounded,
            appearance = UiAppearance(background = UiColor.Rgb(16, 28, 22))
        )
    ) {
    private var elapsed = 0f
    override fun onUpdate(delta: Float) {
        elapsed += delta
    }
    override fun onDraw(context: DrawContext) {
        val columns = context.size.width.toInt()
        if (columns == 0) return
        val colors = listOf(UiColor.Rgb(120, 240, 120), UiColor.Rgb(255, 140, 70), UiColor.Rgb(120, 190, 255))
        colors.forEachIndexed { index, color ->
            if (index < context.size.height) {
                context.text(
                    if (index == 1) "f" else "r",
                    x = ((elapsed * 5).toInt() + index * 7).mod(columns).toDouble(),
                    y = index.toDouble(),
                    width = 1.0,
                    height = 1.0,
                    appearance = UiAppearance(foreground = color, bold = true)
                )
            }
        }
    }
}
