package io.canopy.platforms.desktop.physics.systems

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.World
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.inject
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.ProcessMode
import io.canopy.engine.core.nodes.behavior
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/** Pending execution while the desktop module remains excluded from settings.gradle.kts. */
class PhysicsPauseTests {
    private lateinit var app: App<AppConfig>
    private lateinit var system: PhysicsSystem
    private lateinit var world: World
    private var menuTicks = 0
    private var controllerTicks = 0

    @BeforeEach
    fun setup() {
        ManagersRegistry.exit()
        Box2D.init()
        system = PhysicsSystem(Vector2.Zero)
        app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
            override fun SceneManager.configureSceneManager() {
                addSystem(system)
            }
        }
        app.onEnter {
            manager<SceneManager>().currScene = EmptyNode("root") {
                EmptyNode("menu") {
                    processMode = ProcessMode.WhenPaused
                    behavior(onPhysicsUpdate = { menuTicks++ })
                }
                EmptyNode("controller") {
                    processMode = ProcessMode.Always
                    behavior(onPhysicsUpdate = { controllerTicks++ })
                }
            }
        }
        app.enter()
        world = inject()
    }

    @AfterEach
    fun cleanup() {
        try {
            app.exit()
        } finally {
            world.dispose()
            system.debugRenderer?.dispose()
            ManagersRegistry.exit()
        }
    }

    private fun movingBody(): Body = world.createBody(
        BodyDef().apply { type = BodyDef.BodyType.DynamicBody }
    ).apply { setLinearVelocity(1f, 0f) }

    @Test
    fun `pause freezes bodies while menu and always physics callbacks continue`() {
        // Arrange
        val body = movingBody()
        assertFalse(system.simulateWhilePaused)

        // Act
        app.pause()
        repeat(3) { app.update(1f / 60f) }

        // Assert
        assertEquals(0f, body.position.x)
        assertEquals(1f, body.linearVelocity.x)
        assertEquals(3, menuTicks)
        assertEquals(3, controllerTicks)
    }

    @Test
    fun `resume advances bodies without replaying paused time`() {
        // Arrange
        val body = movingBody()
        app.pause()
        repeat(10) { app.update(1f / 60f) }

        // Act
        app.resume()
        app.update(1f / 60f)

        // Assert
        assertEquals(1f / 60f, body.position.x, 0.000001f)
        assertEquals(10, menuTicks)
        assertEquals(11, controllerTicks)
    }

    @Test
    fun `explicit override advances the entire world during pause and can be disabled again`() {
        // Arrange
        val first = movingBody()
        val second = movingBody()
        app.pause()
        system.simulateWhilePaused = true

        // Act
        app.update(1f / 60f)
        system.simulateWhilePaused = false
        app.update(1f / 60f)

        // Assert
        assertEquals(1f / 60f, first.position.x, 0.000001f)
        assertEquals(1f / 60f, second.position.x, 0.000001f)
        assertEquals(2, menuTicks)
        assertEquals(2, controllerTicks)
    }
}
