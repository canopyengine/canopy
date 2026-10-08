package io.canopy.engine.core.managers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.canopy.engine.core.exceptions.NodeCallbackException
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
    private val independentRoots = mutableListOf<Node<*>>()

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
            val name = if (node.isValid) node.name else node.exitMetadata.name
            calls += "$label:remove:$name"
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
        independentRoots.filter { it.isValid }.forEach { it.queueFree() }
        scenes.onUpdate(0f)
        independentRoots.clear()
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    private fun scene(): EmptyNode = EmptyNode("root") {
        EmptyNode("child")
        EmptyNode2D("unmatched")
    }.also { scenes.currScene = it }

    @Test
    fun `backfill keeps distinct identities with identical paths after one root is destroyed`() {
        // Arrange
        val first = EmptyNode("root") { EmptyNode("child") }.also {
            it.buildTree()
            independentRoots += it
        }
        val second = EmptyNode("root") { EmptyNode("child") }.also {
            it.buildTree()
            independentRoots += it
        }
        val firstChild = first.children.values.single()
        val secondChild = second.children.values.single()
        val system = RecordingSystem(calls)

        // Act
        scenes.addSystem(system)

        // Assert
        assertEquals(listOf(first, firstChild, second, secondChild), system.nodes)
        assertEquals(4, scenes.indexedNodeCount)
        first.queueFree()
        scenes.onUpdate(0f)
        assertEquals(listOf(second, secondChild), system.nodes)
        assertEquals(2, scenes.indexedNodeCount)
        scenes.removeSystem(RecordingSystem::class)
        scenes.addSystem(system)
        assertEquals(listOf(second, secondChild), system.nodes)
    }

    @Test
    fun `rename and same tree reparent preserve registration order during late backfill`() {
        // Arrange
        val root = scene()
        val child = root.children.values.first()
        val destination = EmptyNode("destination").also { root.addChild(it) }

        // Act
        child.name = "renamed"
        root.reparent(child, destination)
        root.name = "renamed-root"
        val system = RecordingSystem(calls)
        scenes.addSystem(system)

        // Assert
        assertEquals(listOf(root, child, destination), system.nodes)
        assertEquals("/renamed-root/destination/renamed", child.path)
        assertEquals(4, scenes.indexedNodeCount)
    }

    @Test
    fun `backfill skips destroyed pending nodes and continues with surviving identities`() {
        // Arrange
        val root = EmptyNode("root") {
            EmptyNode("doomed")
            EmptyNode("survivor")
        }.also {
            it.buildTree()
            independentRoots += it
        }
        val doomed = root.children.values.first()
        val survivor = root.children.values.last()
        val system = RecordingSystem(calls).also {
            it.added = { node ->
                if (node === root) {
                    doomed.queueFree()
                    scenes.onUpdate(0f)
                }
            }
        }

        // Act
        scenes.addSystem(system)

        // Assert
        assertFalse(doomed.isValid)
        assertEquals(listOf(root, survivor), system.nodes)
        assertEquals(2, scenes.indexedNodeCount)
    }

    @Test
    fun `reattachment during backfill appends membership without duplicate system matches`() {
        // Arrange
        val root = EmptyNode("root") {
            EmptyNode("moved")
            EmptyNode("sibling")
        }.also {
            it.buildTree()
            independentRoots += it
        }
        val moved = root.children.getValue("moved")
        val sibling = root.children.getValue("sibling")
        val system = RecordingSystem(calls).also {
            it.added = { node ->
                if (node === root) {
                    root.removeChild(moved)
                    root.addChild(moved)
                }
            }
        }

        // Act
        scenes.addSystem(system)
        val later = OtherSystem(calls)
        scenes.addSystem(later)

        // Assert
        assertEquals(listOf(root, moved, sibling), system.nodes)
        assertEquals(listOf(root, sibling, moved), later.nodes)
        assertEquals(3, scenes.indexedNodeCount)
    }

    @Test
    fun `late backfill excludes exited retained nodes until they reenter`() {
        // Arrange
        val root = EmptyNode("root").also {
            it.buildTree()
            independentRoots += it
        }
        root.nodeExitTree()
        val system = RecordingSystem(calls)

        // Act
        scenes.addSystem(system)

        // Assert
        assertEquals(emptyList(), system.nodes)
        assertEquals(1, scenes.indexedNodeCount)
        root.buildTree()
        assertEquals(listOf(root), system.nodes)
    }

    @Test
    fun `subtree registration skips destroyed pending children and continues to survivors`() {
        // Arrange
        val root = EmptyNode("root") {
            EmptyNode("doomed")
            EmptyNode("survivor")
        }.also { scenes.currScene = it }
        val doomed = root.children.getValue("doomed")
        val survivor = root.children.getValue("survivor")
        scenes.unregisterSubtree(root)
        val system = RecordingSystem(calls).also {
            it.registered = { scenes.registerSubtree(root) }
            it.added = { node ->
                if (node === root) {
                    doomed.queueFree()
                    scenes.onUpdate(0f)
                }
            }
        }

        // Act
        scenes.addSystem(system)

        // Assert
        assertFalse(doomed.isValid)
        assertEquals(listOf(root, survivor), system.nodes)
        assertEquals(2, scenes.indexedNodeCount)
        assertEquals(2, scenes.retainedStateCount)
        assertEquals(
            listOf("system:register", "system:add:root", "system:process:root", "system:add:survivor"),
            calls
        )
    }

    @Test
    fun `subtree registration skips later systems after an earlier hook destroys the current node`() {
        // Arrange
        val root = EmptyNode("root") {
            EmptyNode("doomed")
            EmptyNode("survivor")
        }.also { scenes.currScene = it }
        val doomed = root.children.getValue("doomed")
        val survivor = root.children.getValue("survivor")
        val first = RecordingSystem(calls)
        val later = OtherSystem(calls)
        scenes.addSystem(first)
        scenes.addSystem(later)
        scenes.unregisterSubtree(root)
        first.added = { node ->
            if (node === doomed) {
                doomed.queueFree()
                scenes.onUpdate(0f)
            }
        }
        calls.clear()

        // Act
        scenes.registerSubtree(root)

        // Assert
        assertFalse(doomed.isValid)
        assertEquals(listOf(root, survivor), first.nodes)
        assertEquals(listOf(root, survivor), later.nodes)
        assertEquals(2, scenes.indexedNodeCount)
        assertEquals(2, scenes.retainedStateCount)
        assertFalse("other:add:doomed" in calls)
        assertEquals(1, calls.count { it == "system:add:survivor" })
        assertEquals(1, calls.count { it == "other:add:survivor" })
    }

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
        assertEquals(listOf("system:remove:child", "system:remove:root", "system:unregister"), calls)
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
        val failure = assertFailsWith<NodeCallbackException> { scenes.removeSystem(RecordingSystem::class) }

        // Assert
        assertSame(nodeFailures.first(), failure.cause)
        assertSame(nodeFailures.last(), failure.suppressed[0].cause)
        assertSame(unregisterFailure, failure.suppressed[1])
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

    @Test
    fun `shutdown preserves nested system failures after scene cleanup fails`() {
        // Arrange
        val root = scene()
        val sceneFailure = AssertionError("scene resource")
        val systemFailure = IllegalStateException("system unregister")
        val otherFailure = java.util.concurrent.CancellationException("other unregister")
        root.onRemoval { throw sceneFailure }
        val system = RecordingSystem(calls).apply { unregistered = { throw systemFailure } }
        val other = OtherSystem(calls).apply { unregistered = { throw otherFailure } }
        scenes.addSystem(system)
        scenes.addSystem(other)
        calls.clear()

        // Act
        val failure = assertFailsWith<AssertionError> { scenes.onExit() }
        scenes.onExit()

        // Assert
        assertSame(sceneFailure, failure)
        assertEquals(listOf(systemFailure), failure.suppressed.toList())
        assertEquals(listOf(otherFailure), systemFailure.suppressed.toList())
        assertTrue(system.nodes.isEmpty())
        assertTrue(other.nodes.isEmpty())
        assertEquals(1, calls.count { it == "system:unregister" })
        assertEquals(1, calls.count { it == "other:unregister" })
    }
}
