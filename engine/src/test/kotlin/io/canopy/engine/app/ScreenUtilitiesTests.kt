package io.canopy.engine.app

import kotlin.test.*
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node

class ScreenUtilitiesTests {
    class First : Node<First>("First")
    class Second(val value: Int) : Node<Second>("Second")
    private class TestApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
    }

    @Test
    fun `app and active node shortcuts share the class cache and reject detached callers`() {
        val app = TestApp()
        app.initialScreen<First>()
        app.enter()
        try {
            val screens = getManager<ScreenManager>()
            val first = assertIs<First>(screens.current)
            first.navigateTo(Second::class) { Second(42) }
            app.update(0f)
            assertEquals(42, assertIs<Second>(screens.current).value)
            assertFailsWith<IllegalStateException> { first.navigateTo<First>() }
            app.navigateTo<First>()
            app.update(0f)
            assertSame(first, screens.current)
            first.unloadScreen<Second>()
            first.navigateTo<Second>()
            app.update(0f)
            assertEquals(42, assertIs<Second>(screens.current).value)
            app.reloadScreen()
            app.update(0f)
            assertEquals(42, assertIs<Second>(screens.current).value)
        } finally {
            app.exit()
        }
        assertFailsWith<IllegalStateException> { app.navigateTo<First>() }
    }
}
