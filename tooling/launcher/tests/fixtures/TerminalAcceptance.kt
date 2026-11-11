import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.PauseCommand
import io.canopy.engine.commands.ResumeCommand
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.graphics.GridSymbol
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
import io.canopy.engine.ui.components.Divider
import io.canopy.engine.ui.components.GridNode
import io.canopy.platforms.terminal.app.terminalApp

/** External-consumer fixture for real terminal input, geometry and lifecycle acceptance. */
fun main() {
    terminalApp {
        onEnter {
            val world = AcceptanceWorld()
            val root = UiRoot {
                Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                    Text("Canopy terminal acceptance").style = UiStyle(
                        appearance = UiAppearance(foreground = UiColor.Rgb(120, 220, 160), bold = true)
                    )
                    val map = AcceptanceMap()
                    Text("Ticks: ${map.displayedTicks}")
                    Divider()
                    Text("Population: ${world.population}")
                    ScrollProbe()
                    Button("Add rabbit") { world.addRabbit() }
                    Button("Toggle details") { world.details = !world.details }
                    if (world.details) Text("Details visible")
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
                            world.addRabbit()
                            reply("Population: ${world.population}")
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
    private var steps by signal(0)

    init {
        mouseFilter = UiMouseFilter.Stop
    }

    override fun nodeInit() {
        ui { Text("Scroll: ${steps}") }
    }

    override fun onMouseInput(event: InputEvent) {
        if (event is MouseScrollEvent) steps += event.vertical
    }
}

/** Two independently colored markers, driven by normal Node updates and scoped drawing. */
private class AcceptanceMap : UiNode<AcceptanceMap>(
    "Map",
    UiStyle(width = UiLength.Fill, height = UiLength.Fixed(4.0), border = UiBorder.Rounded)
) {
    var ticks by signal(0)
    val displayedTicks by computed { ticks }
    private var elapsed = 0f
    private var map: GridNode<*>? = null

    override fun nodeInit() {
        super.nodeInit()
        ui {
            map = GridNode(columns = 12, rows = 2) {
                style = UiStyle(appearance = UiAppearance(bold = true))
                set(0, 0, 'r', UiColor.Rgb(120, 240, 120))
                set(0, 1, 'f', UiColor.Rgb(255, 140, 70))
            }
        }
    }

    override fun onUpdate(delta: Float) {
        elapsed += delta
        ticks += 1
        val grid = map ?: return
        val column = (elapsed * 3).toInt().mod(grid.columns)
        grid.clear()
        grid[column, 0] = GridSymbol('r', UiColor.Rgb(120, 240, 120))
        grid[column, 1] = GridSymbol('f', UiColor.Rgb(255, 140, 70))
    }
}

/** Population follows actual gameplay nodes, including subclasses, without a mirrored list or counter. */
private class AcceptanceWorld : Node<AcceptanceWorld>("World") {
    var details by signal(true)
    private var nextRabbit = 0
    val population by computed { getChildrenOfType<AcceptanceRabbit>().size }

    override fun nodeInit() {
        repeat(3) { addRabbit() }
    }

    fun addRabbit() {
        addChild(AcceptanceRabbit("Rabbit${nextRabbit++}"))
    }
}

private class AcceptanceRabbit(name: String) : Node<AcceptanceRabbit>(name)
