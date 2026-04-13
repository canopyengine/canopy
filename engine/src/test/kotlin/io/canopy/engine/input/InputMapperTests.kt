package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.InputEntry

class InputMapperTests {

    @Test
    fun `mapActions replaces existing binds by default`() {
        val mapper = InputMapper()

        mapper.mapActions("jump" to listOf(InputBind.SPACE))
        mapper.mapActions("jump" to listOf(InputBind.ENTER))

        assertEquals(listOf(InputBind.ENTER), mapper.mappings["jump"])
        assertEquals(listOf("jump"), mapper.mapToAction(InputBind.ENTER))
        assertTrue(mapper.mapToAction(InputBind.SPACE).isEmpty())
    }

    @Test
    fun `mapActions appends binds when replace is false`() {
        val mapper = InputMapper()

        mapper.mapActions("jump" to listOf(InputBind.SPACE))
        mapper.mapActions("jump" to listOf(InputBind.ENTER), replace = false)

        assertEquals(listOf(InputBind.SPACE, InputBind.ENTER), mapper.mappings["jump"])
    }

    @Test
    fun `mapToAction returns every action mapped to the bind`() {
        val mapper = InputMapper()

        mapper.mapActions(
            "jump" to listOf(InputBind.SPACE),
            "confirm" to listOf(InputBind.SPACE),
            "cancel" to listOf(InputBind.ESCAPE)
        )

        assertEquals(listOf("jump", "confirm"), mapper.mapToAction(InputBind.SPACE))
    }

    @Test
    fun `unmapAction and clearMappings remove mappings`() {
        val mapper = InputMapper()

        mapper.mapActions(
            "jump" to listOf(InputBind.SPACE),
            "cancel" to listOf(InputBind.ESCAPE)
        )

        mapper.unmapAction("jump")

        assertEquals(setOf("cancel"), mapper.mappings.keys)

        mapper.clearMappings()

        assertTrue(mapper.mappings.isEmpty())
    }

    @Test
    fun `toData and loadData roundtrip mappings`() {
        val mapper = InputMapper()
        mapper.mapActions(
            "left" to listOf(InputBind.A, InputBind.LEFT),
            "right" to listOf(InputBind.D, InputBind.RIGHT)
        )

        val restored = InputMapper()
        restored.loadData(mapper.toData())

        assertEquals(mapper.mappings, restored.mappings)
    }

    @Test
    fun `loadData clears existing mappings before applying data`() {
        val mapper = InputMapper()
        mapper.mapActions("old" to listOf(InputBind.SPACE))

        mapper.loadData(InputData(listOf(InputEntry("new", listOf(InputBind.ENTER)))))

        assertEquals(setOf("new"), mapper.mappings.keys)
        assertEquals(listOf(InputBind.ENTER), mapper.mappings["new"])
    }
}
