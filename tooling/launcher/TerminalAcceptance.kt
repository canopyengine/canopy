import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.PauseCommand
import io.canopy.engine.commands.ResumeCommand
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.MouseScrollEvent
import io.canopy.engine.ui.UiAppearance
import io.canopy.engine.ui.UiBorder
import io.canopy.engine.ui.UiColor
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiNode
import io.canopy.engine.ui.UiMouseFilter
import io.canopy.engine.ui.UiRoot
import io.canopy.engine.ui.UiStyle
import io.canopy.engine.ui.UiTheme
import io.canopy.platforms.terminal.app.terminalApp

/** External-consumer fixture for real terminal input, geometry and lifecycle acceptance. */
fun main() {
    terminalApp {
        onEnter {
            val world = EmptyNode("World")
            val population = signal(owner = world, value = 3)
            val details = signal(owner = world, value = true)
            val root = UiRoot {
                Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                    Text("Canopy terminal acceptance").style = UiStyle(
                        appearance = UiAppearance(foreground = UiColor.Rgb(120, 220, 160), bold = true)
                    )
                    val map = AcceptanceMap()
                    Text("Ticks: ${map.ticks()}")
                    Text("Population: ${population()}")
                    ScrollProbe()
                    Button("Add rabbit") { population.update { it + 1 } }
                    Button("Toggle details") { details.update { !it } }
                    if (details()) Text("Details visible")
                    Text("Arrows focus; Enter activates; Escape opens commands")
                }
            }
            root.theme = UiTheme(button = UiAppearance(foreground = UiColor.Rgb(120, 190, 255)))
            world.addChild(root)
            world.addChild(
                CommandPrompt("Console") {
                    command<PauseCommand>()
                    command<ResumeCommand>()
                    command("add") {
                        execute {
                            population.update { it + 1 }
                            reply("Population: ${population()}")
                        }
                    }
                    command("quit") { execute { app.handle.requestExit() } }
                }
            )
            world.asSceneRoot()
        }
    }.launch()
}

/** A custom component opts in; its Text child keeps the default Ignore filter. */
private class ScrollProbe : UiNode<ScrollProbe>("Scroll") {
    private val steps = signal(0)

    init {
        mouseFilter = UiMouseFilter.Stop
    }

    override fun nodeInit() {
        ui { Text("Scroll: ${steps()}") }
    }

    override fun onMouseInput(event: InputEvent) {
        if (event is MouseScrollEvent) steps.update { it + event.vertical }
    }
}

/** Two independently colored markers, driven by normal Node updates and scoped drawing. */
private class AcceptanceMap : UiNode<AcceptanceMap>(
    "Map",
    UiStyle(width = UiLength.Fill, height = UiLength.Fixed(4.0), border = UiBorder.Rounded)
) {
    val ticks = signal(0)
    private var elapsed = 0f

    override fun onUpdate(delta: Float) {
        elapsed += delta
        ticks.update { it + 1 }
    }

    override fun onDraw(context: DrawContext) {
        val columns = context.size.width.toInt()
        if (columns < 1) return
        val column = (elapsed * 3).toInt().mod(columns).toDouble()
        context.text(
            "r", column, 0.0, 1.0, 1.0,
            appearance = UiAppearance(foreground = UiColor.Rgb(120, 240, 120), bold = true)
        )
        if (context.size.height > 1) {
            context.text(
                "f", column, 1.0, 1.0, 1.0,
                appearance = UiAppearance(foreground = UiColor.Rgb(255, 140, 70), bold = true)
            )
        }
    }
}
