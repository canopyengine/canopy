package io.canopy.engine.app

import kotlin.test.*
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node

class AppScreenConfigurationTests {
    private class TestApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
    }
    class First(block: First.() -> Unit = {}) : Node<First>("First", block = block)
    class Second : Node<Second>("Second")

    @Test
    fun `screen loading uses the configured SceneManager after platform services are registered`() {
        val configuredScenes = io.canopy.engine.core.managers.SceneManager(1f / 30f)
        var configured = false
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
            override fun io.canopy.engine.core.managers.SceneManager.configureSceneManager() {
                getManager<io.canopy.engine.input.InputFocus>()
                configured = true
            }
        }
        app.managers {
            -io.canopy.engine.core.managers.SceneManager::class
            +configuredScenes
        }
        app.initialScreen<First>()
        app.enter()
        try {
            assertTrue(configured)
            assertSame(configuredScenes, getManager<io.canopy.engine.core.managers.SceneManager>())
            assertSame(configuredScenes.currScene, getManager<ScreenManager>().current)
            assertEquals(1f / 30f, app.engineLoop.physicsStep)
        } finally {
            app.exit()
        }
    }

    @Test
    fun `initial factories are lazy app owned and never leak into successive apps`() {
        val visited = mutableListOf<String>()
        val first = TestApp().apply { initialScreen<First> { First { visited += "first" } } }
        val second = TestApp().apply { initialScreen<First> { First { visited += "second" } } }
        assertEquals(emptyList(), visited)
        first.enter()
        val root = getManager<ScreenManager>().current!!
        first.exit()
        assertTrue(root.isFreed)
        second.enter()
        try {
            assertEquals(listOf("first", "second"), visited)
            assertNotSame(root, getManager<ScreenManager>().current)
        } finally {
            second.exit()
        }
        val empty = TestApp()
        empty.enter()
        try {
            assertNull(getManager<ScreenManager>().current)
        } finally {
            empty.exit()
        }
    }

    @Test
    fun `navigation commits after update callbacks and before presentation`() {
        val order = mutableListOf<String>()
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
            override fun presentFrame() {
                assertIs<Second>(getManager<ScreenManager>().current)
                order += "present"
            }
        }
        app.initialScreen<First>()
        app.enter()
        val root = getManager<ScreenManager>().current!!
        app.onUpdate {
            navigateTo<Second>()
            assertSame(root, getManager<ScreenManager>().current)
            order += "callback"
        }
        try {
            assertFailsWith<IllegalStateException> { app.initialScreen<Second>() }
            app.update(0f)
            assertEquals(listOf("callback", "present"), order)
        } finally {
            app.exit()
        }
    }
}
