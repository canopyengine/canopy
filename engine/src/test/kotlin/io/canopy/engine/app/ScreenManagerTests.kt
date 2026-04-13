package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScreenManagerTests {

    private open class RecordingScreen(private val calls: MutableList<String>, private val label: String) : Screen() {
        override fun setup() {
            calls += "$label.setup"
        }

        override fun onEnter() {
            super.onEnter()
            calls += "$label.enter"
        }

        override fun onFrame(delta: Float) {
            calls += "$label.frame:$delta"
        }

        override fun onResize(width: Int, height: Int) {
            calls += "$label.resize:$width:$height"
        }

        override fun onExit() {
            calls += "$label.exit"
        }

        override fun dispose() {
            calls += "$label.dispose"
        }
    }

    private class FirstScreen(calls: MutableList<String>) : RecordingScreen(calls, "first")
    private class SecondScreen(calls: MutableList<String>) : RecordingScreen(calls, "second")

    @Test
    fun `start enters screen and setup only runs once`() {
        val calls = mutableListOf<String>()
        val manager = ScreenManager()
        val first = FirstScreen(calls)
        manager.register(first)

        manager.start(FirstScreen::class)
        manager.start(FirstScreen::class)

        assertSame(first, manager.current)
        assertEquals(listOf("first.setup", "first.enter"), calls)
    }

    @Test
    fun `switching screens exits previous screen and enters next`() {
        val calls = mutableListOf<String>()
        val manager = ScreenManager()
        manager.register(FirstScreen(calls))
        manager.register(SecondScreen(calls))

        manager.start(FirstScreen::class)
        manager.start(SecondScreen::class)

        assertEquals(
            listOf("first.setup", "first.enter", "first.exit", "second.setup", "second.enter"),
            calls
        )
    }

    @Test
    fun `frame and resize are forwarded to current screen`() {
        val calls = mutableListOf<String>()
        val manager = ScreenManager()
        manager.register(FirstScreen(calls))
        manager.start(FirstScreen::class)

        manager.frame(0.25f)
        manager.resize(320, 240)

        assertTrue("first.frame:0.25" in calls)
        assertTrue("first.resize:320:240" in calls)
    }

    @Test
    fun `removing current screen exits it`() {
        val calls = mutableListOf<String>()
        val manager = ScreenManager()
        manager.register(FirstScreen(calls))
        manager.start(FirstScreen::class)

        manager.remove(FirstScreen::class)

        assertNull(manager.current)
        assertTrue("first.exit" in calls)
    }

    @Test
    fun `teardown exits current screen and disposes all registered screens`() {
        val calls = mutableListOf<String>()
        val manager = ScreenManager()
        manager.register(FirstScreen(calls))
        manager.register(SecondScreen(calls))
        manager.start(FirstScreen::class)

        manager.teardown()

        assertEquals(
            listOf("first.setup", "first.enter", "first.exit", "first.dispose", "second.dispose"),
            calls
        )
        assertNull(manager.current)
    }
}
