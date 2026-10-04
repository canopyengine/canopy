package io.canopy.engine.core.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.TextInputEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodePauseTests {
    private lateinit var scenes: SceneManager
    private var paused = false
    private val calls = mutableListOf<String>()

    private class RecordingNode(name: String, calls: MutableList<String>) : Node<RecordingNode>(name) {
        private val calls by nodeProperty(calls)
        override fun onUpdate(delta: Float) {
            calls += "frame:$name:$delta"
            super.onUpdate(delta)
        }

        override fun onPhysicsUpdate(delta: Float) {
            calls += "physics:$name:$delta"
            super.onPhysicsUpdate(delta)
        }

        override fun onInput(event: InputEvent) {
            calls += "input:$name"
            super.onInput(event)
        }
    }

    @BeforeEach
    fun setup() {
        ManagersRegistry.exit()
        scenes = SceneManager().also { it.pauseState = { paused } }
        ManagersRegistry.register(scenes)
        scenes.onEnter()
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test
    fun `default nodes stop while explicit descendants process frame physics and input`() {
        // Arrange
        val root = RecordingNode("root", calls)
        val ordinary = RecordingNode("ordinary", calls)
        val menu = RecordingNode("menu", calls).also { it.processMode = ProcessMode.WhenPaused }
        val disabled = RecordingNode("disabled", calls).also { it.processMode = ProcessMode.Disabled }
        val always = RecordingNode("always", calls).also { it.processMode = ProcessMode.Always }
        val inherited = RecordingNode("inherited", calls)
        root.addChild(ordinary)
        root.addChild(menu)
        root.addChild(disabled)
        disabled.addChild(always)
        always.addChild(inherited)
        scenes.currScene = root
        menu.behavior(onUpdate = { calls += "behavior:menu" })

        // Act
        paused = true
        scenes.onUpdate(0.2f)
        scenes.onPhysicsUpdate(0.1f)
        root.dispatchInput(TextInputEvent("hello"))

        // Assert
        assertEquals(
            listOf(
                "frame:menu:0.2", "behavior:menu", "frame:always:0.2", "frame:inherited:0.2",
                "physics:menu:0.1", "physics:always:0.1", "physics:inherited:0.1",
                "input:menu", "input:always", "input:inherited"
            ),
            calls
        )
        calls.clear()
        paused = false
        scenes.onUpdate(0.2f)
        assertEquals(
            listOf("frame:root:0.2", "frame:ordinary:0.2", "frame:always:0.2", "frame:inherited:0.2"),
            calls
        )
    }

    @Test
    fun `inherited modes track parent changes and reparenting without caching`() {
        // Arrange
        val root = EmptyNode("root")
        val disabled = EmptyNode("disabled").also { it.processMode = ProcessMode.Disabled }
        val always = EmptyNode("always").also { it.processMode = ProcessMode.Always }
        val child = EmptyNode("child")
        root.addChild(disabled)
        root.addChild(always)
        disabled.addChild(child)
        scenes.currScene = root
        paused = true

        // Act and Assert
        assertFalse(child.canProcess())
        disabled.reparent(child, always)
        assertTrue(child.canProcess())
        always.processMode = ProcessMode.Pausable
        assertFalse(child.canProcess())
        child.processMode = ProcessMode.WhenPaused
        assertTrue(child.canProcess())
        paused = false
        assertFalse(child.canProcess())
        child.processMode = ProcessMode.Inherit
        assertTrue(child.canProcess())
    }

    @Test
    fun `systems retain registrations and hooks while gating node processing`() {
        // Arrange
        val system = object : TreeSystem(UpdatePhase.FramePre, 0, EmptyNode::class) {
            override fun beforeProcess(delta: Float) {
                calls += "before"
            }
            override fun afterProcess(delta: Float) {
                calls += "after"
            }
            override fun processNode(node: Node<*>, delta: Float) {
                calls += node.name
            }
        }
        scenes.addSystem(system)
        val root = EmptyNode("root") {
            EmptyNode("menu") { processMode = ProcessMode.WhenPaused }
        }
        scenes.currScene = root

        // Act
        paused = true
        scenes.onUpdate(0.2f)
        paused = false
        scenes.onUpdate(0.2f)

        // Assert
        assertEquals(listOf("before", "menu", "after", "before", "root", "after"), calls)
    }

    @Test
    fun `physics system hooks stay active while paused nodes do not process`() {
        // Arrange
        val system = object : TreeSystem(UpdatePhase.PhysicsPre, 0, EmptyNode::class) {
            override fun beforeProcess(delta: Float) {
                calls += "before:$delta"
            }
            override fun afterProcess(delta: Float) {
                calls += "after:$delta"
            }
            override fun processNode(node: Node<*>, delta: Float) {
                calls += node.name
            }
        }
        scenes.addSystem(system)
        scenes.currScene = EmptyNode("root")

        // Act
        paused = true
        scenes.onPhysicsUpdate(0.1f)

        // Assert
        assertEquals(listOf("before:0.1", "after:0.1"), calls)
    }

    @Test
    fun `every explicit processing mode has consistent running and paused eligibility`() {
        // Arrange
        val node = EmptyNode("root")
        val expectations = mapOf(
            ProcessMode.Inherit to (true to false),
            ProcessMode.Pausable to (true to false),
            ProcessMode.WhenPaused to (false to true),
            ProcessMode.Always to (true to true),
            ProcessMode.Disabled to (false to false)
        )

        // Act and Assert
        expectations.forEach { (mode, eligible) ->
            node.processMode = mode
            assertEquals(eligible.first, node.canProcess(false), "$mode while running")
            assertEquals(eligible.second, node.canProcess(true), "$mode while paused")
        }
    }

    @Test
    fun `disabled nodes retain lifecycle callbacks while paused`() {
        // Arrange
        paused = true
        val root = EmptyNode("root") {
            processMode = ProcessMode.Disabled
            behavior(
                onEnterTree = { calls += "enter" },
                onReady = { calls += "ready" },
                onExitTree = { calls += "exit" },
                onUpdate = { calls += "frame" },
                onPhysicsUpdate = { calls += "physics" },
                onInput = { calls += "input" }
            )
        }

        // Act
        scenes.currScene = root
        scenes.onUpdate(0.2f)
        scenes.onPhysicsUpdate(0.1f)
        root.dispatchInput(TextInputEvent("hello"))
        scenes.currScene = null

        // Assert
        assertEquals(listOf("enter", "ready", "exit"), calls)
    }
}
