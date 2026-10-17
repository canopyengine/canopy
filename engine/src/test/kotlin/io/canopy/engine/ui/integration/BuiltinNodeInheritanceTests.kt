package io.canopy.engine.ui.integration

import kotlin.test.*
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.commands.CommandPromptPresentation
import io.canopy.engine.commands.CommandPromptSnapshot
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
    private class World : EmptyNode("world")
    private class Fish : EmptyNode2D("fish") {
        var energy = 10
    }
    private class Console : CommandPrompt("console") {
        init {
            command("feed") { execute { reply("fed") } }
        }
    }
    private class Aquarium(block: UiScope.() -> Unit) : UiRoot("aquarium", block)
    private class Stack(block: UiScope.() -> Unit) : Column(name = "stack", block = block)
    private class Controls(block: UiScope.() -> Unit) : Row(name = "controls", block = block)
    private class Panel(block: UiScope.() -> Unit) :
        Box(
            style = UiStyle(width = UiLength.Content, height = UiLength.Content),
            name = "panel",
            block = block
        )
    private class Status(food: Signal<Int>) : Text("", name = "status") {
        private val food = food
        override fun onEnterTree() {
            super.onEnterTree()
            effect { text = "Food: ${food()}" }
        }
    }
    private class Feed(onClick: () -> Unit) : Button("Feed", name = "feed", onClick = onClick) {
        var updates = 0
        override fun onUpdate(delta: Float) {
            super.onUpdate(delta)
            updates++
        }
    }
    private class Tank : GridNode(2, 1, name = "tank")
    private class Separator : Divider(name = "separator")

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
        val world = World()
        val settings = Context("settings") { provide("difficulty") { "cozy" } }
        val fish = Fish()
        world.addChild(settings)
        settings.addChild(fish)
        scenes.currScene = world

        // Act / Assert
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
        val console = Console()
        scenes.currScene = console

        // Act
        console.open()
        console.submit("feed")

        // Assert
        assertTrue(console.isOpen)
        assertTrue(console.transcript.any { it.contains("fed") })
        console.queueFree()
        scenes.onUpdate(0f)
        assertTrue(console.isFreed)
        assertFailsWith<NodeDestroyedException> { console.open() }
    }
}
