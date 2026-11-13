package io.canopy.engine.app

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeCallbackException
import io.canopy.engine.core.exceptions.NodeCleanupException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.behavior
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ScreenManagerTests {
    class First(block: First.() -> Unit = {}) : Node<First>("First", block = block) {
        var count by signal(0)
    }
    class Second(block: Second.() -> Unit = {}) : Node<Second>("Second", block = block)
    class NeedsArgument(val value: Int) : Node<NeedsArgument>("Configured")
    private class Stats {
        var created = 0
        var built = 0
        var entered = 0
        var updated = 0
        var exited = 0
        var destroyed = 0
    }
    private lateinit var scenes: SceneManager
    private lateinit var screens: ScreenManager

    @BeforeEach
    fun setup() {
        ManagersRegistry.exit()
        scenes = SceneManager()
        screens = ScreenManager()
        ManagersRegistry.register(screens)
        ManagersRegistry.register(scenes)
        ManagersRegistry.enter()
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    private fun first(stats: Stats): First {
        stats.created++
        return First {
            stats.built++
            onDestroy { stats.destroyed++ }
            behavior(
                onEnterTree = { stats.entered++ },
                onExitTree = { stats.exited++ },
                onUpdate = { stats.updated++ }
            )
        }
    }

    @Test
    fun `requests are lazy coalesced and retained across visits`() {
        // Arrange
        val stats = Stats()
        screens.navigateTo(Second::class) { error("Superseded factory must not run") }
        screens.navigateTo(First::class) { first(stats) }
        assertNull(screens.current)
        assertEquals(0, stats.created)

        // Act
        screens.commit()
        val original = assertIs<First>(screens.current)
        original.count = 7
        screens.navigateTo(Second::class)
        screens.commit()
        scenes.onUpdate(0f)
        assertEquals(0, stats.updated)
        screens.navigateTo(First::class) { error("A cached instance ignores new constructor arguments") }
        screens.commit()

        // Assert
        assertSame(original, screens.current)
        assertEquals(7, original.count)
        assertEquals(1, stats.created)
        assertEquals(1, stats.built)
        assertEquals(2, stats.entered)
        assertEquals(1, stats.exited)
        screens.navigateTo(First::class)
        screens.commit()
        assertEquals(2, stats.entered)
    }

    @Test
    fun `public no argument roots construct automatically and required arguments receive guidance`() {
        screens.navigateTo(First::class)
        screens.commit()
        val original = screens.current
        screens.navigateTo(NeedsArgument::class)
        val failure = assertFailsWith<IllegalStateException> { screens.commit() }
        assertTrue(failure.message!!.contains("public no-argument constructor"))
        assertSame(original, screens.current)
        screens.navigateTo(NeedsArgument::class) { NeedsArgument(42) }
        screens.commit()
        assertEquals(42, assertIs<NeedsArgument>(screens.current).value)
        screens.reloadScreen()
        screens.commit()
        assertEquals(42, assertIs<NeedsArgument>(screens.current).value)
    }

    @Test
    fun `reload and unload destroy instances but remember factories`() {
        val stats = Stats()
        screens.navigateTo(First::class) { first(stats) }
        screens.commit()
        val original = screens.current
        assertFailsWith<IllegalStateException> { screens.unloadScreen(First::class) }
        screens.reloadScreen()
        screens.commit()
        assertTrue(original!!.isFreed)
        assertNotSame(original, screens.current)
        assertEquals(2, stats.created)
        screens.navigateTo(Second::class)
        screens.commit()
        screens.unloadScreen(First::class)
        screens.unloadScreen(First::class)
        assertEquals(2, stats.destroyed)
        screens.navigateTo(First::class)
        screens.commit()
        assertEquals(3, stats.created)
        assertEquals(0, assertIs<First>(screens.current).count)
    }

    @Test
    fun `navigation requested by entry waits for another boundary`() {
        screens.navigateTo(First::class) {
            First { behavior(onReady = { navigateTo<Second>() }) }
        }
        screens.commit()
        assertIs<First>(screens.current)
        screens.commit()
        assertIs<Second>(screens.current)
    }

    @Test
    fun `failed creation rolls back new nodes and leaves current scene selected`() {
        screens.navigateTo(First::class)
        screens.commit()
        val original = screens.current
        val states = scenes.retainedStateCount
        var destroyed = 0
        val failure = IllegalStateException("creation")
        screens.navigateTo(Second::class) {
            Second().also {
                it.onDestroy { destroyed++ }
                throw failure
            }
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { screens.commit() })
        assertSame(original, screens.current)
        assertSame(original, scenes.currScene)
        assertEquals(states, scenes.retainedStateCount)
        assertEquals(1, destroyed)
    }

    @Test
    fun `entry failure destroys partial destination and retained source remains tracked`() {
        screens.navigateTo(First::class)
        screens.commit()
        val original = screens.current
        val failure = IllegalStateException("entry")
        var destroyed = 0
        screens.navigateTo(Second::class) {
            Second {
                onDestroy { destroyed++ }
                behavior(onEnterTree = { throw failure })
            }
        }
        assertSame(failure, assertFailsWith<NodeCallbackException> { screens.commit() }.cause)
        assertNull(screens.current)
        assertNull(scenes.currScene)
        assertEquals(1, destroyed)
        screens.navigateTo(First::class)
        screens.commit()
        assertSame(original, screens.current)
    }

    @Test
    fun `reload cleanup failure also destroys its prepared replacement`() {
        var created = 0
        var destroyed = 0
        val failure = IllegalStateException("destruction")
        screens.navigateTo(First::class) {
            val first = ++created == 1
            First().also { root ->
                root.onDestroy {
                    destroyed++
                    if (first) throw failure
                }
            }
        }
        screens.commit()
        screens.reloadScreen()
        assertSame(failure, assertFailsWith<NodeCleanupException> { screens.commit() }.cause)
        assertEquals(2, destroyed)
        assertNull(screens.current)
        assertEquals(0, scenes.retainedStateCount)
    }

    @Test
    fun `root ownership is guarded and explicit destruction invalidates cached instance`() {
        screens.navigateTo(First::class)
        screens.commit()
        val original = screens.current!!
        assertFailsWith<IllegalStateException> { scenes.currScene = null }
        original.queueFree()
        scenes.onUpdate(0f)
        assertNull(screens.current)
        assertFailsWith<IllegalStateException> { screens.reloadScreen() }
        screens.navigateTo(First::class)
        screens.commit()
        assertNotSame(original, screens.current)
    }

    @Test
    fun `shutdown releases active and inactive roots even after destruction failure`() {
        val stats = Stats()
        val failure = IllegalStateException("cleanup")
        screens.navigateTo(First::class) {
            first(stats).also { root -> root.onDestroy { throw failure } }
        }
        screens.commit()
        screens.navigateTo(Second::class)
        screens.commit()
        assertSame(failure, assertFailsWith<NodeCleanupException> { screens.onExit() }.cause)
        screens.onExit()
        assertEquals(1, stats.destroyed)
        assertEquals(0, scenes.retainedStateCount)
        assertNull(scenes.currScene)
        assertFailsWith<IllegalStateException> { screens.navigateTo(First::class) }
    }
}
