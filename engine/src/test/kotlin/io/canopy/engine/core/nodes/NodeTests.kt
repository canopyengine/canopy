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

        assertEquals(0, tree.children.size)
    }

    @Test
    fun `custom scene should work`() {
        // Verifies create() can define internal structure.
        class CustomScene(name: String = "custom", block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block) {
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
            Node<CustomScene>(name, block) {
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
}
