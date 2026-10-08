package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.effect
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputFocus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class UiRuntimeTests {
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

    private class RecordingBackend(private val glyphWidth: Double = 1.0, private val lineHeight: Double = 1.0) :
        UiBackend {
        data class Draw(val text: String, val bounds: UiRect, val clip: UiRect, val focused: Boolean)
        var frames = 0
        var measurements = 0
        val draws = mutableListOf<Draw>()
        override fun begin(viewport: UiSize) {
            frames++
            draws.clear()
        }
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean): UiSize {
            measurements++
            val rawWidth = text.length * glyphWidth
            if (!wrap || maxWidth <= 0) return UiSize(rawWidth, lineHeight)
            return UiSize(
                rawWidth.coerceAtMost(maxWidth),
                kotlin.math.ceil(rawWidth / maxWidth).coerceAtLeast(1.0) * lineHeight
            )
        }
        override fun drawText(text: String, bounds: UiRect, clip: UiRect, focused: Boolean, wrap: Boolean) {
            draws += Draw(text, bounds, clip, focused)
        }
    }

    @Test
    fun `targeted expressions coalesce at safe boundary and unchanged results skip frame`() {
        // Arrange
        val count = signal(owner = null, 1)
        var rootRuns = 0
        var leftRuns = 0
        var rightRuns = 0
        val root = UiRoot {
            rootRuns++
            Column {
                bindText("left") {
                    leftRuns++
                    "${count() / 10}"
                }
                bindText("right") {
                    rightRuns++
                    "static"
                }
            }
        }
        scenes.currScene = root
        ui.onUpdate(0f)
        val frames = backend.frames
        val measures = backend.measurements
        // Act
        count.update { 2 }
        count.update { 3 }
        ui.onUpdate(0f)
        // Assert
        assertEquals(1, rootRuns)
        assertEquals(2, leftRuns)
        assertEquals(1, rightRuns)
        assertEquals(frames, backend.frames)
        assertEquals(measures, backend.measurements)
        count.update { 20 }
        ui.onUpdate(0f)
        assertEquals(listOf("2", "static"), backend.draws.map { it.text })
    }

    @Test
    fun `key reorder retains identity focus local state and owned work while omission disposes`() {
        // Arrange
        val ids = signal(owner = null, listOf("a", "b"))
        val pulse = signal(owner = null, 0)
        val counters = mutableMapOf<String, Signal<Int>>()
        val buttons = mutableMapOf<String, UiElement>()
        val creations = mutableListOf<String>()
        val effects = mutableListOf<String>()
        val root = UiRoot {
            Column {
                structure("list") {
                    for (id in ids()) {
                        key(id) {
                            val counter = remember("counter") {
                                creations += id
                                signal(0).also { counters[id] = it }
                            }
                            remember("effect") {
                                effect {
                                    pulse()
                                    effects += id
                                }
                            }
                            buttons[id] = bindButtonText("button", { "$id:${counter()}" }) {
                                counter.update { it + 1 }
                            }
                        }
                    }
                }
            }
        }
        scenes.currScene = root
        ui.onUpdate(0f)
        val a = buttons.getValue("a")
        root.focus(a)
        a.activate()
        ui.onUpdate(0f)
        // Act
        ids.update { listOf("b", "a") }
        ui.onUpdate(0f)
        // Assert
        assertSame(a, buttons.getValue("a"))
        assertSame(a, root.focusedElement)
        assertEquals(listOf("a", "b"), creations)
        assertEquals(listOf("b:0", "a:1"), backend.draws.map { it.text })
        ids.update { listOf("b") }
        ui.onUpdate(0f)
        scenes.onUpdate(0f)
        assertTrue(a.isFreed)
        assertNull(root.focusedElement)
        assertFailsWith<NodeDestroyedException> { counters.getValue("a")() }
        effects.clear()
        pulse.update { 1 }
        assertEquals(listOf("b"), effects)
        ids.update { listOf("a", "b") }
        ui.onUpdate(0f)
        assertNotSame(a, buttons.getValue("a"))
        assertEquals(0, counters.getValue("a")())
    }

    @Test
    fun `reusable removal suspends pending observers and reentry preserves local signal state`() {
        // Arrange
        lateinit var count: Signal<Int>
        var constructions = 0
        val root = UiRoot {
            count = signal(0)
            constructions++
            bindText("count") { "${count()}" }
        }
        scenes.currScene = root
        ui.onUpdate(0f)
        count.update { 5 }
        // Act
        root.nodeExitTree()
        ui.onUpdate(0f)
        count.update { 7 }
        root.buildTree()
        ui.onUpdate(0f)
        // Assert
        assertEquals(1, constructions)
        assertEquals("7", backend.draws.single().text)
    }

    @Test
    fun `hidden element reserves layout space but cannot draw or retain focus`() {
        // Arrange
        lateinit var button: UiElement
        val root = UiRoot {
            Column {
                button = Button("action") {}
                Text("next")
            }
        }
        scenes.currScene = root
        ui.onUpdate(0f)
        root.focus(button)
        val nextY = backend.draws.last().bounds.y
        // Act
        button.hide()
        ui.onUpdate(0f)
        // Assert
        assertNull(root.focusedElement)
        assertEquals(listOf("next"), backend.draws.map { it.text })
        assertEquals(nextY, backend.draws.single().bounds.y)
        button.show()
        ui.onUpdate(0f)
        assertEquals(listOf("action", "next"), backend.draws.map { it.text })
    }

    @Test
    fun `resize recomputes fractions fill gap padding alignment and clipping in cell and pixel metrics`() {
        for (metrics in listOf(RecordingBackend(), RecordingBackend(8.0, 16.0))) {
            // Arrange
            lateinit var left: UiElement
            lateinit var right: UiElement
            val root = UiRoot {
                Row(
                    UiStyle(
                        width = UiLength.Fill,
                        height = UiLength.Fill,
                        padding = 2.0,
                        gap = 3.0,
                        alignment = UiAlignment.End
                    )
                ) {
                    left = Text("left").also { it.style = UiStyle(width = UiLength.Fraction(0.25)) }
                    right = Text("right").also { it.style = UiStyle(width = UiLength.Fill) }
                }
            }
            scenes.currScene = root
            ui.backend = metrics
            ui.onResize(100, 60)
            ui.onUpdate(0f)
            assertEquals(24.0, left.bounds.width)
            assertEquals(69.0, right.bounds.width)
            assertTrue(metrics.draws.all { it.clip.width <= it.bounds.width })
            // Act
            ui.onResize(50, 30)
            ui.onUpdate(0f)
            // Assert
            assertEquals(11.5, left.bounds.width)
            assertEquals(31.5, right.bounds.width)
            assertEquals(16.5, right.bounds.x)
        }
    }

    @Test
    fun `focus draws without measurement and action reads never become reactive dependencies`() {
        val pulse = signal(owner = null, 0)
        var actions = 0
        lateinit var button: UiElement
        scenes.currScene = UiRoot {
            button = Button("action") {
                pulse()
                actions++
            }
        }
        ui.onUpdate(0f)
        val measures = backend.measurements
        val root = scenes.currScene as UiRoot
        root.focus(button)
        ui.onUpdate(0f)
        assertEquals(measures, backend.measurements)
        assertTrue(backend.draws.single().focused)
        button.activate()
        pulse.update { 1 }
        ui.onUpdate(0f)
        assertEquals(1, actions)
    }

    @Test
    fun `wrapped fill and fractional text receive allocated width before height measurement`() {
        scenes.currScene = UiRoot {
            Column {
                Row {
                    Text("abcdefgh").also { it.style = UiStyle(width = UiLength.Fill) }
                    Text("ijklmnop").also { it.style = UiStyle(width = UiLength.Fill) }
                }
                Text("abcdefgh").also { it.style = UiStyle(width = UiLength.Fraction(0.25)) }
            }
        }
        ui.onResize(8, 10)
        ui.onUpdate(0f)
        assertEquals(listOf(2.0, 2.0, 4.0), backend.draws.map { it.bounds.height })
        assertEquals(listOf(0.0, 0.0, 2.0), backend.draws.map { it.bounds.y })
    }

    @Test
    fun `branch local state destroys on omission and independent observers survive failed sibling`() {
        val show = signal(owner = null, true)
        val first = signal(owner = null, 0)
        val second = signal(owner = null, 0)
        lateinit var local: Signal<Int>
        scenes.currScene = UiRoot {
            structure("branch") {
                if (show()) {
                    local = remember("local") { signal(0) }
                    bindText("local-text") { "${local()}" }
                }
            }
            bindText("first") { if (first() == 1) error("bad value") else "first:${first()}" }
            bindText("second") { "second:${second()}" }
        }
        ui.onUpdate(0f)
        val removed = local
        show.update { false }
        first.update { 1 }
        second.update { 2 }
        assertFailsWith<IllegalStateException> { ui.onUpdate(0f) }
        scenes.onUpdate(0f)
        assertFailsWith<NodeDestroyedException> { removed() }
        first.update { 3 }
        ui.onUpdate(0f)
        assertEquals(listOf("first:3", "second:2"), backend.draws.map { it.text })
        show.update { true }
        ui.onUpdate(0f)
        assertNotSame(removed, local)
    }

    @Test
    fun `fill caps redistribute space on both layout axes and resize`() {
        lateinit var rowCapped: UiElement
        lateinit var rowFill: UiElement
        lateinit var columnCapped: UiElement
        lateinit var columnFill: UiElement
        scenes.currScene = UiRoot {
            Row(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                rowCapped = Text("left").also { it.style = UiStyle(width = UiLength.Fill, maxWidth = 10.0) }
                rowFill = Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                    columnCapped = Text("top").also {
                        it.style = UiStyle(height = UiLength.Fill, maxHeight = 3.0)
                    }
                    columnFill = Text("bottom").also { it.style = UiStyle(height = UiLength.Fill) }
                }
            }
        }
        ui.onResize(80, 24)
        ui.onUpdate(0f)
        assertEquals(10.0, rowCapped.bounds.width)
        assertEquals(70.0, rowFill.bounds.width)
        assertEquals(3.0, columnCapped.bounds.height)
        assertEquals(21.0, columnFill.bounds.height)
        ui.onResize(10, 4)
        ui.onUpdate(0f)
        assertEquals(5.0, rowCapped.bounds.width)
        assertEquals(5.0, rowFill.bounds.width)
        assertEquals(2.0, columnCapped.bounds.height)
        assertEquals(2.0, columnFill.bounds.height)
    }

    @Test
    fun `root constraints clip child drawing and reject invalid maximums`() {
        val root = UiRoot { Text("overflow").also { it.style = UiStyle(width = UiLength.Fixed(20.0)) } }
        root.style = UiStyle(width = UiLength.Fixed(4.0), height = UiLength.Fixed(2.0))
        root.horizontalAlignment = UiAlignment.End
        scenes.currScene = root
        ui.onResize(10, 4)
        ui.onUpdate(0f)
        assertEquals(UiRect(6.0, 0.0, 4.0, 2.0), root.bounds)
        assertEquals(UiRect(6.0, 0.0, 4.0, 1.0), backend.draws.single().clip)
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { UiStyle(maxWidth = invalid) }
            assertFailsWith<IllegalArgumentException> { UiStyle(maxHeight = invalid) }
        }
    }

    @Test
    fun `wrapped capped fill measurement matches final redistributed allocation`() {
        lateinit var row: UiElement
        scenes.currScene = UiRoot {
            row = Row {
                Text("abcdefghij").also { it.style = UiStyle(width = UiLength.Fill, maxWidth = 2.0) }
                Text("abcdefghij").also { it.style = UiStyle(width = UiLength.Fill) }
            }
        }
        ui.onResize(10, 20)
        ui.onUpdate(0f)
        assertEquals(5.0, row.bounds.height)
        assertEquals(listOf(2.0, 8.0), backend.draws.map { it.bounds.width })
        assertEquals(listOf(5.0, 2.0), backend.draws.map { it.bounds.height })
    }

    @Test
    fun `reusable root exit releases focus and renews cleanup without accumulating destruction handlers`() {
        lateinit var button: UiElement
        val root = UiRoot { button = Button("action") {} }
        scenes.currScene = root
        ui.onUpdate(0f)
        val destructionCount = root.state("test cleanup registrations").destruction.size
        repeat(3) {
            root.focus(button)
            root.nodeExitTree()
            assertNull(root.focusedElement)
            root.buildTree()
            assertNull(root.focusedElement)
            assertFalse(io.canopy.engine.core.managers.manager<InputFocus>().blocksGameplay)
            assertEquals(destructionCount, root.state("test cleanup registrations").destruction.size)
        }
    }

    @Test
    fun `repeated keyed observer replacement retains bounded cleanup registrations`() {
        val tick = signal(owner = null, 0)
        lateinit var text: UiElement
        scenes.currScene = UiRoot {
            structure("list") {
                tick()
                key("same") { text = bindText("value") { "${tick()}" } }
            }
        }
        ui.onUpdate(0f)
        val removals = text.state("test cleanup registrations").removal.size
        val destructions = text.state("test cleanup registrations").destruction.size
        repeat(20) { value ->
            tick.update { value + 1 }
            ui.onUpdate(0f)
            assertEquals(removals, text.state("test cleanup registrations").removal.size)
            assertEquals(destructions, text.state("test cleanup registrations").destruction.size)
        }
    }

    @Test
    fun `failed observer construction releases callbacks and signal subscriptions`() {
        val dependency = signal(owner = null, 0)
        val root = UiRoot {}
        scenes.currScene = root
        val removals = root.state("test cleanup registrations").removal.size
        val destructions = root.state("test cleanup registrations").destruction.size
        assertFailsWith<IllegalStateException> {
            UiObserver(root) {
                dependency()
                error("cannot install")
            }
        }
        assertEquals(removals, root.state("test cleanup registrations").removal.size)
        assertEquals(destructions, root.state("test cleanup registrations").destruction.size)
        dependency.update { 1 }
        ui.onUpdate(0f)
    }
}
