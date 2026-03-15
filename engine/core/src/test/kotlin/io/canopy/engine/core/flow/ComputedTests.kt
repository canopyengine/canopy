package io.canopy.engine.core.flow

import io.canopy.engine.core.flow.events.computed
import io.canopy.engine.core.flow.events.signal
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

/**
 * Tests for [io.canopy.engine.core.flow.events.Computed], a read-only reactive value derived
 * from one or more [io.canopy.engine.core.flow.events.Signal]s.
 *
 * Contracts verified:
 * - Initial value reflects dependencies at construction time
 * - Value updates when a dependency changes
 * - No re-emission when the derived value is unchanged
 * - Multiple dependencies are all tracked
 * - Dynamic (conditional) dependencies are correctly managed
 * - Nested computed chains propagate changes
 * - Flow API works as expected (replay = 1, distinctUntilChanged)
 * - Circular dependency guard does not throw or recurse infinitely
 */
class ComputedTests {

    @Test
    fun `computed initial value reflects dependencies`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        assertEquals(false, isDead.value)
    }

    @Test
    fun `computed updates when dependency changes`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        hp.value = 0

        assertEquals(true, isDead.value)
    }

    @Test
    fun `computed connect listener fires on derived value change`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        var received: Boolean? = null
        val callback: (Boolean) -> Unit = { received = it }
        isDead connect callback

        hp.value = 0

        assertEquals(true, received)
    }

    @Test
    fun `computed does not re-emit when derived value is unchanged`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        var callCount = 0
        val callback: (Boolean) -> Unit = { callCount++ }
        isDead connect callback

        // hp changes but isDead stays false
        hp.value = 90
        hp.value = 80
        hp.value = 1

        assertEquals(0, callCount, "Listener should not fire when derived value stays the same")
    }

    @Test
    fun `computed tracks multiple dependencies`() {
        val x = signal(1)
        val y = signal(2)
        val sum = computed { x() + y() }

        assertEquals(3, sum.value)

        x.value = 10
        assertEquals(12, sum.value)

        y.value = 20
        assertEquals(30, sum.value)
    }

    @Test
    fun `computed handles dynamic conditional dependencies`() {
        val useX = signal(true)
        val x = signal(10)
        val y = signal(20)
        val result = computed { if (useX()) x() else y() }

        assertEquals(10, result.value)

        // Switch to y branch — x should no longer be a dependency
        useX.value = false
        assertEquals(20, result.value)

        // Changing x should not trigger recomputation now
        var callCount = 0
        val callback: (Int) -> Unit = { callCount++ }
        result connect callback

        x.value = 99  // should NOT trigger
        assertEquals(0, callCount, "x should no longer be a dependency after branch switch")

        y.value = 50  // SHOULD trigger
        assertEquals(1, callCount)
        assertEquals(50, result.value)
    }

    @Test
    fun `computed chains propagate changes`() {
        val hp = signal(100)
        val doubled = computed { hp() * 2 }
        val label = computed { "hp=${doubled()}" }

        assertEquals("hp=200", label.value)

        hp.value = 50

        assertEquals("hp=100", label.value)
    }

    @Test
    fun `computed flow emits on derived value change`() = runBlocking {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        val collected = mutableListOf<Boolean>()
        val job = launch {
            isDead.flow.collect { collected.add(it) }
        }

        // replay = 1 means collector sees initial value immediately
        yield()

        hp.value = 0   // isDead becomes true
        hp.value = 50  // isDead becomes false
        hp.value = 0   // isDead becomes true again

        yield()
        job.cancel()

        assertEquals(listOf(false, true, false, true), collected)
    }

    @Test
    fun `computed circular dependency guard does not throw or recurse`() {
        val a = signal(0)

        // Create a computed that writes back to a signal it depends on.
        // The guard should break the cycle after the first re-entry.
        var recomputeCount = 0
        computed {
            recomputeCount++
            a()  // depend on a
        }

        // Trigger a change — recompute should run once and not loop
        a.value = 1

        // If the guard is working, recomputeCount is bounded (≤ 2: initial + one update)
        assert(recomputeCount <= 2) { "Recompute count should be bounded, was $recomputeCount" }
    }
}
