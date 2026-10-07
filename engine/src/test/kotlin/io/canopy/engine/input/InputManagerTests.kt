package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
        var onHandle: (InputEvent) -> Unit = {}

        override fun pollPressed(bind: InputBind): Boolean = bind in pressed
        override fun handleEvent(event: InputEvent) {
            handled += event
            onHandle(event)
        }
    }

    @Test
    fun `batch preparation exposes no partial pair and does not block unrelated publication`() {
        // Arrange
        val input = TestInputManager()
        val preparedFirst = CountDownLatch(1)
        val release = CountDownLatch(1)
        val key = KeyInputEvent(Key.Q_KEY, state = InputState.JustPressed)
        val text = TextInputEvent("q")
        val unrelated = TextInputEvent("unrelated")
        val batch = sequence<InputEvent> {
            yield(key)
            preparedFirst.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Batch preparation was not released" }
            yield(text)
        }.asIterable()
        val producerTask = FutureTask { input.enqueueBatch(batch) }
        val unrelatedTask = FutureTask { input.enqueue(unrelated) }
        val consumerTask = FutureTask { input.processEvents() }
        val producer = Thread(producerTask)
        val otherProducer = Thread(unrelatedTask)
        val consumer = Thread(consumerTask)
        producer.start()
        try {
            assertTrue(preparedFirst.await(10, TimeUnit.SECONDS))

            // Act: independent publication and draining must finish while caller iteration is paused.
            otherProducer.start()
            unrelatedTask.get(10, TimeUnit.SECONDS)
            consumer.start()
            consumerTask.get(10, TimeUnit.SECONDS)

            // Assert
            assertEquals(listOf<InputEvent>(unrelated), input.eventsThisFrame)
        } finally {
            release.countDown()
            listOf(producer, otherProducer, consumer).forEach { it.join(10_000) }
        }
        producerTask.get(10, TimeUnit.SECONDS)
        input.processEvents()
        assertEquals(listOf<InputEvent>(key, text), input.eventsThisFrame)
    }

    @Test
    fun `failed batch iteration publishes nothing and empty batches leave queued events intact`() {
        // Arrange
        val input = TestInputManager()
        val retained = TextInputEvent("retained")
        val failure = IllegalStateException("iteration")
        input.enqueue(retained)
        val batch = sequence<InputEvent> {
            yield(TextInputEvent("partial"))
            throw failure
        }.asIterable()

        // Act
        assertSame(failure, assertFailsWith<IllegalStateException> { input.enqueueBatch(batch) })
        input.enqueueBatch(emptyList())
        input.processEvents()

        // Assert
        assertEquals(listOf<InputEvent>(retained), input.eventsThisFrame)
    }

    @Test
    fun `batch snapshots caller collection while retaining event identities`() {
        // Arrange
        val input = TestInputManager()
        val first = TextInputEvent("first")
        val second = TextInputEvent("second")
        val batch = mutableListOf<InputEvent>(first, second)

        // Act
        input.enqueueBatch(batch)
        batch.clear()
        batch += TextInputEvent("replacement")
        input.processEvents()

        // Assert
        assertEquals(listOf<InputEvent>(first, second), input.eventsThisFrame)
        assertSame(first, input.eventsThisFrame.first())
        assertSame(second, input.eventsThisFrame.last())
    }

    @Test
    fun `concurrent producers keep every pair contiguous while the engine drains frames`() {
        // Arrange
        val input = TestInputManager()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val firstPairsConsumed = CountDownLatch(2)
        input.onHandle = { event ->
            if (event is TextInputEvent && event.text.endsWith(":0")) firstPairsConsumed.countDown()
        }
        val tasks = (0..1).map { producer ->
            FutureTask {
                ready.countDown()
                check(start.await(10, TimeUnit.SECONDS)) { "Producers were not released" }
                repeat(120) { index ->
                    input.enqueueBatch(
                        listOf(
                            KeyInputEvent(Key.Q_KEY, shift = producer == 1, state = InputState.JustPressed),
                            TextInputEvent("$producer:$index")
                        )
                    )
                    if (index == 0) {
                        check(firstPairsConsumed.await(10, TimeUnit.SECONDS)) { "Initial pairs were not consumed" }
                    }
                }
            }
        }
        val threads = tasks.map { Thread(it).also(Thread::start) }
        val frames = mutableListOf<List<InputEvent>>()
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            // Act
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            do {
                input.processEvents()
                frames += input.eventsThisFrame.toList()
                check(System.nanoTime() < deadline) { "Producers did not finish" }
            } while (tasks.any { !it.isDone })
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            input.processEvents()
            frames += input.eventsThisFrame.toList()
        } finally {
            start.countDown()
            repeat(2) { firstPairsConsumed.countDown() }
            threads.forEach { it.join(10_000) }
        }

        // Assert: inter-producer order is unspecified, but pairs and each producer's order are preserved.
        val seen = mutableMapOf(0 to mutableListOf<Int>(), 1 to mutableListOf<Int>())
        for (frame in frames) {
            assertEquals(0, frame.size % 2)
            for (pair in frame.chunked(2)) {
                val key = pair.first() as KeyInputEvent
                val text = pair.last() as TextInputEvent
                val (producer, index) = text.text.split(':').map(String::toInt)
                assertEquals(producer == 1, key.shift)
                seen.getValue(producer) += index
            }
        }
        assertEquals((0 until 120).toList(), seen.getValue(0))
        assertEquals((0 until 120).toList(), seen.getValue(1))
        assertEquals(480, input.handled.size)
    }

    @Test
    fun `callback can wait for a producer and its new batch drains in the same frame`() {
        // Arrange
        val input = TestInputManager()
        val first = TextInputEvent("first")
        val pending = TextInputEvent("pending")
        val key = KeyInputEvent(Key.Q_KEY, state = InputState.JustPressed)
        val text = TextInputEvent("q")
        val producerTask = FutureTask { input.enqueueBatch(listOf(key, text)) }
        val producer = Thread(producerTask)
        input.enqueueBatch(listOf(first, pending))
        input.onHandle = { event ->
            if (event === first) {
                producer.start()
                producerTask.get(10, TimeUnit.SECONDS)
            }
        }

        // Act
        try {
            input.processEvents()
        } finally {
            producer.join(10_000)
        }

        // Assert
        assertEquals(listOf<InputEvent>(first, pending, key, text), input.eventsThisFrame)
    }

    @Test
    fun `event callback failure leaves batch remainder pending and skips action recomputation`() {
        // Arrange
        val input = TestInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE))
        input.pressed += InputBind.SPACE
        val first = TextInputEvent("first")
        val remaining = TextInputEvent("remaining")
        val failure = IllegalStateException("callback")
        input.enqueueBatch(listOf(first, remaining))
        input.onHandle = { if (it === first) throw failure }

        // Act
        assertSame(failure, assertFailsWith<IllegalStateException> { input.processEvents() })

        // Assert
        assertTrue(input.eventsThisFrame.isEmpty())
        assertEquals(InputState.Released, input.getActionState("jump"))
        input.onHandle = {}
        input.processEvents()
        assertEquals(listOf<InputEvent>(first, remaining), input.handled)
        assertEquals(listOf<InputEvent>(remaining), input.eventsThisFrame)
        assertEquals(InputState.JustPressed, input.getActionState("jump"))
    }

    @Test
    fun `final drain after producers stop preserves pending events across manager exit`() {
        // Arrange
        val input = TestInputManager()
        val first = TextInputEvent("before-exit")
        val second = TextInputEvent("after-exit")
        val producerTask = FutureTask { input.enqueue(first) }
        val producer = Thread(producerTask)
        producer.start()
        try {
            producerTask.get(10, TimeUnit.SECONDS)
        } finally {
            producer.join(10_000)
        }

        // Act: inherited exit does not discard or automatically process pending input.
        input.onExit()
        input.enqueueBatch(listOf(second))
        assertTrue(input.handled.isEmpty())
        input.processEvents()

        // Assert
        assertEquals(listOf<InputEvent>(first, second), input.eventsThisFrame)
        input.processEvents()
        assertTrue(input.eventsThisFrame.isEmpty())
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
