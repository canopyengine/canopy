package io.canopy.platforms.terminal.app

import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.PauseCommand
import io.canopy.engine.commands.ResumeCommand
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiRoot
import io.canopy.engine.ui.UiStyle

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
                    Text("Canopy ecosystem 森林 👩‍💻 — resize the terminal; Escape opens commands")
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
