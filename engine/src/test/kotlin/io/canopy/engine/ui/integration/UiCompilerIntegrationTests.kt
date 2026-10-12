package io.canopy.engine.ui.integration

import kotlin.test.*
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import io.canopy.engine.ui.components.Button
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class UiCompilerIntegrationTests {
    private class CounterCard(name: String, initial: Int, created: () -> Unit) : UiNode<CounterCard>(name) {
        val count = signal(initial)
        var increment: Button? = null
        init {
            created()
        }
        override fun nodeInit() {
            ui {
                Column {
                    Text("Count: ${count()}")
                    increment = Button("Add") { count.update { it + 1 } }
                    MapMarker()
                }
            }
        }
    }

    private open class MapMarker : Node<MapMarker>("Map") {
        override fun onDraw(context: DrawContext) {
            context.text("map")
        }
    }

    private class Canvas :
        UiNode<Canvas>(
            "Canvas",
            UiStyle(width = UiLength.Fixed(4.0), height = UiLength.Fixed(3.0), padding = 1.0)
        ) {
        override fun nodeInit() {
            ui { Text("overlay") }
        }
        override fun onDraw(context: DrawContext) {
            context.text("canvas")
        }
    }

    private class Group(block: Group.() -> Unit) : Node<Group>("Group", block = block)

    private class CounterNode(created: () -> Unit) : Node<CounterNode>("Counter") {
        val count = signal(0)
        init {
            created()
        }
        override fun onDraw(context: DrawContext) {
            context.text("Count: ${count()}")
        }
    }

    private lateinit var scenes: SceneManager
    private lateinit var ui: UiManager
    private lateinit var backend: RecordingBackend

    @BeforeEach
    fun setup() {
        backend = RecordingBackend()
        ManagersRegistry.withScope {
            scenes = SceneManager()
            ui = UiManager(backend)
            register(scenes)
            register(InputFocus())
            register(ui)
        }
        ui.onResize(80, 24)
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }
    private class RecordingBackend : UiBackend {
        data class Draw(val text: String, val bounds: UiRect)
        val draws = mutableListOf<Draw>()
        override fun begin(viewport: UiSize) {
            draws.clear()
        }
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean): UiSize =
            UiSize(text.length.toDouble().coerceAtMost(maxWidth), 1.0)
        override fun drawText(
            text: String,
            bounds: UiRect,
            clip: UiRect,
            focused: Boolean,
            wrap: Boolean,
            appearance: UiAppearance,
        ) {
            draws += Draw(text, bounds)
        }
    }

    @Test
    fun `compiler captures reactive appearance and subtree theme assignments`() {
        // Arrange
        val color = signal(owner = null, UiColor.Rgb(255, 0, 0))
        val theme = signal(owner = null, UiTheme())
        lateinit var label: io.canopy.engine.ui.components.Text
        lateinit var column: io.canopy.engine.ui.components.Column
        scenes.currScene = UiRoot {
            column = Column {
                label = Text("styled").also { it.style = UiStyle(appearance = UiAppearance(foreground = color())) }
            }.also { it.theme = theme() }
        }
        ui.updateFrame()

        // Act
        color.update { UiColor.Rgb(0, 255, 0) }
        val next = UiTheme(buttonBorder = UiBorder.Double)
        theme.update { next }
        ui.updateFrame()

        // Assert
        assertEquals(UiColor.Rgb(0, 255, 0), label.style.appearance.foreground)
        assertEquals(next, column.theme)
        color.dispose()
        theme.dispose()
    }

    @Test
    fun `UI bindings inside ordinary grouping nodes resume after reusable tree removal`() {
        // Arrange
        lateinit var card: CounterCard
        var creations = 0
        val root = UiRoot {
            Group { CounterCard("wrapped", 0) { creations++ }.also { card = it } }
        }
        scenes.currScene = root
        ui.updateFrame()

        // Act
        root.nodeExitTree()
        card.count.update { 7 }
        root.buildTree()
        ui.updateFrame()

        // Assert
        assertEquals(1, creations)
        assertEquals("Count: 7", backend.draws.first().text)
        card.count.update { 8 }
        ui.updateFrame()
        assertEquals("Count: 8", backend.draws.first().text)
        root.queueFree()
        scenes.onUpdate(0f)
        assertTrue(root.isFreed)
    }

    @Test
    fun `custom UiNode drawing precedes composed children and uses its own padded bounds`() {
        // Arrange
        scenes.currScene = UiRoot { Canvas() }

        // Act
        ui.updateFrame()

        // Assert
        assertEquals(listOf("canvas", "overlay"), backend.draws.map { it.text })
        assertEquals(UiRect(1.0, 1.0, 2.0, 1.0), backend.draws.first().bounds)
    }

    @Test
    fun `ordinary grouping nodes preserve UI descendants in layout and painting`() {
        // Arrange
        scenes.currScene = UiRoot {
            Column {
                Group { io.canopy.engine.ui.components.Text("wrapped", name = "label") }
                Text("next")
            }
        }

        // Act
        ui.updateFrame()

        // Assert
        assertEquals(listOf("wrapped", "next"), backend.draws.map { it.text })
        assertEquals(listOf(0.0, 1.0), backend.draws.map { it.bounds.y })
    }

    @Test
    fun `anonymous ordinary nodes preserve object literal lowering in reactive UI declarations`() {
        // Arrange
        val visible = signal(owner = null, true)
        scenes.currScene = UiRoot {
            if (visible()) {
                object : MapMarker() {
                    override fun onDraw(context: DrawContext) {
                        context.text("anonymous")
                    }
                }
            }
        }

        // Act / Assert
        ui.updateFrame()
        assertEquals("anonymous", backend.draws.single().text)
        visible.update { false }
        ui.updateFrame()
        assertTrue(backend.draws.isEmpty())
        visible.dispose()
    }

    @Test
    fun `custom UiNodes compose typed controls and ordinary nodes with retained constructor state`() {
        // Arrange
        val ids = signal(owner = null, listOf("a", "b"))
        val initial = signal(owner = null, 2)
        val cards = mutableMapOf<String, CounterCard>()
        var creations = 0
        scenes.currScene = UiRoot {
            Column {
                for (id in ids()) {
                    key(id) {
                        val card = CounterCard(id, initial()) { creations++ }
                        cards[id] = card
                    }
                }
            }
        }
        ui.updateFrame()
        val first = cards.getValue("a")
        first.increment!!.activate()
        ui.updateFrame()

        // Act / Assert
        initial.update { 9 }
        ids.update { listOf("b", "a") }
        ui.updateFrame()
        assertEquals(2, creations)
        assertSame(first, cards["a"])
        assertEquals(listOf("Count: 2", "Add", "map", "Count: 3", "Add", "map"), backend.draws.map { it.text })
        ids.update { listOf("b") }
        ui.updateFrame()
        scenes.onUpdate(0f)
        assertTrue(first.isFreed)
        ids.update { listOf("a", "b") }
        ui.updateFrame()
        assertEquals(3, creations)
        assertNotSame(first, cards["a"])
        assertEquals(9, cards.getValue("a").count())
        ids.dispose()
        initial.dispose()
    }

    @Test
    fun `constructor-only condition is reactive and omission clears a custom component`() {
        // Arrange
        val visible = signal(owner = null, true)
        var creations = 0
        scenes.currScene = UiRoot {
            if (visible()) {
                CounterCard("counter", 0) { creations++ }
            }
        }

        // Act / Assert
        ui.updateFrame()
        assertEquals(1, creations)
        assertEquals(listOf("Count: 0", "Add", "map"), backend.draws.map { it.text })
        visible.update { false }
        ui.updateFrame()
        assertTrue(backend.draws.isEmpty())
        visible.update { true }
        ui.updateFrame()
        assertEquals(2, creations)
        visible.dispose()
    }

    @Test
    fun `compiler conditional drawing node owns live state and initializes once until omission`() {
        // Arrange
        val show = signal(owner = null, true)
        var creations = 0
        lateinit var counter: CounterNode
        scenes.currScene = UiRoot {
            Box {
                if (show()) {
                    node("counter") { CounterNode { creations++ }.also { counter = it } }
                }
            }
        }
        ui.updateFrame()
        val first = counter

        // Act / Assert
        counter.count.update { 4 }
        ui.updateFrame()
        assertEquals("Count: 4", backend.draws.single().text)
        assertEquals(1, creations)
        show.update { false }
        ui.updateFrame()
        scenes.onUpdate(0f)
        assertTrue(first.isFreed)
        assertTrue(backend.draws.isEmpty())
        show.update { true }
        ui.updateFrame()
        assertEquals(2, creations)
        assertNotSame(first, counter)
        assertEquals("Count: 0", backend.draws.single().text)
        show.dispose()
    }

    @Test
    fun `compiler direct DSL updates properties branches keyed items and reusable components selectively`() {
        // Arrange
        data class Item(val id: String, val label: String)
        val items = signal(owner = null, listOf(Item("a", "first"), Item("b", "second")))
        val visible = signal(owner = null, true)
        val width = signal(owner = null, 0.5)
        var rootRuns = 0
        var counterCreations = 0
        var componentCreations = 0
        val counters = mutableMapOf<String, Signal<Int>>()
        val root = UiRoot {
            rootRuns++
            Column {
                if (visible()) {
                    val card = LabelCard { componentCreations++ }
                    card.render(this, "visible:${width()}")
                }
                Row(UiStyle(width = UiLength.Fraction(width()))) {
                    for (item in items()) {
                        key(item.id) {
                            val counter = signal(0).also {
                                counterCreations++
                                counters[item.id] = it
                            }
                            Text("${item.label}:${counter()}")
                        }
                    }
                }
                sharedLabel("one")
                sharedLabel("two")
            }
        }
        scenes.currScene = root
        ui.updateFrame()
        counters.getValue("a").update { 4 }
        ui.updateFrame()
        // Act
        visible.update { false }
        width.update { 1.0 }
        items.update { listOf(Item("b", "changed"), Item("a", "retained")) }
        ui.updateFrame()
        // Assert
        assertEquals(1, rootRuns)
        assertEquals(2, counterCreations)
        assertEquals(1, componentCreations)
        assertEquals(listOf("changed:0", "retained:4", "one", "two"), backend.draws.map { it.text })
        visible.update { true }
        ui.updateFrame()
        assertEquals(2, componentCreations)
        width.update { 0.75 }
        ui.updateFrame()
        assertEquals(2, componentCreations)
        assertEquals("visible:0.75", backend.draws.first().text)
    }
    private class LabelCard(onCreate: () -> Unit) {
        init {
            onCreate()
        }
        fun render(scope: UiScope, label: String) {
            with(scope) { Text(label) }
        }
    }

    private fun UiScope.sharedLabel(value: String) {
        Text(value)
    }
}
