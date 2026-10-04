package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeCleanupException
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.flows.events.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class Pr135ReviewTests {
    private lateinit var scenes: SceneManager

    @BeforeEach fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
    }

    @AfterEach fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test fun `freed emitter disconnects outgoing connections`() {
        lateinit var outgoing: NoArgEvent
        val root = EmptyNode("root") { outgoing = event() }
        scenes.currScene = root
        val survivor = EmptyNode("survivor")
        var calls = 0
        val handle = outgoing.connect(survivor) { calls++ }
        root.queueFree()
        scenes.onUpdate(0f)
        assertFailsWith<NodeDestroyedException> { outgoing.emit() }
        handle.disconnect()
        assertEquals(0, calls)
    }

    @Test fun `listener does not inherit emitter lifetime`() {
        val source = event()
        val nested = event()
        val handles = mutableListOf<EventDisconnectHandler>()
        val survivor = EmptyNode("survivor")
        val listener = source.connect(survivor) { handles += nested.connect {} }
        val root = EmptyNode("root") { behavior(onUpdate = { source.emit() }) }
        scenes.currScene = root
        scenes.onUpdate(0f)
        root.queueFree()
        scenes.onUpdate(0f)
        val remaining = nested.size()
        handles.forEach { it.disconnect() }
        listener.disconnect()
        assertEquals(2, remaining)
    }

    @Test fun `direct disconnect releases owner registration`() {
        val source = event()
        val root = EmptyNode("root")
        val listener: () -> Unit = {}
        source.connect(root, listener)
        source.disconnect(listener)
        assertEquals(0, source.size())
        assertTrue(root.state("test registrations").removal.isEmpty())
    }

    private class ThrowingRoot : Node<ThrowingRoot>("root") {
        override fun onExitTree() {
            error("exit failure")
        }
    }

    @Test fun `failed parent exit still runs child exit`() {
        val root = ThrowingRoot()
        scenes.currScene = root
        var childExited = false
        root.addChild(EmptyNode("child") { behavior(onExitTree = { childExited = true }) })
        root.queueFree()
        assertFailsWith<NodeCleanupException> { scenes.onUpdate(0f) }
        assertTrue(childExited)
    }
}
