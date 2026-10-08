package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodeConstructionTests {
    private lateinit var scenes: SceneManager

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    private class FailingNode(failure: Throwable, cleanup: () -> Unit) : Node<FailingNode>("failed") {
        init {
            onRemoval(cleanup)
            throw failure
        }
    }

    @Test
    fun `explicit boundary releases subclass initializer state and owned cleanup once`() {
        val failure = IllegalStateException("initializer")
        var disposed = 0
        assertSame(failure, assertFails { nodeConstruction { FailingNode(failure) { disposed++ } } })
        assertEquals(0, scenes.retainedStateCount)
        assertEquals(1, disposed)
        scenes.onExit()
        assertEquals(1, disposed)
    }

    @Test
    fun `nested successful nodes join outer rollback but failed inner nodes stay isolated`() {
        val existing = EmptyNode("existing")
        val innerFailure = IllegalArgumentException("inner")
        lateinit var successful: EmptyNode
        lateinit var isolated: EmptyNode
        assertFailsWith<IllegalStateException> {
            nodeConstruction {
                successful = nodeConstruction { EmptyNode("successful") }
                assertSame(
                    innerFailure,
                    assertFails {
                        nodeConstruction {
                            isolated = EmptyNode("isolated")
                            throw innerFailure
                        }
                    }
                )
                assertTrue(successful.isValid)
                assertTrue(existing.isValid)
                throw IllegalStateException("outer")
            }
        }
        assertTrue(successful.isFreed)
        assertTrue(isolated.isFreed)
        assertTrue(existing.isValid)
        assertEquals(1, scenes.retainedStateCount)
    }

    @Test
    fun `builder failure removes newly attached children without disposing its existing parent`() {
        val failure = IllegalStateException("builder")
        var disposed = 0
        val root = EmptyNode("root") {
            EmptyNode("child").onDestroy { disposed++ }
            throw failure
        }
        assertFails { scenes.currScene = root }
        assertTrue(root.isValid)
        assertTrue(root.children.isEmpty())
        assertEquals(1, scenes.retainedStateCount)
        assertEquals(1, disposed)
        assertEquals(0, scenes.indexedNodeCount)
    }

    @Test
    fun `rollback preserves original error and collects reentrant cleanup nodes`() {
        val failure = IllegalStateException("initializer")
        val cleanupFailure = IllegalArgumentException("cleanup")
        lateinit var createdDuringCleanup: EmptyNode
        var destroyed = 0
        val thrown = assertFails {
            nodeConstruction {
                FailingNode(failure) {
                    createdDuringCleanup = EmptyNode("cleanup-created")
                    createdDuringCleanup.onDestroy { destroyed++ }
                    throw cleanupFailure
                }
            }
        }
        assertSame(failure, thrown)
        assertSame(cleanupFailure, thrown.suppressed.single().cause)
        assertTrue(createdDuringCleanup.isFreed)
        assertEquals(1, destroyed)
        assertEquals(0, scenes.retainedStateCount)
    }

    @Test
    fun `existing children attached to a failed new parent remain reusable`() {
        val existing = EmptyNode("existing")
        assertFails {
            nodeConstruction {
                EmptyNode("new").addChild(existing)
                throw IllegalStateException("failure")
            }
        }
        assertTrue(existing.isValid)
        assertNull(existing.parent)
        assertEquals(1, scenes.retainedStateCount)
    }

    @Test
    fun `already freed participant and queued participant are disposed once`() {
        var destroyed = 0
        lateinit var node: EmptyNode
        assertFails {
            nodeConstruction {
                node = EmptyNode("queued")
                node.onDestroy { destroyed++ }
                node.queueFree()
                scenes.onUpdate(0f)
                throw IllegalStateException("failure")
            }
        }
        assertTrue(node.isFreed)
        assertEquals(1, destroyed)
        assertEquals(0, scenes.retainedStateCount)
    }

    @Test
    fun `Java explicit boundary releases a partial facade without plugin integration`() {
        val failure = IllegalStateException("java initializer")
        var destroyed = 0
        assertSame(
            failure,
            assertFails {
                FailingConstructionJavaNode.construct({ destroyed++ }, failure)
            }
        )
        assertEquals(1, destroyed)
        assertEquals(0, scenes.retainedStateCount)
    }

    @Test
    fun `rollback inside scene update removes groups indexes and current root immediately`() {
        scenes.onEnter()
        val root = EmptyNode("existing") {
            behavior(onUpdate = {
                assertFails {
                    nodeConstruction {
                        val child = EmptyNode("new")
                        addChild(child)
                        child.addGroup("temporary")
                        child.queueFree()
                        throw IllegalStateException("frame")
                    }
                }
                assertTrue(children.isEmpty())
                assertEquals(1, scenes.retainedStateCount)
                assertEquals(1, scenes.indexedNodeCount)
                assertTrue(scenes.queryGroup("temporary").isEmpty())
            })
        }
        scenes.currScene = root
        scenes.onUpdate(0f)
        assertEquals(1, scenes.retainedStateCount)
        assertSame(root, scenes.currScene)
        scenes.currScene = null
        assertFails {
            nodeConstruction {
                EmptyNode("failed-root").asSceneRoot()
                throw IllegalStateException("root")
            }
        }
        assertNull(scenes.currScene)
        assertEquals(0, scenes.indexedNodeCount)
        assertEquals(1, scenes.retainedStateCount)
    }

    @Test
    fun `multiple cleanup failures preserve registration order and attempt all callbacks`() {
        val failure = IllegalStateException("original")
        val first = IllegalArgumentException("first cleanup")
        val second = IllegalArgumentException("second cleanup")
        val trace = mutableListOf<String>()
        val thrown = assertFails {
            nodeConstruction {
                EmptyNode("cleanup").apply {
                    onRemoval {
                        trace += "first"
                        throw first
                    }
                    onRemoval {
                        trace += "second"
                        throw second
                    }
                    onDestroy { trace += "destroy" }
                }
                throw failure
            }
        }
        assertSame(failure, thrown)
        val cleanup = thrown.suppressed.single()
        assertSame(first, cleanup.cause)
        assertSame(second, cleanup.suppressed.single().cause)
        assertEquals(listOf("first", "second", "destroy"), trace)
        assertEquals(0, scenes.retainedStateCount)
    }

    private fun earlyReturn(): EmptyNode = nodeConstruction { return EmptyNode("early") }

    @Test
    fun `nonlocal return commits ownership into enclosing rollback and restores the scope`() {
        lateinit var node: EmptyNode
        assertFails {
            nodeConstruction {
                node = earlyReturn()
                throw IllegalStateException("outer")
            }
        }
        assertTrue(node.isFreed)
        val detached = earlyReturn()
        assertTrue(detached.isValid)
        assertEquals(1, scenes.retainedStateCount)
    }

    @Test
    fun `Java builder protects subclass failure without constructor instrumentation`() {
        val failure = IllegalStateException("java child")
        var destroyed = 0
        val parent = FailingConstructionJavaNode.builder({ destroyed++ }, failure)
        assertFails { scenes.currScene = parent }
        assertTrue(parent.isValid)
        assertTrue(parent.children.isEmpty())
        assertEquals(1, destroyed)
        assertEquals(1, scenes.retainedStateCount)
    }

    private class PartialNode(failure: Throwable, acquired: () -> Unit, released: () -> Unit) :
        Node<PartialNode>("partial") {
        init {
            acquired()
            onDestroy(released)
            throw failure
        }

        override fun onExitTree() {
            error("An unentered partial subclass must never receive an exit hook")
        }
    }

    @Test
    fun `initializer failure releases explicitly owned acquisition without partial exit hooks`() {
        var resources = 0
        val failure = IllegalStateException("after acquisition")
        assertSame(
            failure,
            assertFails {
                nodeConstruction { PartialNode(failure, { resources++ }, { resources-- }) }
            }
        )
        assertEquals(0, resources)
        assertEquals(0, scenes.retainedStateCount)
    }

    private class Matches : TreeSystem(UpdatePhase.FramePost, 0, Node::class) {
        val removed = mutableListOf<Node<*>>()
        val nodes get() = matchingNodes.toList()
        override fun processNode(node: Node<*>, delta: Float) {}
        override fun onNodeRemoved(node: Node<*>) {
            removed += node
        }
    }

    @Test
    fun `overlapping queued parent and child rollback releases indexes and system matches once`() {
        val system = Matches()
        scenes.addSystem(system)
        scenes.onEnter()
        var destroyed = 0
        lateinit var parent: EmptyNode
        lateinit var child: EmptyNode
        assertFails {
            nodeConstruction {
                child = EmptyNode("child")
                parent = EmptyNode("parent")
                parent.addChild(child)
                parent.onDestroy { destroyed++ }
                child.onDestroy { destroyed++ }
                parent.asSceneRoot()
                child.queueFree()
                parent.queueFree()
                throw IllegalStateException("failure")
            }
        }
        assertTrue(parent.isFreed)
        assertTrue(child.isFreed)
        assertEquals(2, destroyed)
        assertEquals(2, system.removed.size)
        assertTrue(system.nodes.isEmpty())
        assertEquals(0, scenes.retainedStateCount)
        assertEquals(0, scenes.indexedNodeCount)
        assertNull(scenes.currScene)
        scenes.onUpdate(0f)
        assertEquals(2, destroyed)
        assertEquals(2, system.removed.size)
    }

    @Test
    fun `rollback remains immediate when invoked from a destruction callback during deletion flush`() {
        val root = EmptyNode("root")
        var destroyed = 0
        root.onDestroy {
            assertFails {
                nodeConstruction {
                    EmptyNode("nested").onDestroy { destroyed++ }
                    throw IllegalStateException("during deletion")
                }
            }
            assertEquals(1, scenes.retainedStateCount)
            assertEquals(1, destroyed)
        }
        root.asSceneRoot()
        root.queueFree()
        scenes.onUpdate(0f)
        assertEquals(0, scenes.retainedStateCount)
        assertEquals(1, destroyed)
    }
}
