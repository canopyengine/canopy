package io.canopy.engine.core.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.canopy.engine.core.exceptions.InvalidNodeDefinitionException
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.exceptions.NodeNotFoundException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.events.TextInputEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/** Exercises retained-facade guards, reusable detachment and unsafe precompiled class rejection. */
class NodeStateSafetyTests {
    private lateinit var scenes: SceneManager

    /** Concrete managed-state node used to preserve the class-named DSL and typed lookup contract. */
    private class EnemyNode(name: String, block: EnemyNode.() -> Unit = {}) : Node<EnemyNode>(name, block = block) {
        var health by nodeProperty(100)
        val resource by nodeProperty(Any())
        override fun onUpdate(delta: Float) {
            if (health <= 0) queueFree()
        }
    }

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

    @Test
    fun `retained facade cannot access destroyed built in or custom state`() {
        val root = EmptyNode("Game") { EnemyNode("Enemy") { health = 150 } }
        scenes.currScene = root
        val enemy: EnemyNode = root.getNode("Enemy")
        assertEquals(150, enemy.health)
        val identity = enemy.nodeId
        enemy.queueFree()
        scenes.onUpdate(0f)

        assertFalse(enemy.isValid)
        assertEquals(identity, enemy.nodeId)
        assertNull(root.getNodeOrNull<EnemyNode>("Enemy"))
        listOf<() -> Unit>(
            { enemy.health }, { enemy.health = 1 }, { enemy.resource }, { enemy.name },
            { enemy.children }, { enemy.processMode }, { root.addChild(enemy) },
            { enemy.onDestroy {} }, { enemy.nodeUpdate(0f) }
        ).forEach { operation ->
            val error = assertFailsWith<NodeDestroyedException>(block = operation)
            assertEquals(identity, error.diagnostic.nodeId)
            assertEquals("EnemyNode", error.diagnostic.nodeType)
            assertTrue(error.diagnostic.lastPath.endsWith("/Enemy"))
        }
        assertEquals(1, scenes.retainedStateCount)
        assertEquals(1, scenes.indexedNodeCount)
    }

    @Test
    fun `unsafe Java definitions are rejected before allocating any state`() {
        repeat(2) {
            val error = assertFailsWith<InvalidNodeDefinitionException> { UnsafeJavaNode() }
            assertTrue("resource" in error.message.orEmpty())
            assertFailsWith<UnsupportedOperationException> { (error.fields as MutableList<String>).clear() }
            assertEquals(0, scenes.retainedStateCount)
            assertEquals(0, scenes.indexedNodeCount)
            assertNull(scenes.currScene)
        }
    }

    @Test
    fun `reusable detachment preserves state and exclusive resources`() {
        val root = EmptyNode("Game")
        scenes.currScene = root
        val enemy = EnemyNode("Enemy")
        root.addChild(enemy)
        var releases = 0
        enemy.onDestroy { releases++ }
        root.removeChild(enemy)
        assertTrue(enemy.isValid)
        enemy.health = 42
        root.addChild(enemy)
        assertEquals(42, enemy.health)
        assertEquals(0, releases)
        enemy.queueFree()
        scenes.onPhysicsUpdate(0f)
        assertEquals(1, releases)
    }

    @Test
    fun `diagnostic state labels survive entry detachment and permanent cleanup`() {
        val node = EmptyNode("diagnostic")
        fun lookupState() = assertFailsWith<NodeNotFoundException> {
            node.getNode<EmptyNode>("missing")
        }.diagnostic.state
        assertEquals("Detached", lookupState())
        scenes.currScene = node
        assertEquals("Active", lookupState())
        scenes.currScene = null
        assertEquals("Detached", lookupState())
        var cleanupState: String? = null
        node.onDestroy {
            cleanupState = assertFailsWith<NodeDestroyedException> { node.name }.diagnostic.state
        }
        node.queueFree()
        scenes.onUpdate(0f)
        assertEquals("Destroying", cleanupState)
        assertEquals("Destroyed", assertFailsWith<NodeDestroyedException> { node.name }.diagnostic.state)
    }

    @Test
    fun `consumed input stops sibling and ancestor behavior callbacks`() {
        val calls = mutableListOf<String>()
        val root = EmptyNode("root") {
            behavior(onInput = { calls += "root" })
            EmptyNode("first") {
                behavior(onInput = {
                    calls += "first"
                    it.consume()
                })
                EmptyNode("grandchild") { behavior(onInput = { calls += "grandchild" }) }
            }
            EmptyNode("sibling") { behavior(onInput = { calls += "sibling" }) }
        }
        scenes.currScene = root
        root.nodeInput(TextInputEvent("fresh"))
        assertEquals(listOf("grandchild", "first"), calls)
        calls.clear()
        root.nodeInput(TextInputEvent("handled").apply { consume() })
        assertTrue(calls.isEmpty())
    }
}
