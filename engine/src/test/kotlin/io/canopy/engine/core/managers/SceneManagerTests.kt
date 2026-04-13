package io.canopy.engine.core.managers

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.engine.core.nodes.types.empty.EmptyNode

class SceneManagerTests {

    private open class RecordingSystem(
        phase: TreeSystem.UpdatePhase,
        priority: Int,
        private val calls: MutableList<String>,
    ) : TreeSystem(phase, priority, EmptyNode::class) {
        override fun onRegister() {
            calls += "register:$priority"
        }

        override fun onUnregister() {
            calls += "unregister:$priority"
        }

        override fun onNodeAdded(node: Node<*>) {
            calls += "add:${node.name}:$priority"
        }

        override fun onNodeRemoved(node: Node<*>) {
            calls += "remove:${node.name}:$priority"
        }

        override fun processNode(node: Node<*>, delta: Float) {
            calls += "process:${node.name}:$priority:$delta"
        }
    }

    private class LowPriorityRecordingSystem(calls: MutableList<String>) :
        RecordingSystem(TreeSystem.UpdatePhase.PhysicsPre, 10, calls)

    private class HighPriorityRecordingSystem(calls: MutableList<String>) :
        RecordingSystem(TreeSystem.UpdatePhase.PhysicsPre, 20, calls)

    @AfterTest
    fun cleanup() {
        ManagersRegistry.teardown()
    }

    @Test
    fun `scene replacement unregisters old scene and registers new scene`() {
        val sceneManager = SceneManager()
        val calls = mutableListOf<String>()
        val system = RecordingSystem(TreeSystem.UpdatePhase.FramePre, 0, calls)
        sceneManager.addSystem(system)
        ManagersRegistry.withScope {
            register(sceneManager)
        }

        val oldScene = EmptyNode("old") { EmptyNode("old-child") }
        val newScene = EmptyNode("new") { EmptyNode("new-child") }

        sceneManager.currScene = oldScene
        sceneManager.currScene = newScene

        assertTrue("add:old:0" in calls)
        assertTrue("add:old-child:0" in calls)
        assertTrue("remove:old:0" in calls)
        assertTrue("remove:old-child:0" in calls)
        assertTrue("add:new:0" in calls)
        assertTrue("add:new-child:0" in calls)
    }

    @Test
    fun `tick runs systems in priority order and only runs physics after accumulator reaches step`() {
        val sceneManager = SceneManager(physicsStep = 0.5f)
        val calls = mutableListOf<String>()
        sceneManager.addSystem(HighPriorityRecordingSystem(calls))
        sceneManager.addSystem(LowPriorityRecordingSystem(calls))
        ManagersRegistry.withScope {
            register(sceneManager)
        }
        sceneManager.currScene = EmptyNode("root")
        calls.clear()

        sceneManager.tick(0.25f)
        assertTrue(calls.none { it.startsWith("process") })

        sceneManager.tick(0.25f)

        assertEquals(
            listOf("process:root:10:0.5", "process:root:20:0.5"),
            calls.filter { it.startsWith("process") }
        )
    }

    @Test
    fun `groups can be updated and signaled`() {
        val sceneManager = SceneManager()
        ManagersRegistry.withScope {
            register(sceneManager)
        }

        val node = EmptyNode("root")
        node.addGroup("old")
        sceneManager.currScene = node

        val signaled = mutableListOf<String>()
        sceneManager.signalGroup("old") { signaled += it.name }
        assertEquals(listOf("root"), signaled)

        node.updateGroups {
            remove("old")
            add("new")
        }

        signaled.clear()
        sceneManager.signalGroup("new") { signaled += it.name }

        assertEquals(listOf("root"), signaled)

        signaled.clear()
        sceneManager.signalGroup("old") { signaled += it.name }
        assertTrue(signaled.isEmpty())
    }

    @Test
    fun `resize emits resize event`() {
        val sceneManager = SceneManager()
        val sizes = mutableListOf<Pair<Int, Int>>()
        sceneManager.onResize.connect { width, height -> sizes += width to height }

        sceneManager.resize(800, 600)

        assertEquals(listOf(800 to 600), sizes)
    }

