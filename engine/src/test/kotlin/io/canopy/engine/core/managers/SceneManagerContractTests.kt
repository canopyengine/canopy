package io.canopy.engine.core.managers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.core.nodes.types.empty.EmptyNode2D
import io.canopy.engine.math.Vector2
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class SceneManagerContractTests {
    private lateinit var scenes: SceneManager
    private val calls = mutableListOf<String>()

    private open class RecordingSystem(val calls: MutableList<String>, val label: String = "system") :
        TreeSystem(UpdatePhase.FramePre, 0, EmptyNode::class) {
        val nodes: List<Node<*>> get() = matchingNodes.toList()
        var registered: () -> Unit = {}
        var removed: (Node<*>) -> Unit = {}
        var added: (Node<*>) -> Unit = {}
        var unregistered: () -> Unit = {}

        override fun onRegister() {
            calls += "$label:register"
            registered()
        }

        override fun onNodeAdded(node: Node<*>) {
            calls += "$label:add:${node.name}"
            added(node)
        }

        override fun onNodeRemoved(node: Node<*>) {
            calls += "$label:remove:${node.name}"
            removed(node)
        }

        override fun onUnregister() {
            calls += "$label:unregister"
            unregistered()
        }

        override fun processNode(node: Node<*>, delta: Float) {
            calls += "$label:process:${node.name}"
        }
    }

    private class OtherSystem(calls: MutableList<String>) : RecordingSystem(calls, "other")

    private fun install(manager: SceneManager = SceneManager(), enter: Boolean = true) {
        ManagersRegistry.exit()
        scenes = manager
        ManagersRegistry.register(scenes)
        if (enter) scenes.onEnter()
    }

    @BeforeEach
    fun setup() {
        install()
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    private fun scene(): EmptyNode = EmptyNode("root") {
        EmptyNode("child")
        EmptyNode2D("unmatched")
    }.also { scenes.currScene = it }

    @Test
    fun `resize updates the signal before notifying listeners and repeats events only`() {
        // Arrange
        val sizes = mutableListOf<Vector2>()
        val resizeSizes = mutableListOf<Vector2>()
        val signalSubscription = scenes.sceneSize.connect { sizes += it }
        val resizeSubscription = scenes.onResize.connect { _, _ -> resizeSizes += scenes.sceneSize() }
        assertEquals(Vector2.Zero, scenes.sceneSize())

        // Act
        scenes.onResize(800, 600)
        scenes.onResize(800, 600)
        scenes.onResize(0, 0)

        // Assert
        assertEquals(listOf(Vector2(800f, 600f), Vector2.Zero), sizes)
        assertEquals(listOf(Vector2(800f, 600f), Vector2(800f, 600f), Vector2.Zero), resizeSizes)
        signalSubscription.disconnect()
        resizeSubscription.disconnect()
    }

    @Test
    fun `runtime registration initializes before existing matches and includes future nodes`() {
        // Arrange
        val root = scene()
        val system = RecordingSystem(calls)

        // Act
        scenes.addSystem(system)
        root.addChild(EmptyNode("later"))

        // Assert
        assertEquals(
            listOf("system:register", "system:add:root", "system:add:child", "system:add:later"),
            calls
        )
        assertEquals(listOf("root", "child", "later"), system.nodes.map { it.name })
        calls.clear()
        scenes.onUpdate(0.1f)
        assertEquals(listOf("system:process:root", "system:process:child", "system:process:later"), calls)
    }

    @Test
    fun `initial registration defers all callbacks until manager entry`() {
        // Arrange
        install(enter = false)
        val system = RecordingSystem(calls)
        scenes.addSystem(system)
        scene()
        assertEquals(emptyList(), calls)
        assertEquals(emptyList(), system.nodes)

        // Act
        scenes.onEnter()
        scenes.onEnter()

        // Assert
        assertEquals(listOf("system:register", "system:add:root", "system:add:child"), calls)
    }

    @Test
    fun `removal releases matches before unregister and the same instance can return`() {
        // Arrange
        scene()
        val system = RecordingSystem(calls)
        scenes.addSystem(system)
        calls.clear()

        // Act
        scenes.removeSystem(RecordingSystem::class)

        // Assert
        assertEquals(listOf("system:remove:root", "system:remove:child", "system:unregister"), calls)
        assertEquals(emptyList(), system.nodes)
        assertFalse(scenes.hasSystem(RecordingSystem::class))
        calls.clear()
        scenes.addSystem(system)
        assertEquals(listOf("system:register", "system:add:root", "system:add:child"), calls)
        assertEquals(2, system.nodes.size)
    }

    @Test
    fun `removing an uninitialized system does not call lifecycle hooks`() {
        // Arrange
        install(enter = false)
        val system = RecordingSystem(calls)
        scenes.addSystem(system)
        scene()

        // Act
        scenes.removeSystem(RecordingSystem::class)
        scenes.onEnter()

        // Assert
        assertEquals(emptyList(), calls)
        assertEquals(emptyList(), system.nodes)
    }

    @Test
    fun `duplicate system class is rejected without replacing the registered instance`() {
        // Arrange
        scene()
        val system = RecordingSystem(calls)
        scenes.addSystem(system)
        calls.clear()

        // Act
        assertFailsWith<IllegalArgumentException> { scenes.addSystem(RecordingSystem(calls)) }

        // Assert
        assertSame(system, scenes.getSystem(RecordingSystem::class))
        assertEquals(emptyList(), calls)
        assertFailsWith<IllegalArgumentException> { scenes.removeSystem(OtherSystem::class) }
    }

    @Test
    fun `duplicate node registration does not duplicate matches or added hooks`() {
        // Arrange
        val root = scene()
        val system = RecordingSystem(calls)
        scenes.addSystem(system)
        calls.clear()

        // Act
        system.register(root)
        scenes.registerSubtree(root)
        system.register(EmptyNode2D("unmatched-again"))

        // Assert
        assertEquals(emptyList(), calls)
        assertEquals(2, system.nodes.size)
    }

    @Test
    fun `manager exit is idempotent and reentry rebuilds matches without rerunning configuration`() {
        // Arrange
        val system = RecordingSystem(calls)
        var configurations = 0
        install(
            SceneManager {
                configurations++
                addSystem(system)
            }
        )
        scene()
        calls.clear()

        // Act
        scenes.onExit()
        scenes.onExit()

        // Assert
        assertEquals(listOf("system:remove:root", "system:remove:child", "system:unregister"), calls)
        assertEquals(emptyList(), system.nodes)
        calls.clear()
        scenes.onEnter()
        scenes.onEnter()
        assertEquals(1, configurations)
        assertEquals(listOf("system:register", "system:add:root", "system:add:child"), calls)
    }

    @Test
    fun `registration hook may remove its system without leaving stale matches`() {
        // Arrange
        scene()
        val system = RecordingSystem(calls)
        system.registered = { scenes.removeSystem(RecordingSystem::class) }

        // Act
        scenes.addSystem(system)

        // Assert
        assertEquals(listOf("system:register", "system:unregister"), calls)
        assertEquals(emptyList(), system.nodes)
        assertFalse(scenes.hasSystem(RecordingSystem::class))
    }

    @Test
    fun `backfilling stops when an added hook removes its system`() {
        // Arrange
        scene()
        val system = RecordingSystem(calls)
        system.added = { scenes.removeSystem(RecordingSystem::class) }

        // Act
        scenes.addSystem(system)

        // Assert
        assertEquals(listOf("system:register", "system:add:root", "system:remove:root", "system:unregister"), calls)
        assertEquals(emptyList(), system.nodes)
        assertFalse(scenes.hasSystem(RecordingSystem::class))
    }

    @Test
    fun `removal attempts every node and unregister hook while preserving failures`() {
        // Arrange
        scene()
        val system = RecordingSystem(calls)
        val nodeFailures = listOf(IllegalStateException("root"), IllegalStateException("child"))
        val unregisterFailure = IllegalStateException("unregister")
        var removal = 0
        system.removed = { throw nodeFailures[removal++] }
        system.unregistered = { throw unregisterFailure }
        scenes.addSystem(system)
        calls.clear()

        // Act
        val failure = assertFailsWith<IllegalStateException> { scenes.removeSystem(RecordingSystem::class) }

        // Assert
        assertSame(nodeFailures.first(), failure)
        assertEquals(listOf(nodeFailures.last(), unregisterFailure), failure.suppressed.toList())
        assertEquals(listOf("system:remove:root", "system:remove:child", "system:unregister"), calls)
        assertEquals(emptyList(), system.nodes)
        assertFalse(scenes.hasSystem(RecordingSystem::class))
    }

    @Test
    fun `manager shutdown cleans other systems after an unregister failure`() {
        // Arrange
        scene()
        val system = RecordingSystem(calls)
        val other = OtherSystem(calls)
        val firstFailure = IllegalStateException("first")
        val otherFailure = IllegalStateException("other")
        system.unregistered = { throw firstFailure }
        other.unregistered = { throw otherFailure }
        scenes.addSystem(system)
        scenes.addSystem(other)
        calls.clear()

        // Act
        val failure = assertFailsWith<IllegalStateException> { scenes.onExit() }
        scenes.onExit()

        // Assert
        assertSame(firstFailure, failure)
        assertEquals(listOf(otherFailure), failure.suppressed.toList())
        assertEquals(emptyList(), system.nodes)
        assertEquals(emptyList(), other.nodes)
        assertTrue(calls.contains("other:unregister"))
        assertEquals(1, calls.count { it == "system:unregister" })
        assertEquals(1, calls.count { it == "other:unregister" })
    }
}
