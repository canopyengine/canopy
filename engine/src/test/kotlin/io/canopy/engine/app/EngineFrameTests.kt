package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class EngineFrameTests {
    @Test
    fun `frame preparation precedes physics and presentation follows update`() {
        // Arrange
        val events = mutableListOf<String>()
        val loop = EngineLoop(
            onEnter = {},
            onUpdate = { events += "update" },
            onPhysicsUpdate = { events += "physics" },
            onResize = { _, _ -> },
            onExit = {},
            physicsStep = 0.1f,
            onBeginFrame = { events += "begin" },
            onPresentFrame = { events += "present" },
            onAbortFrame = { events += "abort" }
        )
        loop.enter()

        // Act
        loop.update(0.1f)

        // Assert
        assertEquals(listOf("begin", "physics", "update", "present"), events)
        assertFailsWith<IllegalArgumentException> { loop.update(Float.NaN) }
        assertEquals(4, events.size)
        loop.exit()
    }

    @Test
    fun `failure at any frame stage aborts once and retains the original failure`() {
        for (stage in listOf("begin", "physics", "update", "present")) {
            // Arrange
            val events = mutableListOf<String>()
            val failure = IllegalStateException(stage)
            fun visit(name: String) {
                events += name
                if (name == stage) throw failure
            }
            val loop = EngineLoop(
                onEnter = {},
                onUpdate = { visit("update") },
                onPhysicsUpdate = { visit("physics") },
                onResize = { _, _ -> },
                onExit = {},
                physicsStep = 0.1f,
                onBeginFrame = { visit("begin") },
                onPresentFrame = { visit("present") },
                onAbortFrame = { events += "abort" }
            )
            loop.enter()

            // Act / Assert
            assertSame(failure, assertFailsWith<IllegalStateException> { loop.update(0.1f) })
            val ordered = listOf("begin", "physics", "update", "present")
            assertEquals(ordered.take(ordered.indexOf(stage) + 1) + "abort", events)
            assertSame(failure, loop.failure)
            loop.exit()
        }
    }

    @Test
    fun `abort failure is suppressed without replacing the processing failure`() {
        // Arrange
        val failure = IllegalStateException("update")
        val cleanup = IllegalArgumentException("abort")
        val loop = EngineLoop(
            onEnter = {},
            onUpdate = { throw failure },
            onPhysicsUpdate = {},
            onResize = { _, _ -> },
            onExit = {},
            onAbortFrame = { throw cleanup }
        )
        loop.enter()

        // Act / Assert
        assertSame(failure, assertFailsWith<IllegalStateException> { loop.update(0f) })
        assertEquals(listOf(cleanup), failure.suppressed.toList())
        assertSame(failure, loop.failure)
        loop.exit()
    }

    @Test
    fun `native frame hooks cannot reenter lifecycle dispatch`() {
        // Arrange
        var presented = 0
        lateinit var loop: EngineLoop
        loop = EngineLoop(
            onEnter = {},
            onUpdate = {},
            onPhysicsUpdate = {},
            onResize = { _, _ -> },
            onExit = {},
            onBeginFrame = {
                assertFailsWith<IllegalStateException> { loop.update(0f) }
                assertFailsWith<IllegalStateException> { loop.resize(80, 24) }
                assertFailsWith<IllegalStateException> { loop.exit() }
            },
            onPresentFrame = { presented++ }
        )
        loop.enter()

        // Act
        loop.update(0f)
        loop.update(0f)

        // Assert
        assertEquals(2, presented)
        loop.exit()
    }

    @Test
    fun `direct app frames notify changed geometry before input and update`() {
        // Arrange
        val events = mutableListOf<String>()
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
            override fun beginFrame(): FrameSize {
                events += "begin"
                return FrameSize(80, 24)
            }
            override fun processFrameInput() {
                events += "input"
            }
            override fun presentFrame() {
                events += "present"
            }
        }
        app.onResize { width, height -> events += "resize:$width,$height" }
        app.onUpdate { events += "update" }
        app.enter()
        try {
            // Act
            app.update(0f)
            app.update(0f)

            // Assert
            assertEquals(
                listOf("begin", "resize:80,24", "input", "update", "present", "begin", "input", "update", "present"),
                events
            )
        } finally {
            app.exit()
        }
    }
}
