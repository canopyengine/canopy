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
    fun `paused updates retain real time and reset physics remainder on transitions`() {
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
        loop.update(0.15f)
        paused = false
        loop.update(0.05f)
        loop.update(0.05f)

        assertEquals(
            listOf("physics:0.1", "update:0.15", "update:0.05", "physics:0.1", "update:0.05"),
            events
        )
    }

    @Test
    fun `slow frame caps physics steps per update`() {
        var physicsSteps = 0
        val loop = EngineLoop(
            onEnter = {},
            onUpdate = {},
            onPhysicsUpdate = { physicsSteps++ },
            onResize = { _, _ -> },
            onExit = {},
            physicsStep = 0.1f
        )

        loop.enter()
        loop.update(1f)

        assertEquals(5, physicsSteps)
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
