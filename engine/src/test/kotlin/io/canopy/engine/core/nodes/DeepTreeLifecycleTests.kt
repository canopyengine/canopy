package io.canopy.engine.core.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.TextInputEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/** Moderate depth exercises ordering and mutation without imposing an environment-specific stack limit on CI. */
class DeepTreeLifecycleTests {
    private lateinit var scenes: SceneManager
    private val calls = mutableListOf<String>()

    private class HookNode(name: String, block: HookNode.() -> Unit) : Node<HookNode>(name, block = block) {
        var hook by nodeProperty<(String, InputEvent?) -> Unit>({ _, _ -> })
        override fun onUpdate(delta: Float) = hook("frame", null)
        override fun onPhysicsUpdate(delta: Float) = hook("physics", null)
        override fun onInput(event: InputEvent) = hook("input", event)
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

    private fun chain(action: (Int, String, InputEvent?) -> Unit = { _, _, _ -> }): List<HookNode> {
        val nodes = (0 until 96).map { index ->
            HookNode("n$index") {
                hook = { phase, event ->
                    calls += "hook:$phase:$index"
                    action(index, phase, event)
                }
                behavior(
                    onEnterTree = { calls += "enter:$index" },
                    onReady = { calls += "ready:$index" },
                    onExitTree = { calls += "exit:$index" },
                    onUpdate = { calls += "behavior:frame:$index" },
                    onPhysicsUpdate = { calls += "behavior:physics:$index" },
                    onInput = { calls += "behavior:input:$index" }
                )
            }
        }
        nodes.zipWithNext().forEach { (parent, child) -> parent.addChild(child) }
        return nodes
    }

    @Test
    fun `deep chain enters parent first and readies and exits child first across reuse`() {
        val nodes = chain()
        val root = nodes.first()
        scenes.currScene = root
        assertEquals((0..95).map { "enter:$it" } + (95 downTo 0).map { "ready:$it" }, calls)
        var released = 0
        nodes.forEach { it.onRemoval { released++ } }
        calls.clear()
        scenes.currScene = null
        assertEquals((95 downTo 0).map { "exit:$it" }, calls)
        assertEquals(96, released)
        assertTrue(nodes.all { it.isValid && !it.isInsideTree })
        calls.clear()
        scenes.currScene = root
        assertEquals((0..95).map { "enter:$it" } + (95 downTo 0).map { "ready:$it" }, calls)
        assertEquals(96, released)
    }

    @Test
    fun `deep dispatch skips detached descendants and destroys queued descendants only after physics traversal`() {
        lateinit var nodes: List<HookNode>
        var detach = true
        nodes = chain { index, phase, _ ->
            if (index == 32 && phase == "frame" && detach) nodes[32].removeChild(nodes[33])
            if (index == 32 && phase == "physics") nodes[33].queueFree()
        }
        scenes.currScene = nodes.first()
        calls.clear()
        scenes.onUpdate(0f)
        assertEquals(
            (0..32).map { "hook:frame:$it" } + (95 downTo 33).map { "exit:$it" } +
                (32 downTo 0).map { "behavior:frame:$it" },
            calls
        )
        assertTrue(nodes.drop(33).all { it.isValid && !it.isInsideTree })
        detach = false
        nodes[32].addChild(nodes[33])
        calls.clear()
        scenes.onPhysicsUpdate(0f)
        assertEquals(
            (0..95).map { "hook:physics:$it" } + (95 downTo 0).map { "behavior:physics:$it" } +
                (95 downTo 33).map { "exit:$it" },
            calls
        )
        assertTrue(nodes.drop(33).all { it.isFreed })
        assertTrue(nodes.take(33).all { it.isInsideTree })
        assertEquals(33, scenes.indexedNodeCount)
    }

    @Test
    fun `deep input consumption in node hook stops traversal while paused explicit descendants remain eligible`() {
        val nodes = chain { index, phase, event ->
            if (index == 48 && phase == "input") event!!.consume()
        }
        scenes.currScene = nodes.first()
        calls.clear()
        val event = TextInputEvent("consume")
        nodes.first().nodeInput(event)
        assertEquals((0..48).map { "hook:input:$it" }, calls)
        assertTrue(event.isHandled)
        scenes.pauseState = { true }
        nodes.first().processMode = ProcessMode.Disabled
        nodes.last().processMode = ProcessMode.Always
        calls.clear()
        val pausedEvent = TextInputEvent("paused")
        nodes.first().nodeInput(pausedEvent)
        assertEquals(listOf("hook:input:95", "behavior:input:95"), calls)
        assertFalse(pausedEvent.isHandled)
    }
}
