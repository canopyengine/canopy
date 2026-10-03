package io.canopy.engine.core.nodes

import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.core.nodes.types.empty.EmptyNode2D
import io.canopy.engine.math.Vector2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll

class NodeTests {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            // Tests share the same JVM; ensure a clean manager baseline.
            ManagersRegistry.withScope {
                register(SceneManager())
            }
        }
    }

    @Test
    fun `structure should pass`() {
        // Verifies DSL-built hierarchy and parent pointers.
        val scene = EmptyNode("root") {
            EmptyNode("child-a")

            EmptyNode("child-b") {
                EmptyNode("child-c")
            }
        }

        scene.buildTree()

        assertEquals(2, scene.children.size)
        assertSame(scene, scene.getNode<EmptyNode>("child-b").parent)
        assertSame(
            scene.getNode<EmptyNode>("child-b"),
            scene.getNode<EmptyNode>("child-b/child-c").parent
        )
    }

    @Test
    fun `behavior should work`() {
        // Verifies behavior factory attachment and that behavior can access parent/name.
        val childCount: MutableMap<String, Int> = mutableMapOf()

        val lambdaBehavior =
            createBehavior<EmptyNode>(
                onReady = {
                    val parent = parent ?: return@createBehavior
                    childCount.merge(parent.name, 1) { old, new -> old + new }
                    if (name !in childCount) childCount[name] = 0
                }
            )

        EmptyNode("Test 2") {
            EmptyNode("child-a") {
                attachBehavior(lambdaBehavior)
            }

            EmptyNode("child-b") {
                attachBehavior(lambdaBehavior)

                EmptyNode("child-c") {
                    attachBehavior(lambdaBehavior)
                }
            }
        }.buildTree()

        assertEquals(
            mapOf(
                "Test 2" to 2,
                "child-a" to 0,
                "child-b" to 1,
                "child-c" to 0
            ),
            childCount
        )
    }

    @Test
    fun `ready should execute on correct order`() {
        // Verifies ready order for the current lifecycle implementation:
        // children first, then parent, with depth-first traversal.
        val callOrder = mutableListOf<String>()

        val behaviour =
            createBehavior<EmptyNode>(
                onReady = { callOrder += name }
            )

        EmptyNode("Test 2") {
            attachBehavior(behaviour)

            EmptyNode("child-a") {
                attachBehavior(behaviour)
            }

            EmptyNode("child-b") {
                attachBehavior(behaviour)

                EmptyNode("child-c") {
                    attachBehavior(behaviour)
                }
            }
        }.buildTree()

        assertEquals(
            listOf(
                "child-a",
                "child-c",
                "child-b",
                "Test 2"
            ),
            callOrder
        )
    }

    @Test
    fun `ticks should update state`() = runBlocking {
        // Verifies that nodeUpdate and nodePhysicsUpdate trigger behavior callbacks.
        var nTicks = 0
        var nPhysicsTicks = 0

        val behavior =
            createBehavior<EmptyNode>(
                onUpdate = { nTicks++ },
                onPhysicsUpdate = { nPhysicsTicks++ }
            )

        val tree = EmptyNode("root") {
            attachBehavior(behavior)
        }
        tree.buildTree()

        launch {
            repeat(2) { i ->
                // Simulate "physics tick occasionally"
                if (nTicks % (i + 1) == 0) {
                    tree.nodePhysicsUpdate(0f)
                }

                tree.nodeUpdate(0f)
                delay(20.toDuration(DurationUnit.MILLISECONDS))
            }
        }.join()

        assertEquals(2, nTicks)
        assertEquals(1, nPhysicsTicks)
    }

    @Test
    fun `adding should call ready on child node`() {
        // Verifies runtime addChild triggers lifecycle for non-prefab children.
        var wasCalled = false

        val behavior =
            createBehavior<EmptyNode>(
                onReady = { wasCalled = true }
            )

        val root = EmptyNode("root")
        root.buildTree()

        assertFalse(wasCalled)

        root += EmptyNode("child") {
            attachBehavior(behavior)
        }

        assertTrue(wasCalled)
    }

    @Test
    fun `removing node should call onExitTree`() {
        // Verifies runtime removal triggers exitTree lifecycle.
        var wasCalled = false

        val behavior =
            createBehavior<EmptyNode>(
                onExitTree = { wasCalled = true }
            )

        val tree =
            EmptyNode("root") {
                EmptyNode("child") {
                    attachBehavior(behavior)
                }
            }.asSceneRoot()

        tree.buildTree()

        assertFalse(wasCalled)
        tree.removeChild("child")
        assertTrue(wasCalled)
    }

    @Test
    fun `queue free should delete node`() {
        // Verifies queueFree removes a node from its parent.
        val tree =
            EmptyNode("root") {
                EmptyNode("child")
            }

        tree.buildTree()

        val child = tree.getNode<EmptyNode>("child")
        assertNotNull(child)

        child.queueFree()

        assertEquals(1, tree.children.size)
        io.canopy.engine.core.managers.manager<SceneManager>().onUpdate(0f)
        assertEquals(0, tree.children.size)
    }

    @Test
    fun `custom scene should work`() {
        // Verifies create() can define internal structure.
        class CustomScene(name: String = "custom", block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block = block) {
            override fun nodeInit() {
                EmptyNode("empty")
            }
        }

        val customScene =
            CustomScene {
                EmptyNode("child")
            }

        customScene.buildTree()

        assertEquals(2, customScene.children.size)
    }

    @Test
    fun `patching internal node should work`() {
        // Verifies patch() can locate and mutate internally created nodes by path.
        class CustomScene(name: String = "custom", block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block = block) {
            override fun nodeInit() {
                EmptyNode2D("empty")
            }
        }

        val node = CustomScene {
            patch<EmptyNode2D>("./empty") {
                name = "patched"
                position = Vector2(100f, 100f)
            }
        }
        node.buildTree()

        val child = node.getNode<EmptyNode2D>("./patched")

        assertEquals("patched", child.name)
        assertEquals(Vector2(100f, 100f), child.position)
    }

    @Test
    fun `2D nodes have independent positions and global transforms are defensive copies`() {
        val first = EmptyNode2D("first")
        val second = EmptyNode2D("second")
        first.position = Vector2(3f, 4f)

        assertEquals(Vector2(), second.position)
        assertTrue(first.position !== second.position)

        val rootPosition = first.globalPosition
        assertEquals(Vector2(3f, 4f), first.globalPosition)

        first.scale = Vector2(2f, 3f)
        val rootScale = first.globalScale
        assertEquals(Vector2(2f, 3f), first.globalScale)

        val child = EmptyNode2D("child")
        child.position = Vector2(5f, 6f)
        child.scale = Vector2(4f, 5f)
        first.addChild(child)

        val childGlobalPosition = child.globalPosition
        assertEquals(Vector2(8f, 10f), childGlobalPosition)
        assertEquals(Vector2(8f, 10f), child.globalPosition)
        assertEquals(Vector2(5f, 6f), child.position)

        val childGlobalScale = child.globalScale
        assertEquals(Vector2(8f, 15f), childGlobalScale)
        assertEquals(Vector2(8f, 15f), child.globalScale)
        assertEquals(Vector2(4f, 5f), child.scale)
    }

    @Test
    fun `custom node class with internal script should work`() {
        // Verifies a node can attach behavior from within create().
        var wasCalled = false

        class CustomScene(name: String, block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block = block) {
            override fun nodeInit() {
                behavior(onReady = { wasCalled = true })
            }
        }

        val root = CustomScene("root").asSceneRoot()
        root.buildTree()

        assertTrue(wasCalled)
    }

    @Test
    fun `children can be added during update`() {
        val root = EmptyNode("root")
        val child = EmptyNode("child")
        child.behavior = createBehavior<EmptyNode>(
            onUpdate = { root.addChild(EmptyNode("added")) }
        )(child)
        root.addChild(child)
        root.buildTree()

        root.nodeUpdate(0.016f)
        assertNotNull(root.children["added"])
    }

    @Test
    fun `replacing behavior exits old and enters new behavior`() {
        val calls = mutableListOf<String>()
        val node = EmptyNode("root")
        node.behavior = createBehavior<EmptyNode>(
            onExitTree = { calls += "old-exit" }
        )(node)
        node.buildTree()

        node.behavior = createBehavior<EmptyNode>(
            onEnterTree = { calls += "new-enter" }
        )(node)

        assertEquals(listOf("old-exit", "new-enter"), calls)
    }

    @Test
    fun `tree system helper creates a registerable system`() {
        var ticks = 0
        val system = createTreeSystem(
            TreeSystem.UpdatePhase.FramePre,
            beforeProcess = { ticks++ }
        )
        val scenes = io.canopy.engine.core.managers.manager<SceneManager>()

        scenes.addSystem(system)
        scenes.currScene = EmptyNode("system-root")
        scenes.onUpdate(0f)

        assertEquals(1, ticks)
        scenes.currScene = null
        scenes.removeSystem(system::class)
    }

    @Test
    fun `tree system snapshots matching nodes while processing`() {
        val first = EmptyNode("first")
        val removed = EmptyNode("removed")
        val added = EmptyNode("added")
        val processed = mutableListOf<String>()

        class SnapshotSystem : TreeSystem(UpdatePhase.FramePre, 0, EmptyNode::class) {
            var changedNodes = false

            override fun processNode(node: Node<*>, delta: Float) {
                processed += node.name
                if (!changedNodes && node === first) {
                    changedNodes = true
                    unregister(removed)
                    register(added)
                }
            }
        }

        val system = SnapshotSystem()
        system.register(first)
        system.register(removed)

        system.tick(0f)
        assertEquals(listOf("first", "removed"), processed)

        processed.clear()
        system.tick(0f)
        assertEquals(listOf("first", "added"), processed)
    }

    @Test
    fun `scene manager snapshots systems for frame and physics phases`() {
        val scenes = io.canopy.engine.core.managers.manager<SceneManager>()

        fun verifyPhase(phase: TreeSystem.UpdatePhase, tick: () -> Unit) {
            val calls = mutableListOf<String>()
            var changedSystems = false

            class RemovedSystem : TreeSystem(phase) {
                override fun beforeProcess(delta: Float) {
                    calls += "removed"
                }
            }

            class AddedSystem : TreeSystem(phase) {
                override fun beforeProcess(delta: Float) {
                    calls += "added"
                }
            }

            class DriverSystem : TreeSystem(phase) {
                override fun beforeProcess(delta: Float) {
                    calls += "driver"
                    if (!changedSystems) {
                        changedSystems = true
                        scenes.removeSystem(RemovedSystem::class)
                        scenes.addSystem(AddedSystem())
                    }
                }
            }

            val driver = DriverSystem()
            val removed = RemovedSystem()
            val added = AddedSystem()
            scenes.addSystem(driver)
            scenes.addSystem(removed)
            scenes.currScene = EmptyNode("system-snapshot-root")

            tick()
            assertEquals(listOf("driver", "removed"), calls)

            calls.clear()
            tick()
            assertEquals(listOf("driver", "added"), calls)

            scenes.currScene = null
            scenes.removeSystem(DriverSystem::class)
            scenes.removeSystem(AddedSystem::class)
        }

        verifyPhase(TreeSystem.UpdatePhase.FramePre) { scenes.onUpdate(0f) }
        verifyPhase(TreeSystem.UpdatePhase.PhysicsPre) { scenes.onPhysicsUpdate(1f / 60f) }
    }

    @Test
    fun `scene manager dispatches systems around node updates in phase order`() {
        val scenes = io.canopy.engine.core.managers.manager<SceneManager>()
        val calls = mutableListOf<String>()

        class FramePreSystem : TreeSystem(TreeSystem.UpdatePhase.FramePre) {
            override fun beforeProcess(delta: Float) {
                calls += "frame-pre"
            }
        }

        class FramePostSystem : TreeSystem(TreeSystem.UpdatePhase.FramePost) {
            override fun beforeProcess(delta: Float) {
                calls += "frame-post"
            }
        }

        class PhysicsPreSystem : TreeSystem(TreeSystem.UpdatePhase.PhysicsPre) {
            override fun beforeProcess(delta: Float) {
                calls += "physics-pre"
            }
        }

        class PhysicsPostSystem : TreeSystem(TreeSystem.UpdatePhase.PhysicsPost) {
            override fun beforeProcess(delta: Float) {
                calls += "physics-post"
            }
        }

        scenes.addSystem(FramePostSystem())
        scenes.addSystem(FramePreSystem())
        scenes.addSystem(PhysicsPostSystem())
        scenes.addSystem(PhysicsPreSystem())
        scenes.currScene = EmptyNode("phase-root") {
            behavior(
                onUpdate = { calls += "node-frame" },
                onPhysicsUpdate = { calls += "node-physics" }
            )
        }

        scenes.onUpdate(0f)
        assertEquals(listOf("frame-pre", "node-frame", "frame-post"), calls)

        calls.clear()
        scenes.onPhysicsUpdate(1f / 60f)
        assertEquals(listOf("physics-pre", "node-physics", "physics-post"), calls)

        scenes.currScene = null
        scenes.removeSystem(FramePreSystem::class)
        scenes.removeSystem(FramePostSystem::class)
        scenes.removeSystem(PhysicsPreSystem::class)
        scenes.removeSystem(PhysicsPostSystem::class)
    }

    @Test
    fun `physics system processes each explicit fixed step`() {
        class PhysicsCounter : TreeSystem(UpdatePhase.PhysicsPre) {
            var steps = 0
            override fun beforeProcess(delta: Float) {
                steps++
            }
        }

        val scenes = io.canopy.engine.core.managers.manager<SceneManager>()
        val system = PhysicsCounter()
        scenes.addSystem(system)
        scenes.currScene = EmptyNode("physics-root")

        repeat(3) { scenes.onPhysicsUpdate(1f / 60f) }

        assertEquals(3, system.steps)
        scenes.currScene = null
        scenes.removeSystem(PhysicsCounter::class)
    }

    @Test
    fun `scene replacement exits behaviors and clears old groups`() {
        val scenes = io.canopy.engine.core.managers.manager<SceneManager>()
        var exited = false
        val oldScene = EmptyNode("old") {
            EmptyNode("member") {
                addGroup("old-members")
                behavior(onExitTree = { exited = true })
            }
        }

        scenes.currScene = oldScene
        var groupMemberReceivedSignal = false
        scenes.signalGroup("old-members") { groupMemberReceivedSignal = true }
        assertTrue(groupMemberReceivedSignal)

        scenes.currScene = EmptyNode("new")

        assertTrue(exited)
        assertFailsWith<IllegalStateException> {
            scenes.signalGroup("old-members") {}
        }
        scenes.currScene = null
    }
}
