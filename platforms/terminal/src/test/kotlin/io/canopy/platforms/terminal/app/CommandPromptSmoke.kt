package io.canopy.platforms.terminal.app

import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.PauseCommand
import io.canopy.engine.commands.ResumeCommand
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager

/** Manual smoke entrypoint: Escape (or exact `:console` in line mode), help, echo, pause, resume and quit. */
fun main() {
    terminalApp {
        onEnter {
            manager<SceneManager>().currScene = CommandPrompt("Console") {
                command<PauseCommand>()
                command<ResumeCommand>()
                command("echo", "Repeat required text; quotes preserve spaces") {
                    val text by stringArgument("text")
                    execute { reply(text) }
                }
                command("quit", "Request normal application shutdown") {
                    execute { app.handle.requestExit() }
                }
            }
        }
        onUpdate { renderFrame(listOf("CommandPrompt smoke: Escape opens console; Ctrl+C exits")) }
    }.launch()
}
