package io.canopy.engine.core.reactive

import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.core.Node
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
            context {
                provide("debug" to true)
                n("child") {
                    // child node exists under scope
                }
            }
        }

        root.buildTree()

        // Find the "child" node. Adjust if you have find-by-path utilities.
        val child = root.getNode<EmptyNode>("./child")

        val debug: Boolean = child.context("debug")
        assertTrue(debug)
    }

    @Test
    fun `context resolves from ancestor scope when not provided locally`() {
        val root = n("root") {
            context {
                provide("debug" to true)
                n("a") {
                    n("b") { }
                }
            }
        }

        root.buildTree()

        val b = root.getNode<EmptyNode>("./a/b")

        val debug: Boolean = b.context("debug")
        assertTrue(debug)
    }

    @Test
    fun `nearest provider wins (shadowing overrides)`() {
        val root = n("root") {
            context {
                provide("debug" to true)

                n("a") { }

                context {
                    provide("debug" to false)
                    n("b") { }
                }
            }
        }

        root.buildTree()

        val a = root.getNode<EmptyNode>("./a")
        val b = root.getNode<EmptyNode>("./b")

        val aDebug: Boolean = a.context("debug")
        val bDebug: Boolean = b.context("debug")

        assertTrue(aDebug)
        assertFalse(bDebug)
    }

    @Test
    fun `contextOrNull returns null when missing`() {
        val root = n("root") { n("child") { } }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        val missing: String? = child.contextOrNull("nope")
        assertNull(missing)
    }

    @Test
    fun `context throws when missing`() {
        val root = n("root") { n("child") { } }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        val ex = assertThrows<IllegalStateException> {
            child.context<Int>("missing")
        }

        // Optional: if your error message includes path/name
        assertTrue(ex.message!!.contains("missing", ignoreCase = true))
    }

    @Test
    fun `supports non-string keys (Any keys)`() {
        val root = n("root") {
            context {
                provide("debugMode" to true)
                provide("season" to "winter")
                n("child") { }
            }
        }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        val debug: Boolean = child.context("debugMode")
        val season: String = child.context("season")

        assertTrue(debug)
        assertEquals("winter", season)
    }

    @Test
    fun `multiple keys of same value type do not collide`() {
        val root = n("root") {
            context {
                provide("keyA" to 1)
                provide("keyB" to 2)
                n("child") { }
            }
        }

        root.buildTree()

        val child = root.getNode<EmptyNode>("./child")

        assertEquals(1, child.context("keyA"))
        assertEquals(2, child.context("keyB"))
    }

    @Test
    fun `deep nesting still resolves correctly`() {
        val root = n("root") {
            context {
                provide("x" to 42)

                n("a") {
                    n("b") {
                        n("c") { }
                    }
                }
            }
        }

        root.buildTree()

        val c = root.getNode<EmptyNode>("./a/b/c")
        assertEquals(42, c.context("x"))
    }

    @Test
    fun `nested contexts should feed into each other`() {
        val root = n("root") {
            context {
                provide("keyA" to 1)

                context {
                    provide("keyB" to 2)

                    n("a")
                }
            }
        }
        root.buildTree()

        val c = root.getNode<EmptyNode>("./a")

        assertEquals(1, c.context("keyA"))
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
