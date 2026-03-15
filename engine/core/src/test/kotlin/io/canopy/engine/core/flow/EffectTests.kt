package io.canopy.engine.core.flow

import io.canopy.engine.core.flow.events.effect
import io.canopy.engine.core.flow.events.signal
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

/**
 * Tests for [io.canopy.engine.core.flow.events.Effect], a reactive side-effect
 * that re-runs when its signal dependencies change.
 *
 * Contracts verified:
 * - Block runs immediately on construction
 * - Block re-runs when a dependency changes
 * - Block does not re-run after dispose
 * - dispose is idempotent (safe to call multiple times)
 * - Multiple dependencies are all tracked
 * - Dynamic (conditional) dependencies are correctly managed
 * - Multiple independent effects on the same signal don't interfere
 */
class EffectTests {

    @Test
    fun `effect runs immediately on creation`() {
        val hp = signal(100)

        var runCount = 0
        val e = effect {
            hp()  // declare dependency
            runCount++
        }

        assertEquals(1, runCount, "Effect should run once immediately")
        e.dispose()
    }

    @Test
    fun `effect re-runs when dependency changes`() {
        val hp = signal(100)

        var runCount = 0
        val e = effect {
            hp()
            runCount++
        }

        hp.value = 50
        hp.value = 0

        assertEquals(3, runCount, "Effect should have run once initially plus once per change")
        e.dispose()
    }

    @Test
    fun `effect does not re-run after dispose`() {
        val hp = signal(100)

        var runCount = 0
        val e = effect {
            hp()
            runCount++
        }

        e.dispose()

        hp.value = 0
        hp.value = 50

        assertEquals(1, runCount, "Effect should not run after dispose")
    }

    @Test
    fun `effect dispose is idempotent`() {
        val hp = signal(100)
        val e = effect { hp() }

        // Should not throw
        e.dispose()
        e.dispose()
        e.dispose()
    }

    @Test
    fun `effect tracks multiple dependencies`() {
        val x = signal(1)
        val y = signal(2)

        var lastSum = 0
        val e = effect { lastSum = x() + y() }

        assertEquals(3, lastSum)

        x.value = 10
        assertEquals(12, lastSum)

        y.value = 20
        assertEquals(30, lastSum)

        e.dispose()
    }

    @Test
    fun `effect handles dynamic conditional dependencies`() {
        val useX = signal(true)
        val x = signal(10)
        val y = signal(20)

        var lastValue = 0
        val e = effect { lastValue = if (useX()) x() else y() }

        assertEquals(10, lastValue)

        // Switch branch — x should no longer be a dependency
        useX.value = false
        assertEquals(20, lastValue)

        var runAfterSwitch = 0
        val watcher = effect {
            useX()  // keep a ref to track changes
            runAfterSwitch++
        }

        // Changing x should NOT re-run e now
        val prevValue = lastValue
        x.value = 99
        assertEquals(prevValue, lastValue, "x should not trigger effect after branch switch")

        // Changing y SHOULD re-run e
        y.value = 55
        assertEquals(55, lastValue)

        e.dispose()
        watcher.dispose()
    }

    @Test
    fun `multiple effects on same signal are independent`() {
        val counter = signal(0)

        var countA = 0
        var countB = 0

        val eA = effect { counter(); countA++ }
        val eB = effect { counter(); countB++ }

        counter.value = 1
        counter.value = 2

        assertEquals(3, countA)
        assertEquals(3, countB)

        // Disposing one should not affect the other
        eA.dispose()
        counter.value = 3

        assertEquals(3, countA, "eA should not run after dispose")
        assertEquals(4, countB, "eB should still run")

        eB.dispose()
    }
}
