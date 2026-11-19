package io.canopy.engine.core.flows

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import io.canopy.engine.core.flows.events.computed
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.flows.events.untrack
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests for [io.canopy.engine.core.flows.events.Computed].
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
    fun `initial computed read settles source writes before caching`() {
        val source = signal(0)
        var evaluations = 0
        val result = computed {
            val value = source()
            evaluations++
            if (value < 2) source.update { value + 1 }
            value
        }

        assertEquals(2, result())
        assertEquals(3, evaluations)
        assertEquals(2, result())
        assertEquals(3, evaluations)
        source.update { 3 }
        assertEquals(3, result())
        result.dispose()
    }

    @Test
    fun `computed updates when dependency changes`() {
        val hp = signal(100)
        val isDead = computed { hp() <= 0 }

        assertEquals(false, isDead())
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
    fun `same observable list instance propagates edits through computed chains`() {
        val source = signal(owner = null, value = mutableListOf(1))
        val selected = computed(owner = null) { source() }
        val forwarded = computed(owner = null) { selected() }
        val selectedValues = mutableListOf<List<Int>>()
        val forwardedValues = mutableListOf<List<Int>>()
        val firstConnection = selected.connect(owner = null) { selectedValues += it.toList() }
        val secondConnection = forwarded.connect(owner = null) { forwardedValues += it.toList() }

        source().add(2)

        assertEquals(listOf(listOf(1, 2)), selectedValues)
        assertEquals(listOf(listOf(1, 2)), forwardedValues)
        firstConnection.disconnect()
        secondConnection.disconnect()
        forwarded.dispose()
        selected.dispose()
        source.dispose()
    }

    @Test
    fun `same observable set and map instances emit only for real edits`() {
        val setSource = signal(owner = null, value = mutableSetOf(1))
        val mapSource = signal(owner = null, value = mutableMapOf("first" to 1))
        val selectedSet = computed(owner = null) { setSource() }
        val selectedMap = computed(owner = null) { mapSource() }
        var setChanges = 0
        var mapChanges = 0
        val setConnection = selectedSet.connect(owner = null) { setChanges++ }
        val mapConnection = selectedMap.connect(owner = null) { mapChanges++ }

        setSource().add(1)
        mapSource()["first"] = 1
        setSource().add(2)
        mapSource()["second"] = 2

        assertEquals(1, setChanges)
        assertEquals(1, mapChanges)
        setConnection.disconnect()
        mapConnection.disconnect()
        selectedSet.dispose()
        selectedMap.dispose()
        setSource.dispose()
        mapSource.dispose()
    }

    @Test
    fun `same arbitrary object does not emit on unrelated dependency changes`() {
        val source = signal(owner = null, value = 0)
        val value = Any()
        val selected = computed(owner = null) {
            source()
            value
        }
        var changes = 0
        val connection = selected.connect(owner = null) { changes++ }

        source.update { 1 }

        assertEquals(0, changes)
        connection.disconnect()
        selected.dispose()
        source.dispose()
    }

    @Test
    fun `new dynamic dependency writing during recomputation settles before publication`() {
        val useSecond = signal(owner = null, value = false)
        val first = signal(owner = null, value = 1)
        val second = signal(owner = null, value = 2)
        val selected = computed(owner = null) {
            if (useSecond()) {
                second().also { if (it == 2) second.update { 3 } }
            } else {
                first()
            }
        }
        val values = mutableListOf<Int>()
        val connection = selected.connect(owner = null) { values += it }

        useSecond.update { true }

        assertEquals(3, selected())
        assertEquals(listOf(3), values)
        connection.disconnect()
        selected.dispose()
        useSecond.dispose()
        first.dispose()
        second.dispose()
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
        var runs = 0
        val result = computed {
            runs++
            if (useX()) x() else y()
        }

        assertEquals(10, result())

        // Switch to y branch — x should no longer be a dependency
        useX.update { false }
        assertEquals(20, result())

        var callCount = 0
        val callback: (Int) -> Unit = { callCount++ }
        result connect callback

        x.update { 99 } // should NOT trigger
        assertEquals(0, callCount, "x should no longer be a dependency after branch switch")
        assertEquals(2, runs)

        y.update { 50 } // SHOULD trigger
        assertEquals(1, callCount)
        assertEquals(50, result())
        assertEquals(3, runs)
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
    fun `lazy nested computation retains new dependencies after a throwing recomputation`() {
        val branch = signal(true)
        val first = signal(1)
        val second = signal(2)
        val inner = computed { second() * 2 }
        val failure = IllegalStateException("computed")
        var runs = 0
        var fail = false
        val outer = computed {
            runs++
            val value = if (branch()) first() else inner()
            if (fail) throw failure
            value
        }
        assertEquals(0, runs)
        assertEquals(1, outer())
        fail = true
        assertSame(failure, assertFailsWith<IllegalStateException> { branch.update { false } })
        fail = false
        first.update { 10 }
        assertEquals(2, runs)
        second.update { 3 }
        assertEquals(3, runs)
        assertEquals(6, outer())
        outer.dispose()
        second.update { 4 }
        assertEquals(3, runs)
        inner.dispose()
    }
}
