package io.canopy.engine.core.nodes

import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.types.empty.TestNode
import io.canopy.engine.core.nodes.types.empty.TestNode2D
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
        val scene = TestNode("root") {
            TestNode("child-a")

            TestNode("child-b") {
                TestNode("child-c")
            }
        }

        scene.buildTree()

        assertEquals(2, scene.children.size)
        assertSame(scene, scene.getNode<TestNode>("child-b").parent)
        assertSame(
            scene.getNode<TestNode>("child-b"),
            scene.getNode<TestNode>("child-b/child-c").parent
        )
    }

    @Test
    fun `behavior should work`() {
        // Verifies behavior factory attachment and that behavior can access parent/name.
        val childCount: MutableMap<String, Int> = mutableMapOf()

        val lambdaBehavior =
            createBehavior<TestNode>(
                onReady = {
                    val parent = parent ?: return@createBehavior
                    childCount.merge(parent.name, 1) { old, new -> old + new }
                    if (name !in childCount) childCount[name] = 0
                }
            )

        TestNode("Test 2") {
            TestNode("child-a") {
                attachBehavior(lambdaBehavior)
            }

            TestNode("child-b") {
                attachBehavior(lambdaBehavior)

                TestNode("child-c") {
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
            createBehavior<TestNode>(
                onReady = { callOrder += name }
            )

        TestNode("Test 2") {
            attachBehavior(behaviour)

            TestNode("child-a") {
                attachBehavior(behaviour)
            }

            TestNode("child-b") {
                attachBehavior(behaviour)

                TestNode("child-c") {
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
            createBehavior<TestNode>(
                onUpdate = { nTicks++ },
                onPhysicsUpdate = { nPhysicsTicks++ }
            )

        val tree = TestNode("root") {
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
            createBehavior<TestNode>(
                onReady = { wasCalled = true }
            )

        val root = TestNode("root")
        root.buildTree()

        assertFalse(wasCalled)

        root += TestNode("child") {
            attachBehavior(behavior)
        }

        assertTrue(wasCalled)
    }

    @Test
    fun `removing node should call onExitTree`() {
        // Verifies runtime removal triggers exitTree lifecycle.
        var wasCalled = false

        val behavior =
            createBehavior<TestNode>(
                onExitTree = { wasCalled = true }
            )

        val tree =
            TestNode("root") {
                TestNode("child") {
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
            TestNode("root") {
                TestNode("child")
            }

        tree.buildTree()

        val child = tree.getNode<TestNode>("child")
        assertNotNull(child)

        child.queueFree()

        assertEquals(0, tree.children.size)
    }

    @Test
    fun `custom scene should work`() {
        // Verifies create() can define internal structure.
        class CustomScene(name: String = "custom", block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block = block) {
            override fun create() {
                TestNode("empty")
            }
        }

        val customScene =
            CustomScene {
                TestNode("child")
            }

        customScene.buildTree()

        assertEquals(2, customScene.children.size)
    }

    @Test
    fun `patching internal node should work`() {
        // Verifies patch() can locate and mutate internally created nodes by path.
        class CustomScene(name: String = "custom", block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block = block) {
            override fun create() {
                TestNode2D("empty")
            }
        }

        val node = CustomScene {
            patch<TestNode2D>("./empty") {
                name = "patched"
                position = Vector2(100f, 100f)
            }
        }
        node.buildTree()

        val child = node.getNode<TestNode2D>("./patched")

        assertEquals("patched", child.name)
        assertEquals(Vector2(100f, 100f), child.position)
    }

    @Test
    fun `custom node class with internal script should work`() {
        // Verifies a node can attach behavior from within create().
        var wasCalled = false

        class CustomScene(name: String, block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block = block) {
            override fun create() {
                behavior(onReady = { wasCalled = true })
            }
        }

        val root = CustomScene("root").asSceneRoot()
        root.buildTree()

        assertTrue(wasCalled)
    }

    @Test
    fun `rename reparent and relative lookups should update paths`() {
        val root = TestNode("root") {
            TestNode("a")
            TestNode("b")
        }
        root.buildTree()

        val a = root.getNode<TestNode>("a")
        a.name = "renamed"
        assertEquals("/root/renamed", a.path)
        assertSame(a, root.getNode<TestNode>("renamed"))

        root.reparent(a, root.getNode<TestNode>("b"))

        assertEquals("/root/b/renamed", a.path)
        assertSame(a, root.getNode<TestNode>("b/renamed"))
        assertSame(root.getNode<TestNode>("b"), a.getNode<TestNode>(".."))
    }

    @Test
    fun `prefab child skips lifecycle until built manually`() {
        var readyCalls = 0
        val root = TestNode("root")
        root.buildTree()

        val prefab = TestNode("child") {
            behavior(onReady = { readyCalls++ })
        }.asPrefab()

        root.addChild(prefab)
        assertEquals(0, readyCalls)

        prefab.buildTree()
        assertEquals(1, readyCalls)
    }

    @Test
    fun `group changes on built node should mirror through scene manager`() {
        val root = TestNode("root")
        root.asSceneRoot()
        root.buildTree()

        root.addGroup("one")
        val sceneManager = manager<SceneManager>()
        val firstNames = mutableSetOf<String>()
        sceneManager.signalGroup("one") { firstNames += it.name }
        assertEquals(setOf("root"), firstNames)

        root.removeGroup("one")
        val names = mutableSetOf<String>()
        sceneManager.signalGroup("one") { names += it.name }
        assertTrue(names.isEmpty())
    }

    @Test
    fun `getNode allows complex resolution with relative and wrapper paths`() {
        val root = TestNode("root") {
            TestNode("visibleChild") {
                // Not visible/skipOnSearch = true (using empty node for now but just assume we test relative resolution)
                TestNode("subChild")
            }
        }.asSceneRoot()
        root.buildTree()

        val child = root.getNode<TestNode>("./visibleChild/subChild")
        assertEquals("subChild", child.name)

        val sibling = child.getNode<TestNode>("..")
        assertEquals("visibleChild", sibling.name)

        val backToRoot = sibling.getNode<TestNode>("..")
        assertEquals("root", backToRoot.name)
    }

    @Test
    fun `unary plus and unary minus operators work`() {
        val child1 = TestNode("child1").asPrefab()
        val child2 = TestNode("child2").asPrefab()

        val root = TestNode("root") {
            +child1
            +child2
        }
        root.buildTree()
        assertEquals(2, root.children.size)

        with(root) {
            -child1
        }
        assertEquals(1, root.children.size)
    }

    @Test
    fun `plusAssign and minusAssign operators work`() {
        val root = TestNode("root")
        val child = TestNode("child")

        root += child
        assertEquals(1, root.children.size)

        root -= child
        assertEquals(0, root.children.size)
    }

    @Test
    fun `plus operator returns parent`() {
        val root = TestNode("root")
        val child = TestNode("child")

        with(root) {
            val returned = this + child
            assertSame(root, returned)
            assertEquals(1, root.children.size)
        }
    }

    @Test
    fun `hasChildType works correctly`() {
        val root = TestNode("root") {
            TestNode2D("a2d")
        }
        root.buildTree()

        assertTrue(root.hasChildType(TestNode2D::class))
        assertFalse(root.hasChildType(TestNode::class)) // children does not include TestNode
    }

    @Test
    fun `runBehavior catches and logs exceptions`() {
        val behavior = createBehavior<TestNode>(
            onReady = { throw RuntimeException("Throwing behavior") }
        )

        val root = TestNode("root") {
            attachBehavior(behavior)
        }

        assertFailsWith<RuntimeException> {
            root.buildTree()
        }
    }

    @Test
    fun `getNode supports complex path resolution`() {
        val target = TestNode("target")
        val child = TestNode("child") { +target }
        val wrapper = TestNode("wrapper") { +child }
        val root = TestNode("root") {
            +wrapper
            TestNode("sibling")
        }
        root.buildTree()

        // From target's perspective
        assertEquals(target, target.getNode<TestNode>("."))
        assertEquals(target, target.getNode<TestNode>(""))
        assertEquals(child, target.getNode<TestNode>(".."))
        // Resolving parent, skipping wrapper
        assertEquals(wrapper, target.getNode<TestNode>("../.."))
        // Resolving up and down
        assertEquals<TestNode>(root.getNode("sibling"), target.getNode("../../../sibling"))

        // From root's perspective
        assertEquals<TestNode>(target, root.getNode("wrapper/child/target"))
        assertEquals<TestNode>(target, wrapper.getNode("child/target")) // Because wrapper is skipped

        // Invalid paths
        assertNull(root.getNodeOrNull<TestNode>("missing"))
        assertNull(root.getNodeOrNull<TestNode>("../missing"))

        // Root path fallback
        assertEquals<TestNode>(root, root.getNode("/"))
    }
}
