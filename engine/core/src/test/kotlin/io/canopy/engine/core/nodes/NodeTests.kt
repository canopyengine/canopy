package io.canopy.engine.core.nodes

import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import com.badlogic.gdx.math.Vector2
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.nodes.core.asSceneRoot
import io.canopy.engine.core.nodes.core.attachBehavior
import io.canopy.engine.core.nodes.core.behavior
import io.canopy.engine.core.nodes.core.createBehavior
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll

class NodeTests {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            ManagersRegistry.withScope {
                register(SceneManager())
            }
        }
    }

    @Test
    fun `structure should pass`() {
        val root = EmptyNode("test-node")

        val scene = EmptyNode("root") {
            EmptyNode("child-a")

            EmptyNode("child-b") {
                EmptyNode("child-c")
            }
        }

        scene.buildTree()

        assertSame(2, scene.children.size)
        assertSame(scene, scene.getNode("child-b").parent)
        assertSame(
            scene.getNode("child-b"),
            scene.getNode("child-b/child-c").parent
        )
    }

    @Test
    fun `behavior should work`() {
        val childCount: MutableMap<String, Int> = mutableMapOf()

        // Behavior factory lambda
        val lambdaBehavior =
            createBehavior<EmptyNode>(
                onReady = {
                    val parent = parent ?: return@createBehavior
                    childCount.merge(parent.name, 1) { old, new -> old + new }
                    if (name !in childCount) {
                        childCount[name] = 0
                    }
                }
            )

        // Build scene
        EmptyNode("Test 2") {
            EmptyNode("child-a") {
                attachBehavior(lambdaBehavior)
            } // pass node

            EmptyNode("child-b") {
                attachBehavior(lambdaBehavior)

                EmptyNode("child-c") {
                    attachBehavior(lambdaBehavior)
                } // pass node
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
        val callOrder = mutableListOf<String>()
        val behaviour =
            createBehavior<EmptyNode>(
                onReady = {
                    callOrder += name
                }
            )

        // Build scene
        EmptyNode("Test 2") {
            attachBehavior(behaviour)

            EmptyNode("child-a") {
                attachBehavior(behaviour) // pass node
            }

            EmptyNode("child-b") {
                attachBehavior(behaviour)
                EmptyNode("child-c") {
                    attachBehavior(behaviour)
                } // pass node
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
        var nTicks = 0
        var nPhysicsTicks = 0

        val behavior =
            createBehavior<EmptyNode>(
                onUpdate = {
                    nTicks++
                },
                onPhysicsUpdate = {
                    nPhysicsTicks++
                }
            )

        val tree = EmptyNode("root") {
            attachBehavior(behavior)
        }
        tree.buildTree()

        launch {
            repeat(2) { i ->
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
            }

        assertFalse(wasCalled)
        tree.removeChild("child")

        assertTrue(wasCalled)
    }

    @Test
    fun `queue free should delete node`() {
        val tree =
            EmptyNode("root") {
                EmptyNode("child")
            }
        tree.buildTree()

        val child = tree.getNode("child")
        assertNotNull(child)

        child.queueFree()

        assertEquals(0, tree.children.size)
    }

    @Test
    fun `custom scene should work`() {
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

        assertEquals(2, customScene.children.size)
    }

    @Test
    fun `patching internal node should work`() {
        class CustomScene(name: String = "custom", block: CustomScene.() -> Unit = {}) :
            Node<CustomScene>(name, block) {
            override fun create() {
                EmptyNode("empty")
            }
        }

        val node = CustomScene {
            patch<EmptyNode>("./empty") {
                name = "patched"
                at(100f, 100f)
            }
        }

        val child = node.getNode<EmptyNode>("./patched")

        assertEquals("patched", child.name)
        assertEquals(Vector2(100f, 100f), child.position)
    }

    @Test
    fun `custom  node class with internal script should work`() {
        var wasCalled = false

        class CustomScene(name: String, block: CustomScene.() -> Unit = {}) : Node<CustomScene>(name, block = block) {
            override fun create() {
                behavior(
                    onReady = { wasCalled = true }
                )
            }
        }

        val root = CustomScene("root").asSceneRoot()

        root.buildTree()

        assertTrue(wasCalled)
    }
}
