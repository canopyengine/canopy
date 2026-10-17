package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.ProcessMode
import io.canopy.engine.core.nodes.behavior
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.InputSystem
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.TextInputEvent
import io.canopy.tooling.utils.UnstableApi

class AppPauseTests {
    @OptIn(UnstableApi::class)
    @Test
    fun `pausing in app physics finishes the running tick before changing eligibility`() {
        // Arrange
        val calls = mutableListOf<String>()
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        }
        app.onPhysicsUpdate {
            calls += "app"
            pause()
        }
        app.onEnter {
            manager<SceneManager>().currScene = EmptyNode("root") {
                behavior(onPhysicsUpdate = { calls += "game" })
                EmptyNode("menu") {
                    processMode = ProcessMode.WhenPaused
                    behavior(onPhysicsUpdate = { calls += "menu" })
                }
            }
        }
        app.enter()
        try {
            // Act
            app.update(app.engineLoop.physicsStep * 3)

            // Assert
            assertEquals(listOf("app", "game"), calls)
            calls.clear()
            app.update(app.engineLoop.physicsStep)
            assertEquals(listOf("menu"), calls)
        } finally {
            app.exit()
            ManagersRegistry.exit()
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `resuming in a paused node does not give the current tick to gameplay siblings`() {
        // Arrange
        val calls = mutableListOf<String>()
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        }
        app.onEnter {
            manager<SceneManager>().currScene = EmptyNode("root") {
                EmptyNode("menu") {
                    processMode = ProcessMode.WhenPaused
                    behavior(onPhysicsUpdate = {
                        calls += "menu"
                        app.resume()
                    })
                }
                EmptyNode("game") {
                    behavior(onPhysicsUpdate = { calls += "game" })
                }
                EmptyNode("always") {
                    processMode = ProcessMode.Always
                    behavior(onPhysicsUpdate = { calls += "always" })
                }
            }
        }
        app.enter()
        try {
            // Act
            app.pause()
            app.update(app.engineLoop.physicsStep * 2)

            // Assert
            assertEquals(listOf("menu", "always"), calls)
            calls.clear()
            app.update(app.engineLoop.physicsStep)
            assertEquals(listOf("game", "always"), calls)
        } finally {
            app.exit()
            ManagersRegistry.exit()
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `rapid toggles advance pausable when paused and always nodes without mixing elapsed time`() {
        // Arrange
        val calls = mutableListOf<String>()
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        }
        app.onPhysicsUpdate { calls += "app" }
        app.onEnter {
            manager<SceneManager>().currScene = EmptyNode("root") {
                behavior(onPhysicsUpdate = { calls += "game" })
                EmptyNode("menu") {
                    processMode = ProcessMode.WhenPaused
                    behavior(onPhysicsUpdate = { calls += "menu" })
                }
                EmptyNode("always") {
                    processMode = ProcessMode.Always
                    behavior(onPhysicsUpdate = { calls += "always" })
                }
            }
        }
        app.enter()
        try {
            // Act
            repeat(8) { frame ->
                if (frame % 2 == 0) app.resume() else app.pause()
                app.update(app.engineLoop.physicsStep / 4)
            }

            // Assert
            assertEquals(
                mapOf("app" to 1, "game" to 1, "menu" to 1, "always" to 2),
                calls.groupingBy { it }.eachCount()
            )
        } finally {
            app.exit()
            ManagersRegistry.exit()
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `app pause preserves callback contracts and drives eligible scene nodes and input`() {
        // Arrange
        val calls = mutableListOf<String>()
        val input = object : InputManager() {
            override fun pollPressed(bind: InputBind) = false
        }
        val observer = object : Manager {
            override fun onUpdate(delta: Float) {
                calls += "manager:frame:$delta"
            }
            override fun onPhysicsUpdate(delta: Float) {
                calls += "manager:physics:$delta"
            }
        }
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
            override fun provideManagers() = listOf(input, observer)
            override fun beforeUpdate(delta: Float) {
                input.processEvents()
            }
            override fun SceneManager.configureSceneManager() {
                addSystem(InputSystem())
            }
        }
        app.onUpdate { calls += "app:frame:$it" }
        app.onPhysicsUpdate { calls += "app:physics:$it" }
        app.onEnter {
            manager<SceneManager>().currScene = EmptyNode("root") {
                behavior(onUpdate = { calls += "game:frame" }, onInput = { calls += "game:input" })
                EmptyNode("menu") {
                    processMode = ProcessMode.WhenPaused
                    behavior(
                        onUpdate = { calls += "menu:frame:$it" },
                        onPhysicsUpdate = { calls += "menu:physics:$it" },
                        onInput = { calls += "menu:input:${it.action}" }
                    )
                }
            }
        }
        app.enter()
        try {
            // Act
            app.pause()
            input.enqueue(TextInputEvent("hello"))
            app.update(1f / 60f)

            // Assert
            assertEquals(
                listOf(
                    "menu:physics:${1f / 60f}",
                    "app:frame:0.0",
                    "manager:frame:0.0",
                    "menu:input:text_input",
                    "menu:frame:${1f / 60f}"
                ),
                calls
            )
            assertEquals(0L, app.frameCount)
            calls.clear()
            app.resume()
            app.update(1f / 60f)
            assertEquals(
                listOf(
                    "app:physics:${1f / 60f}",
                    "manager:physics:${1f / 60f}",
                    "app:frame:${1f / 60f}",
                    "manager:frame:${1f / 60f}",
                    "game:frame"
                ),
                calls
            )
            assertEquals(1L, app.frameCount)
        } finally {
            manager<SceneManager>().currScene = null
            app.exit()
            ManagersRegistry.exit()
        }
    }
}
