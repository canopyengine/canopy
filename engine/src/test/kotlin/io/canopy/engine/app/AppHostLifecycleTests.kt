package io.canopy.engine.app

import kotlin.test.*
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent

class AppHostLifecycleTests {
    private class Input : InputManager() {
        private var down = false
        override fun pollPressed(bind: InputBind) = down
        override fun handleEvent(event: InputEvent) {
            down = event.state == InputState.JustPressed
        }
        override fun resetInputState() {
            down = false
        }
    }
    private class TestApp(val input: Input = Input()) : App<AppConfig>() {
        var preparations = 0
        var presentations = 0
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        override fun provideManagers() = listOf(input)
        override fun beginFrame(): FrameSize {
            preparations++
            return FrameSize(80, 24)
        }
        override fun presentFrame() {
            presentations++
        }
    }
    class Game : Node<Game>("Game") {
        var entries = 0
        var exits = 0
        var updates = 0
        var ticks = 0
        override fun onEnterTree() {
            entries++
        }
        override fun onExitTree() {
            exits++
        }
        override fun onUpdate(delta: Float) {
            updates++
        }
        override fun onPhysicsUpdate(delta: Float) {
            ticks++
        }
    }

    @Test
    fun `host restoration preserves manual pause and overlapping pause causes`() {
        // Arrange
        val app = TestApp()
        var leaked: LifecyclePolicy? = null
        app.lifecycle {
            pauseWhenUnfocused = true
            leaked = this
        }
        leaked!!.pauseWhenUnfocused = false // Configuration is copied, not shared with the caller.
        app.enter()
        try {
            // Act / Assert
            assertFailsWith<IllegalStateException> { app.lifecycle { pauseWhenHidden = false } }
            app.pause()
            app.engineLoop.hostStateChanged(HostState(focused = false))
            app.resume()
            assertTrue(app.isPaused)
            app.engineLoop.hostStateChanged(HostState(focused = false, minimized = true))
            app.engineLoop.hostStateChanged(HostState(minimized = true))
            assertTrue(app.isPaused)
            app.pause()
            app.engineLoop.hostStateChanged(HostState())
            assertTrue(app.isPaused)
            app.resume()
            assertFalse(app.isPaused)
        } finally {
            app.exit()
        }
    }

    @Test
    fun `hiding may continue simulation while suppressing presentation and refreshing geometry on return`() {
        // Arrange
        val app = TestApp()
        val sizes = mutableListOf<Pair<Int, Int>>()
        app.lifecycle { pauseWhenHidden = false }
        app.initialScreen<Game>()
        app.onResize { width, height -> sizes += width to height }
        app.enter()
        try {
            val root = assertIs<Game>(getManager<ScreenManager>().current)
            app.update(0f)

            // Act
            app.engineLoop.hostStateChanged(HostState(visible = false))
            app.update(0f)
            assertFalse(app.isPaused)
            assertEquals(2, root.updates)
            assertEquals(1, app.preparations)
            assertEquals(1, app.presentations)
            app.engineLoop.hostStateChanged(HostState())
            app.update(0f)

            // Assert
            assertEquals(listOf(80 to 24, 80 to 24), sizes)
            assertSame(root, getManager<ScreenManager>().current)
            assertEquals(1, root.entries)
            assertEquals(0, root.exits)
            assertEquals(2, app.presentations)
        } finally {
            app.exit()
        }
    }

    @Test
    fun `suspension stops all frames and resumes without charging background time or replacing screen`() {
        // Arrange
        val app = TestApp()
        app.initialScreen<Game>()
        val deltas = mutableListOf<Float>()
        app.onUpdate { deltas += it }
        app.enter()
        val root = assertIs<Game>(getManager<ScreenManager>().current)
        try {
            val step = app.engineLoop.physicsStep
            app.update(step / 2)

            // Act
            app.engineLoop.hostStateChanged(HostState(suspended = true))
            app.update(100f)
            app.engineLoop.physicsUpdate(step)
            assertEquals(1, root.updates)
            assertEquals(0, root.ticks)
            app.engineLoop.hostStateChanged(HostState())
            app.update(100f)
            assertEquals(0, root.ticks)
            app.update(step / 2)

            // Assert
            assertEquals(listOf(step / 2, 0f, step / 2), deltas)
            assertEquals(1, root.ticks)
            assertSame(root, getManager<ScreenManager>().current)
            assertEquals(1, root.entries)
            assertEquals(0, root.exits)
        } finally {
            app.exit()
        }
        assertTrue(root.isFreed)
    }

    @Test
    fun `input loss releases held state and discards events until focused again without pausing by default`() {
        // Arrange
        val app = TestApp()
        val input = app.input
        val press = { KeyInputEvent(Key.A, state = InputState.JustPressed) }
        app.enter()
        try {
            input.mapActions("move" to listOf(InputBind.A))
            input.enqueue(press())
            input.processEvents()
            assertTrue(input.isActionPressed("move"))
            input.enqueue(press())

            // Act
            app.engineLoop.hostStateChanged(HostState(focused = false))
            assertFalse(app.isPaused)
            assertFalse(input.isPressed(InputBind.A))
            assertEquals(emptyList(), input.eventsThisFrame)
            input.enqueue(press())
            input.enqueueBatch(listOf(press()))
            input.processEvents()
            assertFalse(input.isActionPressed("move"))
            app.engineLoop.hostStateChanged(HostState())
            input.processEvents()

            // Assert
            assertTrue(input.eventsThisFrame.isEmpty())
            assertEquals(InputState.Released, input.getActionState("move"))
            input.enqueue(press())
            input.processEvents()
            assertTrue(input.isActionJustPressed("move"))
        } finally {
            app.exit()
        }
    }
}
