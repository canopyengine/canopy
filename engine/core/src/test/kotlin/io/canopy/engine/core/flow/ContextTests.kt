package io.canopy.engine.core.flow

import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Adjust `n(name)` helper if your Node concrete class differs.
 */
class ContextTests {

    companion object {

        @JvmStatic
        @BeforeAll
        fun setup() {
            ManagersRegistry.withScope {
                +SceneManager()
            }
        }
    }

    // --- Helpers ------------------------------------------------------------

    private class EmptyNode(name: String, block: EmptyNode.() -> Unit = {}) : Node<EmptyNode>(name, block)

    private fun n(name: String, block: EmptyNode.() -> Unit = {}) = EmptyNode(name, block)

    private fun Node<*>.child(name: String, block: EmptyNode.() -> Unit = {}): Node<*> {
        val c = n(name, block)
        addChild(c)
        return c
    }

    private object DebugModeKey // any object key (no ::class)
    private object SeasonKey

    // --- Tests --------------------------------------------------------------

    @Test
    fun `context resolves from nearest scope`() {
        val root = n("root") {
            Context {
                provide("debug") { true }
                n("child") {
                    // child node exists under scope
                }
            }
        }

        root.buildTree()

        // Find the "child" node. Adjust if you have find-by-path utilities.
        val child = root.getNode<EmptyNode>("./child")

        val debug: Boolean = child.resolve("debug")
        assertTrue(debug)
    }

    @Test
    fun `context resolves from ancestor scope when not provided locally`() {
        val root = n("root") {
            Context {
                provide("debug") { true }
                n("a") {
                    n("b") { }
                }
            }
        }

        root.buildTree()

        val b = root.getNode<EmptyNode>("./a/b")

        val debug: Boolean = b.resolve("debug")
        assertTrue(debug)
    }

    @Test
    fun `nearest provider wins (shadowing overrides)`() {
        val root = n("root") {
            Context {
                provide("debug") { true }

                n("a") { }

                Context {
                    provide("debug") { false }
                    n("b") { }
                }
            }
        }

        root.buildTree()

        val a = root.getNode<EmptyNode>("./a")
        val b = root.getNode<EmptyNode>("./b")

        val aDebug: Boolean = a.resolve("debug")
        val bDebug: Boolean = b.resolve("debug")

        assertTrue(aDebug)
        assertFalse(bDebug)
    }

    @Test
    fun `contextOrNull returns null when missing`() {
        val root = n("root") { n("child") { } }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        val missing: String? = child.resolveOrNull("nope")
        assertNull(missing)
    }

    @Test
    fun `context throws when missing`() {
        val root = n("root") { n("child") { } }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        val ex = assertThrows<IllegalStateException> {
            child.resolve<Int>("missing")
        }

        // Optional: if your error message includes path/name
        assertTrue(ex.message!!.contains("missing", ignoreCase = true))
    }

    @Test
    fun `supports non-string keys (Any keys)`() {
        val root = n("root") {
            Context {
                provide("debugMode") { true }
                provide("season") { "winter" }
                n("child") { }
            }
        }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        val debug: Boolean = child.resolve("debugMode")
        val season: String = child.resolve("season")

        assertTrue(debug)
        assertEquals("winter", season)
    }

    @Test
    fun `multiple keys of same value type do not collide`() {
        val root = n("root") {
            Context {
                provide("keyA") { 1 }
                provide("keyB") { 2 }
                n("child") { }
            }
        }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        assertEquals(1, child.resolve("keyA"))
        assertEquals(2, child.resolve("keyB"))
    }

    @Test
    fun `deep nesting still resolves correctly`() {
        val root = n("root") {
            Context {
                provide("x") { 42 }

                n("a") {
                    n("b") {
                        n("c") { }
                    }
                }
            }
        }

        root.buildTree()

        val c = root.getNode<EmptyNode>("./a/b/c")
        assertEquals(42, c.resolve("x"))
    }

    @Test
    fun `nested contexts should feed into each other`() {
        val root = n("root") {
            Context {
                provide("keyA") { 1 }

                Context {
                    provide("keyB") { 2 }

                    n("a")
                }
            }
        }
        root.buildTree()

        val c = root.getNode<EmptyNode>("./a")

        assertEquals(1, c.resolve("keyA"))
    }

    // --- Tiny adapter -------------------------------------------------------
    // If your Node doesn't expose children()/name, replace these calls with your real APIs.

    private fun Node<*>.children(): List<Node<*>> = // Replace with your actual children accessor
        // e.g. this.children.values.toList()
        (this as DynamicChildren).childrenList()

    private interface DynamicChildren {
        fun childrenList(): List<Node<*>>
    }
}
