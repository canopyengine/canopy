package io.canopy.engine.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import io.canopy.engine.core.flows.events.Input
import io.canopy.engine.core.flows.events.computed
import io.canopy.engine.core.flows.events.connect
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class InputCompilerIntegrationTests {
    private lateinit var scenes: SceneManager

    private class Fish : Node<Fish>("fish")

    private class Counter(@Input val population: Int) : Node<Counter>("counter")

    private class Aquarium : Node<Aquarium>("aquarium") {
        val fishCount by computed { getChildrenOfType<Fish>().size }
        var counter: Counter? = null

        override fun onSetup() {
            val created = Counter(fishCount)
            counter = created
            addChild(created)
        }
    }

    private class Light(@Input val intensity: Float, @Input val audioGain: Float) : Node<Light>("light")

    private class ChangingNode(@Input val amount: Int, changeDuringConstruction: () -> Unit) :
        Node<ChangingNode>("changing") {
        init {
            changeDuringConstruction()
        }
    }

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test
    fun `hierarchy-derived input updates a retained gameplay node`() {
        // Arrange
        val aquarium = Aquarium()
        scenes.currScene = aquarium
        val counter = aquarium.counter!!
        assertEquals(0, counter.population)

        // Act
        val fish = Fish()
        aquarium.addChild(fish)

        // Assert
        assertSame(counter, aquarium.counter)
        assertEquals(1, counter.population)
        aquarium.removeChild(fish)
        assertEquals(0, counter.population)
    }

    @Test
    fun `compiler-installed input resynchronizes a source changed during construction`() {
        val source = signal(owner = null, value = 1)
        var evaluations = 0
        val node = ChangingNode(
            source().also { evaluations++ },
            changeDuringConstruction = { source.update { 2 } }
        )

        assertEquals(2, node.amount)
        assertEquals(2, evaluations)
        scenes.currScene = node
        source.update { 3 }
        assertEquals(3, node.amount)
    }

    @Test
    fun `non-UI inputs track multiple and conditional sources without an initial connection callback`() {
        // Arrange
        val brightness = signal(owner = null, value = 0.5f)
        val visibility = signal(owner = null, value = 0.5f)
        val enabled = signal(owner = null, value = true)
        var intensityEvaluations = 0
        val light = Light(
            intensity = if (enabled()) {
                intensityEvaluations++
                brightness() * visibility()
            } else {
                intensityEvaluations++
                0f
            },
            audioGain = brightness()
        )
        scenes.currScene = light
        val changes = mutableListOf<Float>()
        val connection = (light::intensity).connect { changes += it }
        assertEquals(0.25f, light.intensity)
        assertEquals(0.5f, light.audioGain)
        assertEquals(1, intensityEvaluations)
        assertEquals(emptyList(), changes)

        // Act / Assert
        brightness.update { 1f }
        assertEquals(0.5f, light.intensity)
        assertEquals(1f, light.audioGain)
        assertEquals(listOf(0.5f), changes)

        enabled.update { false }
        assertEquals(0f, light.intensity)
        assertEquals(3, intensityEvaluations)
        visibility.update { 0.25f }
        assertEquals(3, intensityEvaluations)

        enabled.update { true }
        assertEquals(0.25f, light.intensity)
        assertEquals(listOf(0.5f, 0f, 0.25f), changes)
        connection.disconnect()
    }
}
