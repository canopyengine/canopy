package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.flows.events.computed
import io.canopy.engine.core.flows.events.effect
import io.canopy.engine.core.flows.events.event
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.flows.fromContextOrNull
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.InputSystem
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.TextInputEvent
import io.canopy.tooling.utils.UnstableApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodeLifetimeTests {
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

    private class Matches : TreeSystem(UpdatePhase.FramePost, 0, Node::class) {
        val removed = mutableListOf<Node<*>>()
        val processed = mutableListOf<Node<*>>()
        val nodes get() = matchingNodes.toList()
        var removal: (Node<*>) -> Unit = {}
        override fun onNodeRemoved(node: Node<*>) {
            removed += node
            removal(node)
        }
        override fun processNode(node: Node<*>, delta: Float) {
            processed += node
        }
    }

    @Test
    fun `destruction waits for all frame phases and removes subtree once`() {
        // Arrange
        val system = Matches()
        scenes.addSystem(system)
        val exits = mutableListOf<String>()
        val root = EmptyNode("root") {
            EmptyNode("branch") {
                behavior(onUpdate = {
                    queueFree()
                    queueFree()
                }, onExitTree = { exits += name })
                EmptyNode("leaf") { behavior(onExitTree = { exits += name }) }
            }
            EmptyNode("sibling")
        }
        scenes.currScene = root
        val branch = root.getNode<EmptyNode>("branch")
        val leaf = branch.getNode<EmptyNode>("leaf")

        // Act
        scenes.onUpdate(0f)

        // Assert
        assertEquals(listOf("leaf", "branch"), exits)
        assertTrue(branch.isFreed)
        assertFalse(branch.isQueuedForDeletion)
        assertNull(branch.parent)
        assertSame(branch, leaf.parent)
        assertTrue(branch in system.processed)
        assertTrue(leaf in system.processed)
        assertEquals(listOf<Node<*>>(leaf, branch), system.removed)
        assertEquals(listOf<Node<*>>(root, root.children.getValue("sibling")), system.nodes)
        branch.queueFree()
        scenes.onUpdate(0f)
        assertEquals(2, system.removed.size)
        assertFailsWith<IllegalStateException> { root.addChild(branch) }
    }

    @Test
    fun `physics destruction supports active roots and overlapping requests`() {
        // Arrange
        val root = EmptyNode("root") { EmptyNode("child") }
        scenes.currScene = root
        val child = root.getNode<EmptyNode>("child")
        var replaced = 0
        val subscription = scenes.onSceneReplaced.connect { replaced++ }
        root.queueFree()
        child.queueFree()
        assertSame(root, scenes.currScene)
        assertTrue(root.isQueuedForDeletion)

        // Act
        scenes.onPhysicsUpdate(0f)

        // Assert
        assertNull(scenes.currScene)
        assertTrue(root.isFreed)
        assertTrue(child.isFreed)
        assertEquals(1, replaced)
        subscription.disconnect()
    }

    @Test
    fun `nested updates defer destruction until the outer traversal ends`() {
        var nested = false
        val root = EmptyNode("root") {
            behavior(onUpdate = {
                if (!nested) {
                    nested = true
                    queueFree()
                    scenes.onUpdate(0f)
                    assertSame(this, scenes.currScene)
                    assertFalse(isFreed)
                }
            })
        }
        scenes.currScene = root
        scenes.onUpdate(0f)
        assertTrue(root.isFreed)
        assertNull(scenes.currScene)
    }

    @Test
    fun `detached nodes drain without an active scene`() {
        val node = EmptyNode("detached")
        var cleaned = 0
        node.onRemoval { cleaned++ }
        node.queueFree()
        assertEquals(0, cleaned)
        scenes.onUpdate(0f)
        assertEquals(1, cleaned)
        assertTrue(node.isFreed)
    }

    @Test
    fun `update failures preserve original error while destruction completes`() {
        // Arrange
        val updateFailure = IllegalStateException("update")
        val exitFailure = IllegalArgumentException("exit")
        val removalFailure = UnsupportedOperationException("system")
        val system = Matches().also { it.removal = { throw removalFailure } }
        scenes.addSystem(system)
        val root = EmptyNode("root") {
            behavior(onUpdate = {
                queueFree()
                throw updateFailure
            }, onExitTree = { throw exitFailure })
            EmptyNode("child")
        }
        scenes.currScene = root
        var cleaned = false
        root.onRemoval { cleaned = true }

        // Act
        val failure = assertFailsWith<IllegalStateException> { scenes.onUpdate(0f) }

        // Assert
        assertSame(updateFailure, failure)
        assertTrue(removalFailure in failure.suppressed)
        assertTrue(exitFailure in removalFailure.suppressed)
        assertTrue(cleaned)
        assertTrue(system.nodes.isEmpty())
        assertNull(scenes.currScene)
    }

    @Test
    fun `immediate removal cleans resources preserves subtree and exits once`() {
        // Arrange
        val source = event<Int>()
        val state = signal(0)
        var eventCalls = 0
        var signalCalls = 0
        var effectCalls = 0
        val exits = mutableListOf<String>()
        val root = EmptyNode("root") {
            EmptyNode("child") {
                source.connect { eventCalls++ }
                state.connect { signalCalls++ }
                effect {
                    state()
                    effectCalls++
                }
                behavior(onExitTree = { exits += name })
                EmptyNode("leaf") { behavior(onExitTree = { exits += name }) }
            }
        }
        scenes.currScene = root
        val child = root.getNode<EmptyNode>("child")
        source.emit(1)
        state.update { 1 }
        assertEquals(1, eventCalls)
        assertEquals(1, signalCalls)
        assertEquals(2, effectCalls)

        // Act
        root.removeChild(child)
        source.emit(2)
        state.update { 2 }

        // Assert
        assertEquals(listOf("leaf", "child"), exits)
        assertEquals(1, child.children.size)
        assertFalse(child.isFreed)
        assertEquals(0, source.size())
        assertEquals(1, eventCalls)
        assertEquals(1, signalCalls)
        assertEquals(2, effectCalls)
    }

    @Test
    fun `runtime and explicit subscriptions clear on replacement and shutdown`() {
        val source = event()
        val state = signal(0)
        var calls = 0
        val root = EmptyNode("root") {
            behavior(onUpdate = { source.connect { calls++ } })
        }
        scenes.currScene = root
        scenes.onUpdate(0f)
        source.connect(root) { calls++ }
        state.connect(root) { calls++ }
        effect(root) {
            state()
            calls++
        }
        source.emit()
        assertEquals(3, calls)
        scenes.currScene = EmptyNode("next")
        source.emit()
        state.update { 1 }
        assertEquals(3, calls)
        assertEquals(0, source.size())
        source.connect(scenes.currScene!!) { calls++ }
        scenes.onExit()
        source.emit()
        assertEquals(3, calls)
    }

    @Test
    fun `reparent retains descendants providers subscriptions and lifecycle`() {
        val source = event()
        var calls = 0
        var exits = 0
        val root = EmptyNode("root") {
            EmptyNode("first") {
                Context("scope") {
                    provide("value") { "retained" }
                    source.connect { calls++ }
                    EmptyNode("leaf") { behavior(onExitTree = { exits++ }) }
                }
            }
            EmptyNode("second")
        }
        scenes.currScene = root
        val first = root.getNode<EmptyNode>("first")
        val second = root.getNode<EmptyNode>("second")
        val context = first.children.getValue("scope") as Context
        val leaf = context.getNode<EmptyNode>("leaf")
        first.reparent(context, second)
        source.emit()
        assertSame(context, leaf.parent)
        assertEquals("/root/second/scope/leaf", leaf.path)
        assertEquals("retained", leaf.fromContextOrNull<String>("value"))
        assertEquals(1, calls)
        assertEquals(0, exits)
        second.removeChild(context)
        assertEquals("retained", context.fromContextOrNull<String>("value"))
        source.emit()
        assertEquals(1, calls)
        assertEquals(1, exits)
        context.queueFree()
        scenes.onUpdate(0f)
        assertNull(context.fromContextOrNull<String>("value"))
    }

    @Test
    fun `manager reentry recreates behavior resources without rebuilding children`() {
        val source = event()
        var entries = 0
        var calls = 0
        val root = EmptyNode("root") {
            EmptyNode("child")
            behavior(onEnterTree = {
                entries++
                source.connect { calls++ }
            })
        }
        scenes.currScene = root
        source.emit()
        scenes.onExit()
        source.emit()
        assertEquals(1, calls)
        scenes.onEnter()
        source.emit()
        assertEquals(2, calls)
        assertEquals(2, entries)
        assertEquals(1, root.children.size)
        scenes.currScene = root
        assertEquals(2, entries)
    }

    @Test
    fun `shutdown exits nodes before releasing initialized systems`() {
        val calls = mutableListOf<String>()
        class LifecycleSystem : TreeSystem(UpdatePhase.FramePre, 0, Node::class) {
            var active = false
            val nodes get() = matchingNodes.toList()
            override fun onRegister() {
                active = true
            }
            override fun onNodeRemoved(node: Node<*>) {
                calls += "remove:${node.name}"
            }
            override fun onUnregister() {
                active = false
                calls += "unregister"
            }
        }
        val system = LifecycleSystem()
        scenes.addSystem(system)
        val root = EmptyNode("root") {
            EmptyNode("child")
            behavior(onExitTree = {
                assertTrue(system.active)
                assertTrue(this in system.nodes)
                calls += "exit:root"
            })
        }
        scenes.currScene = root
        scenes.onExit()
        assertEquals(listOf("remove:child", "exit:root", "remove:root", "unregister"), calls)
        assertFalse(system.active)
    }

    @Test
    fun `contexts survive reusable detachment and manager reentry`() {
        val root = EmptyNode("root") { Context("scope") { provide("value") { 42 } } }
        scenes.currScene = root
        val context = root.children.getValue("scope") as Context
        root.removeChild(context)
        root.addChild(context)
        assertEquals(42, context.fromContextOrNull<Int>("value"))
        scenes.onExit()
        scenes.onEnter()
        assertEquals(42, context.fromContextOrNull<Int>("value"))
    }

    @Test
    fun `freed scene replacement preserves the active scene`() {
        val freed = EmptyNode("freed")
        freed.queueFree()
        scenes.onUpdate(0f)
        val active = EmptyNode("active")
        scenes.currScene = active
        assertFailsWith<IllegalArgumentException> { scenes.currScene = freed }
        assertSame(active, scenes.currScene)
    }

    @Test
    fun `custom node update owns connections in managed dispatch`() {
        val source = event()
        var calls = 0
        class Subscriber : Node<Subscriber>("subscriber") {
            override fun nodeUpdate(delta: Float) {
                source.connect { calls++ }
                super.nodeUpdate(delta)
            }
        }
        val root = Subscriber()
        scenes.currScene = root
        scenes.onUpdate(0f)
        source.emit()
        assertEquals(1, calls)
        scenes.currScene = null
        assertEquals(0, source.size())
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `custom root input owns connections during input system dispatch`() {
        val source = event()
        val input = object : InputManager() {
            override fun pollPressed(bind: InputBind) = false
        }
        ManagersRegistry.register(input)
        scenes.addSystem(InputSystem())
        class Subscriber : Node<Subscriber>("subscriber") {
            override fun nodeInput(event: InputEvent) {
                source.connect {}
                super.nodeInput(event)
            }
        }
        scenes.currScene = Subscriber()
        input.enqueue(TextInputEvent("text"))
        input.processEvents()
        scenes.onUpdate(0f)
        assertEquals(1, source.size())
        scenes.currScene = null
        assertEquals(0, source.size())
    }

    @Test
    fun `custom failing exit still releases owned resources`() {
        val source = event()
        val exitFailure = IllegalStateException("custom exit")
        class Subscriber : Node<Subscriber>("subscriber") {
            override fun nodeInit() {
                source.connect {}
                Context("scope") { provide("value") { 42 } }
            }
            override fun nodeExitTree(): Unit = throw exitFailure
        }
        val root = Subscriber()
        scenes.currScene = root
        val context = root.children.getValue("scope") as Context
        root.queueFree()
        assertSame(exitFailure, assertFailsWith<IllegalStateException> { scenes.onUpdate(0f) })
        assertEquals(0, source.size())
        assertNull(context.fromContextOrNull<Int>("value"))
        assertNull(scenes.currScene)
    }

    @Test
    fun `computed dependencies survive removal of their first reader`() {
        val state = signal(1)
        val derived = computed { state() * 2 }
        val root = EmptyNode("root") { assertEquals(2, derived()) }
        scenes.currScene = root
        scenes.currScene = null
        state.update { 2 }
        assertEquals(4, derived())
    }

    @Test
    fun `manually registered system matches release on node removal`() {
        val root = EmptyNode("root") { EmptyNode("child") }
        scenes.currScene = root
        val child = root.getNode<EmptyNode>("child")
        val system = Matches()
        system.register(child)
        root.removeChild(child)
        assertTrue(system.nodes.isEmpty())
        assertEquals(listOf<Node<*>>(child), system.removed)
    }

    @Test
    fun `cleanup cancellation and new requests during cleanup are drained`() {
        val first = EmptyNode("first")
        val second = EmptyNode("second")
        var cancelledCalls = 0
        val callback: () -> Unit = { cancelledCalls++ }
        val cancel = first.onRemoval(callback)
        first.onRemoval(callback)
        cancel()
        first.onRemoval { second.queueFree() }
        first.queueFree()
        scenes.onUpdate(0f)
        assertEquals(1, cancelledCalls)
        assertTrue(second.isFreed)
    }
}
