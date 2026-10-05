package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent
import io.canopy.engine.math.Vector2

class InputManagerTests {
    private class TestInputManager : InputManager() {
        val pressed = mutableSetOf<InputBind>()
        val handled = mutableListOf<InputEvent>()

        override fun pollPressed(bind: InputBind): Boolean = bind in pressed
        override fun handleEvent(event: InputEvent) {
            handled += event
        }

        fun publishPair(firstPublished: CountDownLatch, publishSecond: CountDownLatch) {
            synchronized(eventQueue) {
                enqueue(KeyInputEvent(Key.Q_KEY, state = InputState.JustPressed))
                firstPublished.countDown()
                check(publishSecond.await(10, TimeUnit.SECONDS)) { "Batch publication was not released" }
                enqueue(TextInputEvent("q"))
            }
        }
    }

    @Test
    fun `a frame cannot observe a partially published backend key and text batch`() {
        // Arrange: hold the publication monitor between the key and its related text.
        val input = TestInputManager()
        val firstPublished = CountDownLatch(1)
        val publishSecond = CountDownLatch(1)
        val producerTask = FutureTask { input.publishPair(firstPublished, publishSecond) }
        val producer = Thread(producerTask)
        val consumerTask = FutureTask { input.processEvents() }
        val consumer = Thread(consumerTask)
        producer.start()
        assertTrue(firstPublished.await(10, TimeUnit.SECONDS))
        consumer.start()
        try {
            // Act: wait for observable blocking or completion, rather than guessing thread execution order.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (consumer.state != Thread.State.BLOCKED && !consumerTask.isDone && System.nanoTime() < deadline) {
                Thread.yield()
            }
            // Assert: polling waits for complete publication; it cannot return the key alone.
            assertEquals(Thread.State.BLOCKED, consumer.state)
            assertTrue(input.handled.isEmpty())
        } finally {
            publishSecond.countDown()
            producerTask.get(10, TimeUnit.SECONDS)
            consumerTask.get(10, TimeUnit.SECONDS)
        }
        assertEquals(
            listOf(KeyInputEvent(Key.Q_KEY, state = InputState.JustPressed), TextInputEvent("q")),
            input.eventsThisFrame
        )
    }

    @Test
    fun `action state advances through press and release edges`() {
        val input = TestInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE))

        input.pressed += InputBind.SPACE
        input.processEvents()
        assertEquals(InputState.JustPressed, input.getActionState("jump"))
        assertTrue(input.isActionPressed("jump"))

        input.processEvents()
        assertEquals(InputState.Pressed, input.getActionState("jump"))

        input.pressed -= InputBind.SPACE
        input.processEvents()
        assertEquals(InputState.JustReleased, input.getActionState("jump"))
        assertTrue(input.isActionReleased("jump"))

        input.processEvents()
        assertEquals(InputState.Released, input.getActionState("jump"))
        assertFalse(input.isActionPressed("jump"))
    }

    @Test
    fun `axes and input vector follow mapped actions`() {
        val input = TestInputManager()
        input.mapActions(
            "left" to listOf(InputBind.A),
            "right" to listOf(InputBind.D),
            "up" to listOf(InputBind.W),
            "down" to listOf(InputBind.S)
        )
        input.pressed.addAll(listOf(InputBind.D, InputBind.W))
        input.processEvents()

        assertEquals(1f, input.getAxis("left", "right"))
        assertEquals(Vector2(1f, 1f), input.getInputVector("left", "right", "down", "up"))

        input.pressed += InputBind.A
        input.processEvents()
        assertEquals(0f, input.getAxis("left", "right"))
    }

    @Test
    fun `process events drains queue and exposes frame events once`() {
        val input = TestInputManager()
        val event = KeyInputEvent(Key.SPACE, state = InputState.JustPressed)
        val expectedEvents: List<InputEvent> = listOf(event)
        input.enqueue(event)

        input.processEvents()

        assertEquals(expectedEvents, input.handled)
        assertEquals(expectedEvents, input.eventsThisFrame)
        assertEquals(expectedEvents, input.consumeEventsThisFrame())
        assertTrue(input.consumeEventsThisFrame().isEmpty())

        input.processEvents()
        assertTrue(input.eventsThisFrame.isEmpty())
    }

    @Test
    fun `mappings can be extended removed and cleared`() {
        val input = TestInputManager()
        input.mapActions("move" to listOf(InputBind.A))
        input.mapActions("move" to listOf(InputBind.D), replace = false)
        input.pressed += InputBind.D
        input.processEvents()

        assertTrue(input.isActionJustPressed("move"))
        assertEquals(1f, input.getAxis("missing", "move"))
        assertTrue(input.isPressed(InputBind.D))

        input.unmapAction("move")
        assertEquals(InputState.Released, input.getActionState("move"))
        input.mapActions("move" to listOf(InputBind.D))
        input.clearMappings()
        assertEquals(InputState.Released, input.getActionState("move"))
        assertFalse(input.isActionJustReleased("move"))
    }

    @Test
    fun `input mapper serializes and restores keyboard and mouse mappings`() {
        val mapper = InputMapper()
        mapper.mapActions("select" to listOf(InputBind.ENTER, InputBind.LEFT_MOUSE))

        val data: InputData = mapper.toData()
        val restored = InputMapper()
        restored.loadData(data)

        assertEquals(mapper.actions, restored.actions)
        restored.mapActions("select" to listOf(InputBind.SPACE))
        assertEquals(mapOf("select" to listOf(InputBind.SPACE)), restored.actions)

        restored.unmapAction("select")
        restored.clearMappings()
        assertTrue(restored.actions.isEmpty())
    }
}
