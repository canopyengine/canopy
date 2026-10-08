package io.canopy.engine.ui.integration

import kotlin.test.*
import io.canopy.engine.core.flows.events.Signal
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class UiCompilerIntegrationTests {
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
        override fun drawText(text: String, bounds: UiRect, clip: UiRect, focused: Boolean, wrap: Boolean) {
            draws += Draw(text, bounds)
        }
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
        ui.onUpdate(0f)
        counters.getValue("a").update { 4 }
        ui.onUpdate(0f)
        // Act
        visible.update { false }
        width.update { 1.0 }
        items.update { listOf(Item("b", "changed"), Item("a", "retained")) }
        ui.onUpdate(0f)
        // Assert
        assertEquals(1, rootRuns)
        assertEquals(2, counterCreations)
        assertEquals(1, componentCreations)
        assertEquals(listOf("changed:0", "retained:4", "one", "two"), backend.draws.map { it.text })
        visible.update { true }
        ui.onUpdate(0f)
        assertEquals(2, componentCreations)
        width.update { 0.75 }
        ui.onUpdate(0f)
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
