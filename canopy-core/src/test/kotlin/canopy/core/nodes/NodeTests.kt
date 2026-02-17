package canopy.core.nodes

import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import canopy.core.managers.ManagersRegistry
import canopy.core.managers.SceneManager
import canopy.core.nodes.core.Behavior
import canopy.core.nodes.core.Node
import canopy.core.nodes.core.behavior
import canopy.core.nodes.types.empty.EmptyNode
import com.badlogic.gdx.math.Vector2
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

        val scene =
            EmptyNode("root") {
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
        val behaviour =
            behavior<EmptyNode>(
                onReady = {
                    val parent = parent ?: return@behavior
                    childCount.merge(parent.name, 1) { old, new -> old + new }
                    if (name !in childCount) {
                        childCount[name] = 0
                    }
                }
            )

        // Build scene
        EmptyNode("Test 2") {
            EmptyNode("child-a", behaviour) // pass node

            EmptyNode("child-b", behaviour) {
                EmptyNode("child-c", behaviour) // pass node
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
            behavior<EmptyNode>(
                onReady = {
                    callOrder += name
                }
            )

        // Build scene
        EmptyNode("Test 2", behaviour) {
            EmptyNode("child-a", behaviour) // pass node

            EmptyNode("child-b", behaviour) {
                EmptyNode("child-c", behaviour) // pass node
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
            behavior<EmptyNode>(
                onUpdate = {
                    nTicks++
                },
                onPhysicsUpdate = {
                    nPhysicsTicks++
                }
            )

        val tree = EmptyNode("root", behavior)
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
            behavior<EmptyNode>(
                onReady = { wasCalled = true }
            )

        val root = EmptyNode("root")
        root.buildTree()

        assertFalse(wasCalled)

        root.addChild(EmptyNode("child", behavior))

        assertTrue(wasCalled)
    }

    @Test
    fun `removing node should call onExitTree`() {
        var wasCalled = false
        val behavior =
            behavior<EmptyNode>(
                onExitTree = { wasCalled = true }
            )

        val tree =
            EmptyNode("root") {
                EmptyNode("child", behavior)
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
        class CustomScene(
            name: String = "custom",
            script: (node: CustomScene) -> Behavior<CustomScene>? = { null },
            position: Vector2 = Vector2.Zero,
            scale: Vector2 = Vector2(1f, 1f),
            rotation: Float = 0f,
            groups: MutableList<String> = mutableListOf(),
            block: CustomScene.() -> Unit = {},
        ) : Node<CustomScene>(
            name,
            script,
            position,
            scale,
            rotation,
            groups,
            block = {
                EmptyNode("empty")
                block()
            }
        )

        val customScene =
            CustomScene {
                EmptyNode("child")
            }

        assertEquals(2, customScene.children.size)
    }
}
