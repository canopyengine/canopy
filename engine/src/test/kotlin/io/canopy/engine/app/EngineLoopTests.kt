package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EngineLoopTests {

    @Test
    fun `coordinates lifecycle and fixed-step updates`() {
        val events = mutableListOf<String>()
        val loop = EngineLoop(
            onEnter = { events += "enter" },
            onUpdate = { events += "update:$it" },
            onPhysicsUpdate = { events += "physics:$it" },
            onResize = { width, height -> events += "resize:$width,$height" },
            onExit = { events += "exit" },
            physicsStep = 0.1f
        )

        loop.enter()
        loop.enter()
        loop.update(0.05f)
        loop.update(0.05f)
        loop.update(0.25f)
        loop.resize(800, 600)
        loop.exit()
        loop.exit()

        assertEquals(
            listOf(
                "enter",
                "update:0.05",
                "physics:0.1",
                "update:0.05",
                "physics:0.1",
                "physics:0.1",
                "update:0.25",
                "resize:800,600",
                "exit"
            ),
            events
        )
    }

    @Test
    fun `paused updates skip physics and pass zero delta`() {
        val events = mutableListOf<String>()
        var paused = true
        val loop = EngineLoop(
            onEnter = {},
            onUpdate = { events += "update:$it" },
            onPhysicsUpdate = { events += "physics:$it" },
            onResize = { _, _ -> },
            onExit = {},
            isPaused = { paused },
            physicsStep = 0.1f
        )

        loop.enter()
        loop.update(0.3f)
        paused = false
        loop.update(0.1f)

        assertEquals(listOf("update:0.0", "physics:0.1", "update:0.1"), events)
    }

    @Test
    fun `updates require an entered active loop`() {
        val loop = EngineLoop({}, {}, {}, { _, _ -> }, {})

        assertFailsWith<IllegalStateException> { loop.update(1f / 60f) }
        loop.enter()
        loop.exit()
        assertFailsWith<IllegalStateException> { loop.update(1f / 60f) }
    }
}
