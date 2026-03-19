package io.canopy.engine.core.flow

import kotlin.test.Test
import io.canopy.engine.core.flow.events.computed
import io.canopy.engine.core.flow.events.signal
import io.canopy.engine.core.flow.events.untrack
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests for [io.canopy.engine.core.flow.events.Computed].
 *
 * Signals are written via `signal.update { }` and read via `signal()`.
 * Computed values are read via `computed()` or `computed()`.
 */
class ComputedTests {

    @Test
    fun `computed initial value reflects dependencies`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        assertEquals(false, isDead())
    }

    @Test
    fun `computed updates when dependency changes`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        hp.update { 0 }

        assertEquals(true, isDead())
    }

    @Test
    fun `computed connect listener fires on derived value change`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        var received: Boolean? = null
        val callback: (Boolean) -> Unit = { received = it }
        isDead connect callback

        hp.update { 0 }

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
        hp.update { 90 }
        hp.update { 80 }
        hp.update { 1 }

        assertEquals(0, callCount, "Listener should not fire when derived value stays the same")
    }

    @Test
    fun `computed tracks multiple dependencies`() {
        val x = signal(1)
        val y = signal(2)
        val sum = computed { x() + y() }

        assertEquals(3, sum())

        x.update { 10 }
        assertEquals(12, sum())

        y.update { 20 }
        assertEquals(30, sum())
    }

    @Test
    fun `computed handles dynamic conditional dependencies`() {
        val useX = signal(true)
        val x = signal(10)
        val y = signal(20)
        val result = computed { if (useX()) x() else y() }

        assertEquals(10, result())

        // Switch to y branch — x should no longer be a dependency
        useX.update { false }
        assertEquals(20, result())

        var callCount = 0
        val callback: (Int) -> Unit = { callCount++ }
        result connect callback

        x.update { 99 } // should NOT trigger
        assertEquals(0, callCount, "x should no longer be a dependency after branch switch")

        y.update { 50 } // SHOULD trigger
        assertEquals(1, callCount)
        assertEquals(50, result())
    }

    @Test
    fun `computed chains propagate changes`() {
        val hp = signal(100)
        val doubled = computed { hp() * 2 }
        val label = computed { "hp=${doubled()}" }

        assertEquals("hp=200", label())

        hp.update { 50 }

        assertEquals("hp=100", label())
    }

    @Test
    fun `computed flow emits on derived value change`() = runBlocking {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        val collected = mutableListOf<Boolean>()
        val job = launch {
            isDead.flow.collect { collected.add(it) }
        }

        yield()

        hp.update { 0 } // isDead becomes true
        hp.update { 50 } // isDead becomes false
        hp.update { 0 } // isDead becomes true again

        yield()
        job.cancel()

        assertEquals(listOf(false, true, false, true), collected)
    }

    @Test
    fun `untrack prevents signal from becoming a dependency`() {
        val base = signal(10)
        val multiplier = signal(2)
        // base is read via untrack — changes to base should NOT recompute
        val result = computed { untrack { base() } * multiplier() }

        assertEquals(20, result())

        var callCount = 0
        val callback: (Int) -> Unit = { callCount++ }
        result connect callback

        base.update { 99 } // should NOT trigger recompute
        assertEquals(0, callCount, "base is untracked — should not trigger recompute")
        assertEquals(20, result())

        multiplier.update { 3 } // SHOULD trigger; reads the current base value (99)
        assertEquals(1, callCount)
        assertEquals(99 * 3, result())
    }

    @Test
    fun `computed circular dependency guard does not throw or recurse`() {
        val a = signal(0)

        var recomputeCount = 0
        computed {
            recomputeCount++
            a()
        }

        a.update { 1 }

        assert(recomputeCount <= 2) { "Recompute count should be bounded, was $recomputeCount" }
    }
}
