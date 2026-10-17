package io.canopy.engine.ui.integration

import kotlin.test.*
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.commands.CommandPromptPresentation
import io.canopy.engine.commands.CommandPromptSnapshot
import io.canopy.engine.core.exceptions.InvalidNodeOperationException
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.flows.fromContext
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.*
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.core.nodes.types.empty.EmptyNode2D
import io.canopy.engine.graphics.GridContent
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import io.canopy.engine.ui.components.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class BuiltinNodeInheritanceTests {
    private class TestApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
    }
    private class World(block: World.() -> Unit = {}) : EmptyNode<World>("world", block)
    private class Fish(block: Fish.() -> Unit = {}) : EmptyNode2D<Fish>("fish", block) {
        var energy = 10
    }
    private class Console(block: Console.() -> Unit = {}) : CommandPrompt<Console>("console", block) {
        init {
            command("feed") { execute { reply("fed") } }
        }
    }
    private class Aquarium(block: Aquarium.() -> Unit = {}, content: UiScope.() -> Unit) :
        UiRoot<Aquarium>("aquarium", block = block, content = content)
    private class Stack(block: Stack.() -> Unit = {}, content: UiScope.() -> Unit) :
        Column<Stack>(name = "stack", block = block, content = content)
    private class Controls(block: Controls.() -> Unit = {}, content: UiScope.() -> Unit) :
        Row<Controls>(name = "controls", block = block, content = content)
    private class Panel(block: Panel.() -> Unit = {}, content: UiScope.() -> Unit) :
        Box<Panel>(
            style = UiStyle(width = UiLength.Content, height = UiLength.Content),
            name = "panel",
            block = block,
            content = content
        )
    private class Status(food: Signal<Int>, block: Status.() -> Unit = {}) :
        Text<Status>("", name = "status", block = block) {
        private val food = food
        override fun onEnterTree() {
            super.onEnterTree()
            effect { text = "Food: ${food()}" }
        }
    }
    private class Feed(block: Feed.() -> Unit = {}, onClick: () -> Unit) :
        Button<Feed>("Feed", name = "feed", block = block, onClick = onClick) {
        var updates = 0
        override fun onUpdate(delta: Float) {
            super.onUpdate(delta)
            updates++
        }
    }
    private class Tank(block: Tank.() -> Unit = {}) : GridNode<Tank>(2, 1, name = "tank", block = block)
    private class Separator(block: Separator.() -> Unit = {}) : Divider<Separator>(name = "separator", block = block)

    private open class Habitat<N : Habitat<N>>(block: N.() -> Unit = {}) : GridNode<N>(10, 10, block = block) {
        var capacity = 6
        override fun nodeInit() {
            super.nodeInit()
            gridColor = UiColor.Rgb(0, 100, 200)
        }
    }
    private class Reef(block: Reef.() -> Unit = {}) : Habitat<Reef>(block) {
        var corals = 1
        var enteredWith = ""
        override fun onEnterTree() {
            super.onEnterTree()
            enteredWith = "$capacity/$corals"
        }
    }

    private class Backend : UiBackend {
        val labels = mutableListOf<String>()
        var grid = ""
        var dividers = 0
        override fun begin(viewport: UiSize) {
            labels.clear()
            dividers = 0
        }
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean) = UiSize(text.length.toDouble(), 1.0)
        override fun drawText(
            text: String,
            bounds: UiRect,
            clip: UiRect,
            focused: Boolean,
            wrap: Boolean,
            appearance: UiAppearance,
        ) {
            labels += text
        }
        override fun drawGrid(grid: GridContent, bounds: UiRect, clip: UiRect, appearance: UiAppearance) {
            this.grid = (0 until grid.columns).map { grid[it, 0].glyph }.joinToString("")
        }
        override fun drawDivider(bounds: UiRect, clip: UiRect, appearance: UiAppearance) {
            dividers++
        }
    }

    private lateinit var scenes: SceneManager
    private lateinit var ui: UiManager
    private lateinit var backend: Backend

    @BeforeEach
    fun setup() {
        scenes = SceneManager()
        backend = Backend()
        ui = UiManager(backend)
        ManagersRegistry.withScope {
            register(scenes)
            register(InputFocus())
            register(ui)
            register(
                CommandPromptHost(
                    TestApp(),
                    object : CommandPromptPresentation {
                        override fun render(snapshot: CommandPromptSnapshot) = Unit
                        override fun hide() = Unit
                    }
                )
            )
        }
        ui.onResize(80, 24)
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `every UI component forwards its subclass configuration without replacing content or actions`() {
        // Arrange
        val food = signal(owner = null, 0)
        val configured = mutableListOf<String>()
        lateinit var feed: Feed
        lateinit var tank: Tank
        val root = Aquarium(block = {
            configured += "root"
            zIndex = 2
        }) {
            Stack(block = { configured += "column" }) {
                Panel(block = { configured += "box" }) {
                    Status(food) { configured += "text" }
                }
                Controls(block = { configured += "row" }) {
                    feed = Feed(block = {
                        configured += "button"
                        updates = 4
                    }) { food.update { it + 1 } }
                }
                Separator { configured += "divider" }
                tank = Tank {
                    configured += "grid"
                    columnGap = 0
                }
            }
        }
        assertTrue(configured.isEmpty())

        // Act
        scenes.currScene = root
        feed.activate()
        ui.updateFrame()

        // Assert
        assertEquals(listOf("root", "column", "box", "text", "row", "button", "divider", "grid"), configured)
        assertEquals(2, root.zIndex)
        assertEquals(4, feed.updates)
        assertEquals(0, tank.columnGap)
        assertEquals(listOf("Food: 1", "Feed"), backend.labels)
        root.nodeExitTree()
        root.buildTree()
        assertEquals(8, configured.size)
        food.dispose()
    }

    @Test
    fun `multi level receiver block runs after subclass initialization and defaults and retains identity`() {
        // Arrange
        val shown = signal(owner = null, true)
        val desired = signal(owner = null, 3)
        var configured = 0
        lateinit var reef: Reef
        val green = UiColor.Rgb(40, 180, 80)
        val root = UiRoot {
            if (shown()) {
                reef = Reef {
                    configured++
                    capacity = desired()
                    corals = 4
                    gridColor = green
                }
            }
        }
        scenes.currScene = root
        val first = reef
        ui.updateFrame()

        // Act / Assert
        assertEquals("3/4", reef.enteredWith)
        assertEquals(green, reef.gridColor)
        desired.update { 8 }
        ui.updateFrame()
        assertEquals(1, configured)
        assertSame(first, reef)
        assertEquals(3, reef.capacity)
        root.nodeExitTree()
        root.buildTree()
        assertEquals(1, configured)
        shown.update { false }
        ui.updateFrame()
        scenes.onUpdate(0f)
        assertTrue(first.isFreed)
        shown.update { true }
        ui.updateFrame()
        assertEquals(2, configured)
        assertNotSame(first, reef)
        assertEquals("8/4", reef.enteredWith)
        shown.dispose()
        desired.dispose()
    }

    @Test
    fun `stored receiver blocks initialize once rather than installing outer UI property bindings`() {
        // Arrange
        val red = UiColor.Rgb(220, 40, 40)
        val color = signal(owner = null, red)
        lateinit var reef: Reef
        val root = UiRoot {
            val setup: Reef.() -> Unit = {
                style = UiStyle(appearance = UiAppearance(foreground = color()))
            }
            reef = Reef(block = setup)
        }
        scenes.currScene = root
        ui.updateFrame()

        // Act
        color.update { UiColor.Rgb(40, 220, 40) }
        ui.updateFrame()

        // Assert
        assertEquals(red, reef.style.appearance.foreground)
        color.dispose()
    }

    @Test
    fun `root configuration precedes child composition`() {
        // Arrange
        lateinit var root: Aquarium
        root = Aquarium(block = { zIndex = 7 }) {
            Text("Layer: ${root.zIndex}")
        }

        // Act
        scenes.currScene = root
        ui.updateFrame()

        // Assert
        assertEquals(listOf("Layer: 7"), backend.labels)
    }

    @Test
    fun `UI initializer cannot escape its composition guard through the surrounding scope`() {
        // Arrange
        lateinit var reef: Reef
        val root = UiRoot {
            reef = Reef {
                assertFailsWith<InvalidNodeOperationException> { EmptyNode("decoration") }
            }
        }

        // Act
        scenes.currScene = root

        // Assert
        assertTrue(reef.children.isEmpty())
        assertEquals(listOf(reef), root.children.values.toList())
    }

    @Test
    fun `inferred direct grid construction preserves its self returning helpers`() {
        // Arrange
        val grid = GridNode(10, 10) { columnGap = 0 }

        // Act
        val prefab = grid.asPrefab()
        prefab.buildTree()

        // Assert
        assertSame(grid, prefab)
        assertEquals(10, prefab.columns)
        assertEquals(0, prefab.columnGap)
    }

    @Test
    fun `built in subclasses retain UI composition actions state and lifecycle`() {
        // Arrange
        val food = signal(owner = null, 0)
        lateinit var feed: Feed
        lateinit var tank: Tank
        val root = Aquarium {
            Stack {
                Panel { Status(food) }
                Controls { feed = Feed { food.update { it + 1 } } }
                Separator()
                tank = Tank()
            }
        }
        scenes.currScene = root
        tank.set(0, 0, 'f')
        ui.updateFrame()

        // Act
        root.focus(feed)
        feed.activate()
        scenes.onUpdate(0f)
        ui.updateFrame()

        // Assert
        assertEquals(listOf("Food: 1", "Feed"), backend.labels)
        assertEquals("f.", backend.grid)
        assertEquals(1, backend.dividers)
        assertEquals(1, feed.updates)
        assertSame(feed, root.focusedElement)
        root.nodeExitTree()
        food.update { 2 }
        root.buildTree()
        ui.updateFrame()
        assertEquals(listOf("Food: 2", "Feed"), backend.labels)
        assertSame(feed, root.getNode<Feed>("stack/controls/feed"))
        root.queueFree()
        scenes.onUpdate(0f)
        assertTrue(feed.isFreed)
        assertTrue(tank.isFreed)
        assertFailsWith<NodeDestroyedException> { feed.updates }
        food.dispose()
    }

    @Test
    fun `typed lookup patch and behavior helpers accept subclasses of concrete nodes`() {
        // Arrange
        val world = World { addGroup("worlds") }
        val settings = Context("settings") { provide("difficulty") { "cozy" } }
        val fish = Fish {
            energy = 15
            EmptyNode("inventory")
        }
        assertEquals(10, fish.energy)
        world.addChild(settings)
        settings.addChild(fish)
        scenes.currScene = world

        // Act / Assert
        assertEquals(15, fish.energy)
        assertSame(fish, fish.getNode<EmptyNode<*>>("inventory").parent)
        assertTrue("worlds" in world.groups)
        assertSame(fish, world.getNode<Fish>("fish"))
        assertEquals("cozy", fish.fromContext<String>("difficulty"))
        assertSame(fish, world.getNodeOrNull<Fish>("fish"))
        assertSame(fish, world.get<Fish>("fish"))
        assertNull(world.getNodeOrNull<Fish>("missing"))
        world.patch<Fish>("fish") { energy = 20 }
        fish.behavior<Fish>(onUpdate = { energy++ })
        scenes.onUpdate(0f)
        assertEquals(21, fish.energy)
        fish.attachBehavior { owner ->
            object : Behavior<Fish>(owner) {
                override fun onUpdate(delta: Float) {
                    node!!.energy += 2
                }
            }
        }
        scenes.onUpdate(0f)
        assertEquals(23, fish.energy)
        fish += createBehavior<Fish>(onUpdate = { energy += 3 })
        scenes.onUpdate(0f)
        assertEquals(26, fish.energy)
    }

    @Test
    fun `command prompt subclass keeps command registration and owned cleanup`() {
        // Arrange
        val console = Console { prompt = "aquarium> " }
        scenes.currScene = console

        // Act
        console.open()
        console.submit("feed")

        // Assert
        assertEquals("aquarium> ", console.prompt)
        assertTrue(console.isOpen)
        assertTrue(console.transcript.any { it.contains("fed") })
        console.queueFree()
        scenes.onUpdate(0f)
        assertTrue(console.isFreed)
        assertFailsWith<NodeDestroyedException> { console.open() }
    }
}
