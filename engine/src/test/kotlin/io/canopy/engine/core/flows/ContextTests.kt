package io.canopy.engine.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ContextTests {
    private enum class Key(override val key: String) : ContextKey {
        Debug("debug"),
        Season("season"),
    }

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope { register(SceneManager()) }
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `context resolves from nearest scope`() {
        val root = EmptyNode("root") {
            Context {
                provide("debug") { true }
                EmptyNode("child")
            }
        }
        root.buildTree()

        assertTrue(root.getNode<EmptyNode<*>>("child").fromContext<Boolean>("debug"))
    }

    @Test
    fun `nearest provider shadows an outer provider`() {
        val root = EmptyNode("root") {
            Context {
                provide("debug") { true }
                EmptyNode("a")
                Context {
                    provide("debug") { false }
                    EmptyNode("b")
                }
            }
        }
        root.buildTree()

        assertTrue(root.getNode<EmptyNode<*>>("a").fromContext<Boolean>("debug"))
        assertFalse(root.getNode<EmptyNode<*>>("b").fromContext<Boolean>("debug"))
    }

    @Test
    fun `optional context lookup returns null when missing`() {
        val root = EmptyNode("root") { EmptyNode("child") }
        root.buildTree()

        assertNull(root.getNode<EmptyNode<*>>("child").fromContextOrNull<String>("missing"))
    }

    @Test
    fun `required lookup through an empty context identifies the missing key`() {
        val root = EmptyNode("root") { Context { EmptyNode("child") } }
        root.buildTree()
        val child = root.getNode<EmptyNode<*>>("child")

        val failure = assertFailsWith<NoSuchElementException> { child.fromContext<Int>("missing") }

        assertTrue(failure.message.orEmpty().contains("missing"))
    }

    @Test
    fun `ContextKey providers and lookups interoperate with their string keys`() {
        val root = EmptyNode("root") {
            Context {
                provide(Key.Debug) { true }
                provide("season") { "winter" }
                EmptyNode("child")
            }
        }
        root.buildTree()
        val child = root.getNode<EmptyNode<*>>("child")

        assertTrue(child.fromContext<Boolean>("debug"))
        assertTrue(child.fromContext<Boolean>(Key.Debug))
        assertEquals("winter", child.fromContext<String>(Key.Season))
    }

    @Test
    fun `multiple keys of the same value type do not collide`() {
        val root = EmptyNode("root") {
            Context {
                provide("first") { 1 }
                provide("second") { 2 }
                EmptyNode("child")
            }
        }
        root.buildTree()
        val child = root.getNode<EmptyNode<*>>("child")

        assertEquals(1, child.fromContext<Int>("first"))
        assertEquals(2, child.fromContext<Int>("second"))
    }

    @Test
    fun `ancestor context resolves through ordinary grouping nodes`() {
        val root = EmptyNode("root") {
            Context {
                provide("value") { 42 }
                EmptyNode("a") { EmptyNode("b") { EmptyNode("c") } }
            }
        }
        root.buildTree()

        assertEquals(42, root.getNode<EmptyNode<*>>("a/b/c").fromContext<Int>("value"))
    }

    @Test
    fun `nested contexts retain both outer and inner providers`() {
        val root = EmptyNode("root") {
            Context {
                provide("outer") { 1 }
                Context {
                    provide("inner") { 2 }
                    EmptyNode("child")
                }
            }
        }
        root.buildTree()
        val child = root.getNode<EmptyNode<*>>("child")

        assertEquals(1, child.fromContext<Int>("outer"))
        assertEquals(2, child.fromContext<Int>("inner"))
    }
}
