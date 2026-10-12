package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.effect
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.components.Button
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
        data class Draw(
            val text: String,
            val bounds: UiRect,
            val clip: UiRect,
            val focused: Boolean,
            val appearance: UiAppearance,
        )
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
        override fun drawText(
            text: String,
            bounds: UiRect,
            clip: UiRect,
            focused: Boolean,
            wrap: Boolean,
            appearance: UiAppearance,
        ) {
            draws += Draw(text, bounds, clip, focused, appearance)
        }
    }

    @Test
    fun `appearance and theme changes repaint without remeasuring and explicit overrides reset inheritance`() {
        // Arrange
        lateinit var label: io.canopy.engine.ui.components.Text
        lateinit var button: Button
        val red = UiColor.Rgb(230, 40, 50)
        val blue = UiColor.Rgb(40, 80, 230)
        val root = UiRoot {
            Column(UiStyle(appearance = UiAppearance(foreground = red, bold = true))) {
                label = Text("label")
                button = Button("action") {}
            }
        }
        scenes.currScene = root
        ui.updateFrame()
        val measurements = backend.measurements
        assertEquals(red, backend.draws.first().appearance.foreground)
        assertEquals(true, backend.draws.first().appearance.bold)

        // Act / Assert
        label.style = label.style.copy(appearance = UiAppearance(foreground = UiColor.Default, bold = false))
        root.theme = UiTheme(button = UiAppearance(foreground = blue))
        ui.updateFrame()
        assertEquals(measurements, backend.measurements)
        assertEquals(UiColor.Default, backend.draws.first().appearance.foreground)
        assertEquals(false, backend.draws.first().appearance.bold)
        root.focus(button)
        ui.updateFrame()
        assertEquals(measurements, backend.measurements)
        assertEquals(true, backend.draws.last().appearance.inverse)
        button.enabled = false
        ui.updateFrame()
        assertEquals(UiColor.Default, backend.draws.last().appearance.foreground)
        assertEquals(true, backend.draws.last().appearance.dim)
    }

    @Test
    fun `borders reserve content space once and update child placement`() {
        // Arrange
        lateinit var box: io.canopy.engine.ui.components.Box
        scenes.currScene = UiRoot {
            box = Box(UiStyle(width = UiLength.Fixed(8.0), height = UiLength.Fixed(4.0), border = UiBorder.Double)) {
                Text("inside")
            }
        }

        // Act / Assert
        ui.updateFrame()
        assertEquals(UiRect(1.0, 1.0, 6.0, 1.0), backend.draws.single().bounds)
        box.style = box.style.copy(border = UiBorder.None)
        ui.updateFrame()
        assertEquals(UiRect(0.0, 0.0, 6.0, 1.0), backend.draws.single().bounds)
        assertFailsWith<IllegalArgumentException> { UiColor.Rgb(256, 0, 0) }
    }

    @Test
    fun `failed frame stages abort without committing and the next frame can retry`() {
        for (phase in listOf("begin", "paint", "end")) {
            // Arrange
            val failure = IllegalStateException(phase)
            var fail = true
            var commits = 0
            var aborts = 0
            var draws = 0
            ui.backend = object : UiBackend by backend {
                override fun begin(viewport: UiSize) {
                    if (fail && phase == "begin") throw failure
                    backend.begin(viewport)
                }
                override fun drawText(
                    text: String,
                    bounds: UiRect,
                    clip: UiRect,
                    focused: Boolean,
                    wrap: Boolean,
                    appearance: UiAppearance,
                ) {
                    draws++
                    if (fail && phase == "paint" && draws == 2) throw failure
                    backend.drawText(text, bounds, clip, focused, wrap)
                }
                override fun end() {
                    if (fail && phase == "end") throw failure
                    commits++
                }
                override fun abort() {
                    aborts++
                }
            }
            scenes.currScene = UiRoot {
                Column {
                    Text("first")
                    Text("second")
                }
            }

            // Act / Assert
            assertSame(failure, assertFailsWith<IllegalStateException> { ui.renderNow() })
            assertEquals(0, commits)
            assertEquals(1, aborts)
            fail = false
            ui.renderNow()
            assertEquals(1, commits)
            assertEquals(listOf("first", "second"), backend.draws.map { it.text })
            ui.renderNow()
            assertEquals(1, commits)
        }
    }

    @Test
    fun `a failed changed frame retains the last successful comparison and retries current content`() {
        // Arrange
        lateinit var label: io.canopy.engine.ui.components.Text
        scenes.currScene = UiRoot { label = Text("first") }
        ui.updateFrame()
        var fail = true
        var commits = 0
        ui.backend = object : UiBackend by backend {
            override fun end() {
                if (fail) error("commit")
                commits++
            }
        }
        label.text = "second"

        // Act / Assert
        repeat(2) { assertFailsWith<IllegalStateException> { ui.updateFrame() } }
        assertEquals(0, commits)
        label.text = "third"
        fail = false
        ui.updateFrame()
        assertEquals(1, commits)
        assertEquals("third", backend.draws.single().text)
        repeat(3) { ui.updateFrame() }
        assertEquals(1, commits)
        label.text = "first"
        ui.updateFrame()
        assertEquals(2, commits)
        assertEquals("first", backend.draws.single().text)
    }

    @Test
    fun `nearest explicit appearance wins across nested containers and theme defaults`() {
        // Arrange
        val red = UiColor.Rgb(200, 20, 20)
        val green = UiColor.Rgb(20, 200, 20)
        val blue = UiColor.Rgb(20, 20, 200)
        lateinit var inner: io.canopy.engine.ui.components.Box
        val root = UiRoot {
            Box(UiStyle(appearance = UiAppearance(foreground = red, bold = true))) {
                inner = Box(UiStyle(appearance = UiAppearance(background = blue, bold = false))) {
                    Text("label").style = UiStyle(appearance = UiAppearance(foreground = UiColor.Default))
                }
            }
        }
        root.theme = UiTheme(text = UiAppearance(foreground = green, italic = true))
        scenes.currScene = root

        // Act / Assert
        ui.updateFrame()
        assertEquals(
            UiAppearance(foreground = UiColor.Default, background = blue, bold = false, italic = true),
            backend.draws.single().appearance
        )
        inner.style = inner.style.copy(appearance = UiAppearance(background = blue))
        ui.updateFrame()
        assertEquals(true, backend.draws.single().appearance.bold)
        root.theme = UiTheme(text = UiAppearance(italic = false))
        ui.updateFrame()
        assertEquals(false, backend.draws.single().appearance.italic)
    }

    @Test
    fun `root order visibility and removal remain detectable between equal frames`() {
        // Arrange
        val first = UiRoot("First") { Text("first") }
        val second = UiRoot("Second") { Text("second") }
        val world = EmptyNode("World").also {
            it.addChild(first)
            it.addChild(second)
        }
        scenes.currScene = world
        ui.updateFrame()
        assertEquals(listOf("first", "second"), backend.draws.map { it.text })
        val frames = backend.frames
        ui.updateFrame()
        assertEquals(frames, backend.frames)

        // Act / Assert
        first.layer = UiLayer.Overlay
        ui.updateFrame()
        assertEquals(listOf("second", "first"), backend.draws.map { it.text })
        second.layer = UiLayer.Overlay
        first.zIndex = 1
        ui.updateFrame()
        assertEquals(listOf("second", "first"), backend.draws.map { it.text })
        second.zIndex = 2
        ui.updateFrame()
        assertEquals(listOf("first", "second"), backend.draws.map { it.text })
        second.hide()
        ui.updateFrame()
        assertEquals("first", backend.draws.single().text)
        world.removeChild(second)
        ui.updateFrame()
        assertEquals("first", backend.draws.single().text)
        second.show()
        world.addChild(second)
        ui.updateFrame()
        assertEquals(listOf("first", "second"), backend.draws.map { it.text })
    }

    @Test
    fun `failed abort preserves the painting failure and suppresses the cleanup failure`() {
        // Arrange
        val painting = IllegalStateException("paint")
        val cleanup = IllegalArgumentException("abort")
        ui.backend = object : UiBackend by backend {
            override fun drawText(
                text: String,
                bounds: UiRect,
                clip: UiRect,
                focused: Boolean,
                wrap: Boolean,
                appearance: UiAppearance,
            ) = throw painting
            override fun end() = error("A failed paint must not commit")
            override fun abort() = throw cleanup
        }
        scenes.currScene = UiRoot { Text("content") }

        // Act / Assert
        assertSame(painting, assertFailsWith<IllegalStateException> { ui.renderNow() })
        assertEquals(listOf(cleanup), painting.suppressed.toList())
    }

    @Test
    fun `failed application updates defer painting and release the frame boundary`() {
        val count = signal(owner = null, 0)
        scenes.currScene = UiRoot { bindText("count") { "Count: ${count()}" } }
        ui.updateFrame()
        val frames = backend.frames
        val failure = IllegalStateException("update")

        assertSame(
            failure,
            assertFailsWith<IllegalStateException> {
                ui.afterUpdates {
                    count.update { 1 }
                    ui.renderNow()
                    assertEquals(frames, backend.frames)
                    throw failure
                }
            }
        )
        assertEquals(frames, backend.frames)
        ui.updateFrame()
        assertEquals("Count: 1", backend.draws.single().text)
        count.dispose()
    }

    @Test
    fun `column labels measure once and updating one label only remeasures that label`() {
        // Arrange
        val value = signal(owner = null, "first")
        scenes.currScene = UiRoot {
            Column {
                bindText("live") { value() }
                repeat(9) { Text("fixed") }
            }
        }

        // Act / Assert
        ui.updateFrame()
        assertEquals(10, backend.measurements)
        value.update { "other" }
        ui.updateFrame()
        assertEquals(11, backend.measurements)
        assertEquals("other", backend.draws.first().text)
        assertEquals((0..9).map(Int::toDouble), backend.draws.map { it.bounds.y })
        value.dispose()
    }

    @Test
    fun `height resize reuses text metrics while width resize updates wrapping and placement`() {
        // Arrange
        scenes.currScene = UiRoot {
            Column {
                Text("abcdefgh")
                Text("next")
            }
        }
        ui.onResize(8, 10)
        ui.updateFrame()
        val measurements = backend.measurements

        // Act / Assert
        ui.onResize(8, 20)
        ui.updateFrame()
        assertEquals(measurements, backend.measurements)
        assertEquals(listOf(0.0, 1.0), backend.draws.map { it.bounds.y })
        ui.onResize(4, 20)
        ui.updateFrame()
        assertEquals(measurements + 2, backend.measurements)
        assertEquals(listOf(2.0, 1.0), backend.draws.map { it.bounds.height })
        assertEquals(listOf(0.0, 2.0), backend.draws.map { it.bounds.y })
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
        ui.updateFrame()
        val frames = backend.frames
        val measures = backend.measurements
        // Act
        count.update { 2 }
        count.update { 3 }
        ui.updateFrame()
        // Assert
        assertEquals(1, rootRuns)
        assertEquals(2, leftRuns)
        assertEquals(1, rightRuns)
        assertEquals(frames, backend.frames)
        assertEquals(measures, backend.measurements)
        count.update { 20 }
        ui.updateFrame()
        assertEquals(listOf("2", "static"), backend.draws.map { it.text })
    }

    @Test
    fun `key reorder retains identity focus local state and owned work while omission disposes`() {
        // Arrange
        val ids = signal(owner = null, listOf("a", "b"))
        val pulse = signal(owner = null, 0)
        val counters = mutableMapOf<String, Signal<Int>>()
        val buttons = mutableMapOf<String, Button>()
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
        ui.updateFrame()
        val a = buttons.getValue("a")
        root.focus(a)
        a.activate()
        ui.updateFrame()
        // Act
        ids.update { listOf("b", "a") }
        ui.updateFrame()
        // Assert
        assertSame(a, buttons.getValue("a"))
        assertSame(a, root.focusedElement)
        assertEquals(listOf("a", "b"), creations)
        assertEquals(listOf("b:0", "a:1"), backend.draws.map { it.text })
        ids.update { listOf("b") }
        ui.updateFrame()
        scenes.onUpdate(0f)
        assertTrue(a.isFreed)
        assertNull(root.focusedElement)
        assertFailsWith<NodeDestroyedException> { counters.getValue("a")() }
        effects.clear()
        pulse.update { 1 }
        assertEquals(listOf("b"), effects)
        ids.update { listOf("a", "b") }
        ui.updateFrame()
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
        ui.updateFrame()
        count.update { 5 }
        // Act
        root.nodeExitTree()
        ui.updateFrame()
        count.update { 7 }
        root.buildTree()
        ui.updateFrame()
        // Assert
        assertEquals(1, constructions)
        assertEquals("7", backend.draws.single().text)
    }

    @Test
    fun `hidden element reserves layout space but cannot draw or retain focus`() {
        // Arrange
        lateinit var button: Button
        val root = UiRoot {
            Column {
                button = Button("action") {}
                Text("next")
            }
        }
        scenes.currScene = root
        ui.updateFrame()
        root.focus(button)
        val nextY = backend.draws.last().bounds.y
        // Act
        button.hide()
        ui.updateFrame()
        // Assert
        assertNull(root.focusedElement)
        assertEquals(listOf("next"), backend.draws.map { it.text })
        assertEquals(nextY, backend.draws.single().bounds.y)
        button.show()
        ui.updateFrame()
        assertEquals(listOf("action", "next"), backend.draws.map { it.text })
    }

    @Test
    fun `resize recomputes fractions fill gap padding alignment and clipping in cell and pixel metrics`() {
        for (metrics in listOf(RecordingBackend(), RecordingBackend(8.0, 16.0))) {
            // Arrange
            lateinit var left: UiNode<*>
            lateinit var right: UiNode<*>
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
            ui.updateFrame()
            assertEquals(24.0, left.bounds.width)
            assertEquals(69.0, right.bounds.width)
            assertTrue(metrics.draws.all { it.clip.width <= it.bounds.width })
            // Act
            ui.onResize(50, 30)
            ui.updateFrame()
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
        lateinit var button: Button
        scenes.currScene = UiRoot {
            button = Button("action") {
                pulse()
                actions++
            }
        }
        ui.updateFrame()
        val measures = backend.measurements
        val root = scenes.currScene as UiRoot
        root.focus(button)
        ui.updateFrame()
        assertEquals(measures, backend.measurements)
        assertTrue(backend.draws.single().focused)
        button.activate()
        pulse.update { 1 }
        ui.updateFrame()
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
        ui.updateFrame()
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
        ui.updateFrame()
        val removed = local
        show.update { false }
        first.update { 1 }
        second.update { 2 }
        assertFailsWith<IllegalStateException> { ui.updateFrame() }
        scenes.onUpdate(0f)
        assertFailsWith<NodeDestroyedException> { removed() }
        first.update { 3 }
        ui.updateFrame()
        assertEquals(listOf("first:3", "second:2"), backend.draws.map { it.text })
        show.update { true }
        ui.updateFrame()
        assertNotSame(removed, local)
    }

    @Test
    fun `fill caps redistribute space on both layout axes and resize`() {
        lateinit var rowCapped: UiNode<*>
        lateinit var rowFill: UiNode<*>
        lateinit var columnCapped: UiNode<*>
        lateinit var columnFill: UiNode<*>
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
        ui.updateFrame()
        assertEquals(10.0, rowCapped.bounds.width)
        assertEquals(70.0, rowFill.bounds.width)
        assertEquals(3.0, columnCapped.bounds.height)
        assertEquals(21.0, columnFill.bounds.height)
        ui.onResize(10, 4)
        ui.updateFrame()
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
        ui.updateFrame()
        assertEquals(UiRect(6.0, 0.0, 4.0, 2.0), root.bounds)
        assertEquals(UiRect(6.0, 0.0, 4.0, 1.0), backend.draws.single().clip)
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { UiStyle(maxWidth = invalid) }
            assertFailsWith<IllegalArgumentException> { UiStyle(maxHeight = invalid) }
        }
    }

    @Test
    fun `wrapped capped fill measurement matches final redistributed allocation`() {
        lateinit var row: UiNode<*>
        scenes.currScene = UiRoot {
            row = Row {
                Text("abcdefghij").also { it.style = UiStyle(width = UiLength.Fill, maxWidth = 2.0) }
                Text("abcdefghij").also { it.style = UiStyle(width = UiLength.Fill) }
            }
        }
        ui.onResize(10, 20)
        ui.updateFrame()
        assertEquals(5.0, row.bounds.height)
        assertEquals(listOf(2.0, 8.0), backend.draws.map { it.bounds.width })
        assertEquals(listOf(5.0, 2.0), backend.draws.map { it.bounds.height })
    }

    @Test
    fun `reusable root exit releases focus and renews cleanup without accumulating destruction handlers`() {
        lateinit var button: Button
        val root = UiRoot { button = Button("action") {} }
        scenes.currScene = root
        ui.updateFrame()
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
        lateinit var text: UiNode<*>
        scenes.currScene = UiRoot {
            structure("list") {
                tick()
                key("same") { text = bindText("value") { "${tick()}" } }
            }
        }
        ui.updateFrame()
        val removals = text.state("test cleanup registrations").removal.size
        val destructions = text.state("test cleanup registrations").destruction.size
        repeat(20) { value ->
            tick.update { value + 1 }
            ui.updateFrame()
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
        ui.updateFrame()
    }
}
