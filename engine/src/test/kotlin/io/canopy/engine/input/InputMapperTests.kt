package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.InputEntry

class InputMapperTests {
    private fun InputMapper.entries(): List<Pair<String, List<InputBind>>> = buildList {
        forEachAction { action, binds -> add(action to binds) }
    }

    @Test
    fun `public snapshots and supplied binding lists cannot change mapper storage`() {
        val mapper = InputMapper()
        val supplied = mutableListOf(InputBind.A, InputBind.D)
        mapper.mapActions("move" to supplied)
        val first = mapper.actions
        val second = mapper.actions
        supplied.clear()
        (first.getValue("move") as MutableList<InputBind>).clear()
        (first as MutableMap<String, List<InputBind>>).clear()

        assertEquals(mapOf("move" to listOf(InputBind.A, InputBind.D)), mapper.actions)
        assertEquals(mapOf("move" to listOf(InputBind.A, InputBind.D)), second)
        mapper.mapActions("move" to listOf(InputBind.W))
        assertEquals(mapOf("move" to listOf(InputBind.A, InputBind.D)), second)
        assertEquals(listOf("move" to listOf(InputBind.W)), mapper.entries())
    }

    @Test
    fun `failed data binding copies discard snapshots captured by source collection callbacks`() {
        val mapper = InputMapper()
        mapper.mapActions("move" to listOf(InputBind.A))
        mapper.entries()
        val failure = IllegalArgumentException("binding copy")
        val failingBindings = object : AbstractList<InputBind>() {
            override val size = 2
            override fun get(index: Int): InputBind {
                mapper.entries()
                if (index == 1) throw failure
                return InputBind.D
            }
        }

        assertSame(
            failure,
            assertFailsWith<IllegalArgumentException> {
                mapper.loadData(InputData(listOf(InputEntry("move", failingBindings))))
            }
        )
        assertTrue(mapper.actions.isEmpty())
        assertTrue(mapper.entries().isEmpty())
        mapper.mapActions("move" to listOf(InputBind.W))
        assertEquals(listOf("move" to listOf(InputBind.W)), mapper.entries())
    }

    @Test
    fun `loaded mappings replace prior iteration entries and copy input data in order`() {
        val mapper = InputMapper()
        mapper.mapActions("old" to listOf(InputBind.A))
        assertEquals(listOf("old" to listOf(InputBind.A)), mapper.entries())
        val supplied = mutableListOf(InputBind.D, InputBind.S)
        mapper.loadData(InputData(listOf(InputEntry("new", supplied), InputEntry("other", listOf(InputBind.W)))))
        supplied.clear()
        assertEquals(
            listOf("new" to listOf(InputBind.D, InputBind.S), "other" to listOf(InputBind.W)),
            mapper.entries()
        )
        mapper.loadData(InputData(emptyList()))
        assertTrue(mapper.entries().isEmpty())
    }
}
