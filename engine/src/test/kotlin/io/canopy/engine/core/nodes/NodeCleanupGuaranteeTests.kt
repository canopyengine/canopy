package io.canopy.engine.core.nodes

import kotlin.test.*
import io.canopy.engine.core.exceptions.*
import io.canopy.engine.core.flows.events.*
import io.canopy.engine.core.managers.*
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class NodeCleanupGuaranteeTests {
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

    @Test fun `bulk overlapping deletion removes one index entry per node even while paused`() {
        val root = EmptyNode("root")
        val retained = (0 until 1_000).map { EmptyNode("child-$it").also(root::addChild) }
        scenes.currScene = root
        scenes.pauseState = { true }
        var releases = 0
        retained.forEach {
            it.onDestroy { releases++ }
            it.queueFree()
        }
        root.queueFree()
        val before = scenes.indexRemovalCount
        scenes.onPhysicsUpdate(0f)
        assertEquals(1_001L, scenes.indexRemovalCount - before)
        assertEquals(0, scenes.indexedNodeCount)
        assertEquals(0, scenes.retainedStateCount)
        assertEquals(1_000, releases)
        retained.forEach { assertFalse(it.isValid) }
        scenes.onUpdate(0f)
        assertEquals(1_000, releases)
    }

    @Test fun `owned calculation releases dependencies on removal while shared calculation survives`() {
        val source = signal(owner = null, value = 1)
        lateinit var owned: Computed<Int>
        var ownedRuns = 0
        var sharedRuns = 0
        val shared = computed(owner = null) {
            sharedRuns++
            source() * 3
        }
        val root = EmptyNode("root") {
            owned = computed {
                ownedRuns++
                source() * 2
            }
            assertEquals(2, owned())
        }
        scenes.currScene = root
        assertEquals(3, shared())
        scenes.currScene = null
        source.update { 2 }
        assertEquals(1, ownedRuns)
        assertEquals(2, sharedRuns)
        assertEquals(6, shared())
        assertFailsWith<CanopyException> { owned() }
        owned.dispose()
        shared.dispose()
    }

    @Test fun `cancelled provider registration cannot remove a later shared replacement`() {
        val injections = InjectionManager()
        ManagersRegistry.register(injections)
        val node = EmptyNode("owner")
        injections.registerInjectable(String::class, node) { "owned" }
        injections.unregisterInjectable(String::class)
        injections.registerInjectable(String::class, null) { "shared" }
        node.queueFree()
        scenes.onUpdate(0f)
        assertEquals("shared", injections.inject(String::class))
    }

    @Test fun `cleanup rejects new registrations and still releases later exclusive resources`() {
        val node = EmptyNode("node")
        var released = 0
        node.onDestroy { node.onRemoval {} }
        node.onDestroy { released++ }
        node.queueFree()
        assertFailsWith<NodeDestroyedException> { scenes.onUpdate(0f) }
        assertEquals(1, released)
        assertFalse(node.isValid)
        assertEquals(0, scenes.retainedStateCount)
        scenes.onUpdate(0f)
        assertEquals(1, released)
    }

    @Test fun `owned job cancellation completes without timing sleeps`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val node = EmptyNode("worker")
        val job = launch(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
            try {
                entered.complete(Unit)
                awaitCancellation()
            } finally {
                completed.complete(Unit)
            }
        }
        node.onRemoval(job)
        job.start()
        entered.await()
        node.queueFree()
        scenes.onUpdate(0f)
        assertTrue(job.isCancelled)
        completed.await()
        job.join()
        assertFalse(job.isActive)
    }

    @Test fun `disconnect during emission prevents later callbacks in the captured snapshot`() {
        val source = event(owner = null)
        val node = EmptyNode("owner")
        var calls = 0
        lateinit var second: EventDisconnectHandler
        val first = source.connect(owner = null) { second.disconnect() }
        second = source.connect(node) { calls++ }
        source.emit()
        assertEquals(0, calls)
        assertTrue(node.state("test registrations").removal.isEmpty())
        first.disconnect()
    }
}
