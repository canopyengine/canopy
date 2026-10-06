package io.canopy.engine.core.managers

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ManagersRegistryDispatchTests {
    private class CallbackManager(var callback: () -> Unit = {}) : Manager {
        override fun onEnter() = callback()
        override fun onUpdate(delta: Float) = callback()
        override fun onPhysicsUpdate(delta: Float) = callback()
        override fun onResize(width: Int, height: Int) = callback()
    }
    private class OtherManager : Manager

    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `all lifecycle passes reject mutation and nested dispatch while allowing lookup`() {
        val manager = CallbackManager()
        ManagersRegistry.register(manager)
        manager.callback = {
            assertSame(manager, ManagersRegistry.getManager(CallbackManager::class))
            assertFailsWith<IllegalStateException> { ManagersRegistry.register(OtherManager()) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.unregister(CallbackManager::class) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.enter() }
            assertFailsWith<IllegalStateException> { ManagersRegistry.update(0f) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.physicsUpdate(0f) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.resize(1, 1) }
            assertFailsWith<IllegalStateException> { ManagersRegistry.exit() }
            assertFailsWith<IllegalStateException> { ManagersRegistry.withScope {} }
        }

        ManagersRegistry.enter()
        ManagersRegistry.update(0f)
        ManagersRegistry.physicsUpdate(0f)
        ManagersRegistry.resize(1, 1)
        ManagersRegistry.unregister(CallbackManager::class)
        ManagersRegistry.register(OtherManager())
        assertNull(ManagersRegistry.getManagerOrNull(CallbackManager::class))
        assertNotNull(ManagersRegistry.getManagerOrNull(OtherManager::class))
    }

    @Test
    fun `direct registry callback cannot stop app before manager teardown is permitted`() = runBlocking {
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        }
        var rejected = 0
        app.managers {
            +CallbackManager {
                assertFailsWith<IllegalStateException> { app.engineLoop.exit() }
                rejected++
            }
        }
        app.enter()
        ManagersRegistry.update(0f)
        assertFalse(app.handle.join(1.milliseconds))
        assertNotNull(ManagersRegistry.getManagerOrNull(CallbackManager::class))
        app.update(0f)
        assertEquals(3, rejected)
        app.exit()
        assertTrue(app.handle.join(1.seconds))
        assertNull(ManagersRegistry.getManagerOrNull(CallbackManager::class))
    }

    @Test
    fun `direct registry teardown cannot prematurely mark an active app stopped`() = runBlocking {
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        }
        var appExited = false
        app.onExit { appExited = true }
        app.managers {
            +object : Manager {
                override fun onExit() {
                    assertFailsWith<IllegalStateException> { app.engineLoop.exit() }
                }
            }
        }
        app.enter()
        ManagersRegistry.exit()
        assertFalse(appExited)
        assertFalse(app.handle.join(1.milliseconds))
        app.exit()
        assertTrue(appExited)
        assertTrue(app.handle.join(1.seconds))
    }

    @Test
    fun `failed dispatch restores mutation permission and preserves original failure`() {
        val passes: List<() -> Unit> = listOf(
            { ManagersRegistry.enter() },
            { ManagersRegistry.update(0f) },
            { ManagersRegistry.physicsUpdate(0f) },
            { ManagersRegistry.resize(1, 1) }
        )
        passes.forEach { pass ->
            val failure = IllegalArgumentException("callback")
            ManagersRegistry.register(CallbackManager { throw failure })
            assertSame(failure, assertFailsWith<IllegalArgumentException> { pass() })
            ManagersRegistry.unregister(CallbackManager::class)
            assertNull(ManagersRegistry.getManagerOrNull(CallbackManager::class))
        }
    }
}
