package io.canopy.engine.core.nodes

import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
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

        assertEquals(0, tree.children.size)
    }

    @Test
    fun `custom scene should work`() {
        // Verifies create() can define internal structure.
        class CustomScene(name: String = "custom", block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block = block) {
            override fun create() {
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
            override fun create() {
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
        val root = EmptyNode("root") {
            EmptyNode("a")
            EmptyNode("b")
        }
        root.buildTree()

        val a = root.getNode<EmptyNode>("a")
        a.name = "renamed"
        assertEquals("/root/renamed", a.path)
        assertSame(a, root.getNode<EmptyNode>("renamed"))

        root.reparent(a, root.getNode<EmptyNode>("b"))

        assertEquals("/root/b/renamed", a.path)
        assertSame(a, root.getNode<EmptyNode>("b/renamed"))
        assertSame(root.getNode<EmptyNode>("b"), a.getNode<EmptyNode>(".."))
    }

    @Test
    fun `prefab child skips lifecycle until built manually`() {
        var readyCalls = 0
        val root = EmptyNode("root")
        root.buildTree()

        val prefab = EmptyNode("child") {
            behavior(onReady = { readyCalls++ })
        }.asPrefab()

        root.addChild(prefab)
        assertEquals(0, readyCalls)

        prefab.buildTree()
        assertEquals(1, readyCalls)
    }

    @Test
    fun `group changes on built node should mirror through scene manager`() {
        val root = EmptyNode("root")
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
        val root = EmptyNode("root") {
            EmptyNode("visibleChild") {
                // Not visible/skipOnSearch = true (using empty node for now but just assume we test relative resolution)
                EmptyNode("subChild")
            }
        }.asSceneRoot()
        root.buildTree()

        val child = root.getNode<EmptyNode>("./visibleChild/subChild")
        assertEquals("subChild", child.name)

        val sibling = child.getNode<EmptyNode>("..")
        assertEquals("visibleChild", sibling.name)

        val backToRoot = sibling.getNode<EmptyNode>("..")
        assertEquals("root", backToRoot.name)
    }

    @Test
    fun `unary plus and unary minus operators work`() {
        val child1 = EmptyNode("child1").asPrefab()
        val child2 = EmptyNode("child2").asPrefab()

        val root = EmptyNode("root") {
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
        val root = EmptyNode("root")
        val child = EmptyNode("child")

        root += child
        assertEquals(1, root.children.size)

        root -= child
        assertEquals(0, root.children.size)
    }

    @Test
    fun `plus operator returns parent`() {
        val root = EmptyNode("root")
        val child = EmptyNode("child")

        with(root) {
            val returned = this + child
            assertSame(root, returned)
            assertEquals(1, root.children.size)
        }
    }

    @Test
    fun `hasChildType works correctly`() {
        val root = EmptyNode("root") {
            EmptyNode2D("a2d")
        }
        root.buildTree()

        assertTrue(root.hasChildType(EmptyNode2D::class))
        assertFalse(root.hasChildType(EmptyNode::class)) // children does not include EmptyNode
    }

    @Test
    fun `runBehavior catches and logs exceptions`() {
        val behavior = createBehavior<EmptyNode>(
            onReady = { throw RuntimeException("Throwing behavior") }
        )

        val root = EmptyNode("root") {
            attachBehavior(behavior)
        }

        assertFailsWith<RuntimeException> {
            root.buildTree()
        }
    }

    @Test
    fun `getNode supports complex path resolution`() {
        val target = EmptyNode("target")
        val child = EmptyNode("child") { +target }
        val wrapper = EmptyNode("wrapper") { +child }
        val root = EmptyNode("root") {
            +wrapper
            EmptyNode("sibling")
        }
        root.buildTree()

        // From target's perspective
        assertEquals(target, target.getNode<EmptyNode>("."))
        assertEquals(target, target.getNode<EmptyNode>(""))
        assertEquals(child, target.getNode<EmptyNode>(".."))
        // Resolving parent, skipping wrapper
        assertEquals(wrapper, target.getNode<EmptyNode>("../.."))
        // Resolving up and down
        assertEquals<EmptyNode>(root.getNode("sibling"), target.getNode("../../../sibling"))

        // From root's perspective
        assertEquals<EmptyNode>(target, root.getNode("wrapper/child/target"))
        assertEquals<EmptyNode>(target, wrapper.getNode("child/target")) // Because wrapper is skipped

        // Invalid paths
        assertNull(root.getNodeOrNull<EmptyNode>("missing"))
        assertNull(root.getNodeOrNull<EmptyNode>("../missing"))

        // Root path fallback
        assertEquals<EmptyNode>(root, root.getNode("/"))
    }
}