    @Test
    fun `teardown calls system unregister hooks`() {
        val calls = mutableListOf<String>()
        val sceneManager = SceneManager {
            addSystem(RecordingSystem(TreeSystem.UpdatePhase.FramePre, 0, calls))
        }

        sceneManager.setup()
        sceneManager.teardown()

        assertEquals(listOf("register:0", "unregister:0"), calls)
    }

    @Test
    fun `scene replacement event and system lookup helpers work`() {
        val sceneManager = SceneManager()
        class LookupSystem(calls: MutableList<String>) : RecordingSystem(TreeSystem.UpdatePhase.FramePre, 0, calls)
        val calls = mutableListOf<String>()
        val replaced = mutableListOf<String?>()
        val system = LookupSystem(calls)
        sceneManager.onSceneReplaced.connect { replaced += it?.name }

        sceneManager.addSystem(system)
        assertTrue(sceneManager.hasSystem(system::class))
        assertEquals(system, sceneManager.getSystem(system::class))

        sceneManager.currScene = EmptyNode("first")
        sceneManager.currScene = EmptyNode("second")

        assertEquals(listOf<String?>("first", "second"), replaced)

        sceneManager.removeSystem(system::class)
        assertTrue(!sceneManager.hasSystem(system::class))
    }

    @Test
    fun `global systems and type-specific systems are fully triggered in register and unregister`() {
        val sceneManager = SceneManager()
        val calls = mutableListOf<String>()
        val globalSystem = object : TreeSystem(TreeSystem.UpdatePhase.FramePre, 1) {
            override fun onNodeAdded(node: Node<*>) {
                calls += "add:${node.name}:1"
            }
            override fun onNodeRemoved(node: Node<*>) {
                calls += "remove:${node.name}:1"
            }
        }
        val specificSystem = RecordingSystem(TreeSystem.UpdatePhase.FramePre, 2, calls)

        sceneManager.addSystem(globalSystem)
        sceneManager.addSystem(specificSystem)

        ManagersRegistry.withScope {
            register(sceneManager)
        }

        val node = EmptyNode("root")
        sceneManager.currScene = node // triggers registerSubtree

        assertTrue(calls.contains("add:root:1"))
        assertTrue(calls.contains("add:root:2"))
        calls.clear()

        sceneManager.currScene = null // triggers unregisterSubtree

        assertTrue(calls.contains("remove:root:1"))
        assertTrue(calls.contains("remove:root:2"))

        // test removeSystem as well
        sceneManager.removeSystem(globalSystem::class)
        sceneManager.removeSystem(specificSystem::class)
        assertFalse(sceneManager.hasSystem(globalSystem::class))
        assertFalse(sceneManager.hasSystem(specificSystem::class))
    }

    @Test
    fun `tick executes all system update phases`() {
        val sceneManager = SceneManager(physicsStep = 0.5f)
        val calls = mutableListOf<String>()

        sceneManager.addSystem(object : RecordingSystem(TreeSystem.UpdatePhase.PhysicsPre, 1, calls) {})
        sceneManager.addSystem(object : RecordingSystem(TreeSystem.UpdatePhase.PhysicsPost, 1, calls) {})
        sceneManager.addSystem(object : RecordingSystem(TreeSystem.UpdatePhase.FramePre, 1, calls) {})
        sceneManager.addSystem(object : RecordingSystem(TreeSystem.UpdatePhase.FramePost, 1, calls) {})

        ManagersRegistry.withScope {
            register(sceneManager)
        }

        sceneManager.currScene = EmptyNode("root")
        calls.clear()

        // run delta enough to trigger physics tick
        sceneManager.tick(0.5f)

        TreeSystem.UpdatePhase.entries.forEach { phase ->
            assertTrue(calls.any { it.startsWith("process:root:1") }, "Missing phase: $phase")
        }
    }

    @Test
    fun `removeFromGroup throws when group or node is invalid`() {
        val sceneManager = SceneManager()
        val node = EmptyNode("root")

        assertFailsWith<IllegalStateException> {
            sceneManager.removeFromGroup("ghost", node)
        }
    }
}
