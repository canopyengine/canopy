package io.canopy.engine.ui.integration

import kotlin.test.*
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.computed
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.flows.events.untrack
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.queries.ancestor
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import io.canopy.engine.ui.Canvas as SceneCanvas
import io.canopy.engine.ui.components.Button
import io.canopy.engine.ui.components.Text as TextNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class UiCompilerIntegrationTests {
    @Test
    fun `button callbacks resolve local canvas delegates and restore ownership after failures`() {
        // Arrange
        lateinit var outer: Button<*>
        lateinit var inner: Button<*>
        lateinit var nestedCanvas: SceneCanvas<*>
        val owners = mutableListOf<SceneCanvas<*>>()
        val failure = IllegalArgumentException("action failed")
        val root = SceneCanvas {
            outer = Button("Resume", onClick = {
                val canvas by ancestor<SceneCanvas<*>>()
                owners.add(canvas)
                assertSame(failure, assertFailsWith<IllegalArgumentException> { inner.activate() })
                owners.add(canvas)
            })
            nestedCanvas = Canvas {
                inner = Button("Nested", onClick = {
                    val canvas by ancestor<SceneCanvas<*>>()
                    owners.add(canvas)
                    throw failure
                })
            }
        }
        scenes.currScene = root
        ui.updateFrame()

        // Act
        outer.activate()

        // Assert
        assertEquals(listOf(root, nestedCanvas, root), owners)
        val canvas by ancestor<SceneCanvas<*>>()
        assertFailsWith<IllegalStateException> { canvas }
    }

    @Test
    fun `unified grid keeps configuration once glyphs and keyed cell controls through reactive line changes`() {
        // Arrange
        val decorated = signal(owner = null, false)
        val ids = signal(owner = null, listOf(0, 1))
        val buttons = mutableMapOf<Int, Button<*>>()
        lateinit var grid: io.canopy.engine.ui.components.Grid<*>
        var configurations = 0
        var actions = 0
        scenes.currScene = io.canopy.engine.ui.Canvas {
            grid = Grid(
                columns = 2,
                rows = 2,
                cellSize = Size(UiLength.Fixed(8.0), UiLength.Fixed(1.0)),
                lines = GridLines(outer = if (decorated()) LineStyle.Single else LineStyle.None),
                block = {
                    configurations++
                    set(0, 0, '@')
                }
            ) {
                for (id in ids()) {
                    key(id) {
                        cell(id, id) { buttons[id] = Button("cell:$id", onClick = { actions++ }) }
                    }
                }
            }
        }
        ui.updateFrame()
        val first = buttons.getValue(0)

        // Act
        decorated.update { true }
        ids.update { listOf(1, 0) }
        ui.updateFrame()
        grid.clear()
        ui.updateFrame()
        first.activate()

        // Assert
        assertEquals(1, configurations)
        assertSame(first, buttons[0])
        assertEquals(LinePattern.Single, grid.lines.outer.pattern)
        assertEquals(' ', grid[0, 0].glyph)
        assertEquals(1, actions)
        decorated.dispose()
        ids.dispose()
    }

    @Test
    fun `declarative placement sizing and grid expressions react without rebuilding content`() {
        // Arrange
        val expanded = signal(owner = null, false)
        var constructions = 0
        lateinit var text: UiNode<*>
        lateinit var grid: UiNode<*>
        scenes.currScene = io.canopy.engine.ui.Canvas {
            text = Text("anchor", place = Place.At(if (expanded()) Anchor.BottomRight else Anchor.TopLeft))
            grid = Grid(if (expanded()) 2 else 1, style = UiStyle(width = UiLength.Fixed(10.0))) {
                constructions++
                Text("first")
                Text("second")
            }
            FlowRow(size = Size(width = UiLength.Fixed(if (expanded()) 20.0 else 10.0))) {
                Text("flow")
            }
        }

        // Act
        ui.updateFrame()
        val firstHeight = grid.bounds.height
        expanded.update { true }
        ui.updateFrame()

        // Assert
        assertEquals(1, constructions)
        assertTrue(text.bounds.x > 0.0)
        assertTrue(grid.bounds.height < firstHeight)
        expanded.dispose()
    }

    @Test
    fun `canvas placement and sizing stay live without replacing retained components`() {
        // Arrange
        val centered = signal(owner = null, false)
        val count = signal(owner = null, 1)
        lateinit var label: UiNode<*>
        var clicks = 0
        scenes.currScene = io.canopy.engine.ui.Canvas {
            Row(
                style = UiStyle(height = UiLength.Fixed(4.0)),
                place = Place.At(Anchor.BottomLeft),
                size = Size(width = UiLength.Fixed(20.0)),
                arrange = Arrange.SpaceBetween,
                align = CrossAlign.Center,
                gap = 1.0
            ) {
                Text("count: ${count()}", size = Size.Content)
                Button("go", size = Size.Content, onClick = { clicks++ })
            }
            label = Text("overlay")
            label.place = if (centered()) Place.At(Anchor.Center) else Place.At(Anchor.TopLeft)
            label.size = Size(width = UiLength.Fixed(if (centered()) 10.0 else 8.0))
        }

        // Act
        ui.updateFrame()
        val original = label
        val before = label.bounds
        centered.update { true }
        count.update { 2 }
        ui.updateFrame()

        // Assert
        assertSame(original, label)
        assertNotEquals(before.x, label.bounds.x)
        assertEquals(10.0, label.bounds.width)
        assertTrue(backend.draws.any { it.text == "count: 2" })
        assertEquals(0, clicks)
        centered.dispose()
        count.dispose()
    }

    private class CounterCard(name: String, initial: Int, created: () -> Unit) : UiNode<CounterCard>(name) {
        var count by signal(initial)
        var increment: Button<*>? = null
        init {
            created()
        }
        override fun onSetup() {
            ui {
                Column {
                    Text("Count: $count")
                    increment = Button("Add") { count += 1 }
                    MapMarker()
                }
            }
        }
    }

    private class DirectLabels : Node<DirectLabels>("DirectLabels") {
        var count by signal(1)
        var labelNode: TextNode<*>? = null
        var buttonNode: Button<*>? = null

        override fun onSetup() {
            labelNode = TextNode("Fish: $count", name = "fish")
            buttonNode = Button("Add: $count", name = "add", onClick = { count += 1 })
        }
    }

    private class Fish(name: String) : Node<Fish>(name)

    private class AquariumScene : Node<AquariumScene>("Aquarium") {
        var countEvaluations = 0
        val fishCount by computed {
            untrack { countEvaluations++ }
            getChildrenOfType<Fish>().size
        }
        var countLabel: TextNode<*>? = null
        var addFishButton: Button<*>? = null

        override fun onSetup() {
            countLabel = TextNode("Fish: $fishCount", name = "count")
            addFishButton = Button("Add fish", name = "add", onClick = {
                addChild(Fish("fish-${fishCount + 1}"))
            })
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
        override fun onSetup() {
            ui { Text("overlay") }
        }
        override fun onDraw(context: DrawContext) {
            context.text("canvas")
        }
    }

    private class Group(block: Group.() -> Unit) : Node<Group>("Group", block = block)

    private class ReadyRoot(content: UiScope.() -> Unit) : UiRoot<ReadyRoot>(content = content) {
        var readyCount = 0
        override fun onReady() {
            // Engine-owned observer renewal must not depend on calling super from a user hook.
            readyCount++
        }
    }

    private class CounterNode(created: () -> Unit) : Node<CounterNode>("Counter") {
        var count by signal(0)
        init {
            created()
        }
        override fun onDraw(context: DrawContext) {
            context.text("Count: $count")
        }
    }

    private class WatchingPanel(
        private val input: Signal<Int>,
        private val shown: Signal<Boolean>,
        private val observed: MutableList<Int>,
    ) : UiNode<WatchingPanel>("WatchingPanel") {
        override fun onSetup() {
            ui {
                if (shown()) {
                    io.canopy.engine.ui.components.Text("Watching") {
                        this@WatchingPanel.input.connect { this@WatchingPanel.observed += it }
                    }
                }
            }
        }
    }

    @Test
    fun `conditional UI subscriptions belong to the retained region rather than the outer component`() {
        // Arrange
        val input = signal(owner = null, value = 0)
        val shown = signal(owner = null, value = true)
        val observed = mutableListOf<Int>()
        scenes.currScene = UiRoot { WatchingPanel(input, shown, observed) }
        ui.updateFrame()

        // Act
        input.update { 1 }
        shown.update { false }
        ui.updateFrame()
        input.update { 2 }
        shown.update { true }
        ui.updateFrame()
        input.update { 3 }

        // Assert
        assertEquals(listOf(1, 3), observed)
        input.dispose()
        shown.dispose()
    }

    private lateinit var scenes: SceneManager
    private lateinit var ui: UiManager
    private lateinit var backend: RecordingBackend

    @Test
    fun `direct UI constructor labels react without replacing nodes and mutable text can override them`() {
        val scene = DirectLabels()
        scenes.currScene = scene
        val label = scene.labelNode!!
        val button = scene.buttonNode!!
        assertEquals("Fish: 1", label.label)
        assertEquals("Add: 1", button.label)
        ui.updateFrame()
        assertEquals(listOf("Fish: 1", "Add: 1"), backend.draws.map { it.text })

        scene.count = 2
        ui.updateFrame()
        assertSame(label, scene.labelNode)
        assertSame(button, scene.buttonNode)
        assertEquals("Fish: 2", label.label)
        assertEquals("Add: 2", button.label)
        assertEquals(listOf("Fish: 2", "Add: 2"), backend.draws.map { it.text })

        button.activate()
        ui.updateFrame()
        assertEquals(3, scene.count)
        assertEquals(listOf("Fish: 3", "Add: 3"), backend.draws.map { it.text })

        label.text = "Manual fish"
        button.text = "Manual add"
        scene.count = 4
        ui.updateFrame()
        assertEquals("Fish: 4", label.label)
        assertEquals("Add: 4", button.label)
        assertEquals(listOf("Manual fish", "Manual add"), backend.draws.map { it.text })
    }

    @Test
    fun `aquarium computed child count updates direct text after button adds fish without replacing the label`() {
        val scene = AquariumScene()
        scenes.currScene = scene
        val label = scene.countLabel!!
        val button = scene.addFishButton!!
        ui.updateFrame()
        assertEquals(0, scene.fishCount)
        assertEquals(listOf("Fish: 0", "Add fish"), backend.draws.map { it.text })

        button.activate()
        ui.updateFrame()
        assertEquals(1, scene.fishCount)
        assertSame(label, scene.countLabel)
        assertEquals("Fish: 1", label.text)
        assertEquals(listOf("Fish: 1", "Add fish"), backend.draws.map { it.text })
        val firstFish = scene.getChildrenOfType<Fish>().single()

        button.activate()
        ui.updateFrame()
        assertEquals(2, scene.fishCount)
        assertSame(label, scene.countLabel)
        assertEquals("Fish: 2", backend.draws.first().text)
        val secondFish = scene.getChildrenOfType<Fish>().last()

        scene.removeChild(firstFish)
        ui.updateFrame()
        assertEquals(1, scene.fishCount)
        assertSame(label, scene.countLabel)
        assertEquals("Fish: 1", backend.draws.first().text)

        scene.addChild(firstFish)
        val shelter = Group {}
        scene.addChild(shelter)
        scene.reparent(firstFish, shelter)
        ui.updateFrame()
        assertEquals(1, scene.fishCount)
        assertEquals("Fish: 1", backend.draws.first().text)

        shelter.reparent(firstFish, scene)
        ui.updateFrame()
        assertEquals(2, scene.fishCount)
        assertEquals("Fish: 2", backend.draws.first().text)

        secondFish.queueFree()
        scenes.onUpdate(0f)
        ui.updateFrame()
        assertTrue(secondFish.isFreed)
        assertEquals(1, scene.fishCount)
        assertSame(label, scene.countLabel)
        assertEquals("Fish: 1", backend.draws.first().text)
    }

    @Test
    fun `aquarium direct input resumes after scene reentry without duplicate hierarchy subscriptions`() {
        val scene = AquariumScene()
        scenes.currScene = scene
        val label = scene.countLabel!!
        val button = scene.addFishButton!!
        ui.updateFrame()

        scene.nodeExitTree()
        scene.addChild(Fish("offline"))
        scene.buildTree()
        ui.updateFrame()
        assertSame(label, scene.countLabel)
        assertSame(button, scene.addFishButton)
        assertEquals(1, scene.fishCount)
        assertEquals("Fish: 1", backend.draws.first().text)

        scene.nodeExitTree()
        scene.buildTree()
        ui.updateFrame()
        val evaluationsBeforeAdd = scene.countEvaluations
        button.activate()
        ui.updateFrame()
        assertSame(label, scene.countLabel)
        assertSame(button, scene.addFishButton)
        assertEquals(2, scene.fishCount)
        assertEquals(evaluationsBeforeAdd + 1, scene.countEvaluations)
        assertEquals("Fish: 2", backend.draws.first().text)
    }

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
        override fun drawGridLines(
            lines: GridLines,
            geometry: GridGeometry,
            bounds: UiRect,
            clip: UiRect,
            appearance: UiAppearance,
        ) = Unit
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
        lateinit var label: io.canopy.engine.ui.components.Text<*>
        lateinit var column: io.canopy.engine.ui.components.Column<*>
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
        val root = ReadyRoot {
            Group { CounterCard("wrapped", 0) { creations++ }.also { card = it } }
        }
        scenes.currScene = root
        ui.updateFrame()

        // Act
        root.nodeExitTree()
        card.count = 7
        root.buildTree()
        ui.updateFrame()

        // Assert
        assertEquals(2, root.readyCount)
        assertEquals(1, creations)
        assertEquals("Count: 7", backend.draws.first().text)
        card.count = 8
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
    fun `direct changes beneath ordinary grouping nodes invalidate an otherwise equal frame`() {
        // Arrange
        lateinit var group: Group
        lateinit var label: io.canopy.engine.ui.components.Text<*>
        val root = UiRoot {
            group = Group { label = io.canopy.engine.ui.components.Text("first", name = "label") }
        }
        scenes.currScene = root
        ui.updateFrame()

        // Act / Assert
        group.hide()
        ui.updateFrame()
        assertTrue(backend.draws.isEmpty())
        group.show()
        label.text = "second"
        ui.updateFrame()
        assertEquals("second", backend.draws.single().text)
        label.hide()
        ui.updateFrame()
        assertTrue(backend.draws.isEmpty())
        label.show()
        ui.onResize(3, 24)
        ui.updateFrame()
        assertEquals(3.0, backend.draws.single().bounds.width)
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
        assertEquals(9, cards.getValue("a").count)
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
        counter.count = 4
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
