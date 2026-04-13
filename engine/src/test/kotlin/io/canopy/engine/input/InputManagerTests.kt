package io.canopy.engine.input

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.data.assets.WritableAssetEntry
import io.canopy.engine.data.saving.InMemoryAssetEntry
import io.canopy.engine.data.saving.SaveManager
import io.canopy.engine.input.binds.InputBind

class InputManagerTests {

    private class TestInputManager : InputManager() {
        private val pressed = mutableSetOf<InputBind>()

        fun press(vararg binds: InputBind) {
            pressed += binds
        }

        fun release(vararg binds: InputBind) {
            pressed -= binds.toSet()
        }

        override fun pollPressed(bind: InputBind): Boolean = bind in pressed
    }

    @AfterTest
    fun cleanup() {
        ManagersRegistry.teardown()
    }

    @Test
    fun `updateActions advances button state across frames`() {
        val input = TestInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE))

        assertEquals(InputState.Released, input.getActionState("jump"))

        input.press(InputBind.SPACE)
        input.updateActions()
        assertEquals(InputState.JustPressed, input.getActionState("jump"))
        assertTrue(input.isActionPressed("jump"))
        assertTrue(input.isActionJustPressed("jump"))

        input.updateActions()
        assertEquals(InputState.Pressed, input.getActionState("jump"))

        input.release(InputBind.SPACE)
        input.updateActions()
        assertEquals(InputState.JustReleased, input.getActionState("jump"))
        assertTrue(input.isActionReleased("jump"))
        assertTrue(input.isActionJustReleased("jump"))

        input.updateActions()
        assertEquals(InputState.Released, input.getActionState("jump"))
    }

    @Test
    fun `multiple binds press an action when any bind is pressed`() {
        val input = TestInputManager()
        input.mapActions("left" to listOf(InputBind.A, InputBind.LEFT))

        input.press(InputBind.LEFT)
        input.updateActions()

        assertEquals(InputState.JustPressed, input.getActionState("left"))
        assertTrue(input.isPressed(InputBind.LEFT))
        assertFalse(input.isPressed(InputBind.A))
    }

    @Test
    fun `axis and input vector combine action states`() {
        val input = TestInputManager()
        input.mapActions(
            "left" to listOf(InputBind.A),
            "right" to listOf(InputBind.D),
            "down" to listOf(InputBind.S),
            "up" to listOf(InputBind.W)
        )

        input.press(InputBind.D, InputBind.W)
        input.updateActions()

        assertEquals(1f, input.getAxis("left", "right"))
        assertEquals(1f, input.getAxis("down", "up"))
        assertEquals(1f, input.getInputVector("left", "right", "down", "up").x)
        assertEquals(1f, input.getInputVector("left", "right", "down", "up").y)

        input.press(InputBind.A)
        input.updateActions()

        assertEquals(0f, input.getAxis("left", "right"))
    }

    @Test
    fun `unmapAction and clearMappings reset action states`() {
        val input = TestInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE), "cancel" to listOf(InputBind.ESCAPE))

        input.unmapAction("jump")

        assertEquals(InputState.Released, input.getActionState("jump"))
        assertEquals(setOf("cancel"), input.actionStates.keys)

        input.clearMappings()

        assertTrue(input.actionStates.isEmpty())
    }

    @Test
    fun `registerPersistence saves and loads mappings with released states`() {
        val entries = mutableMapOf<Int, InMemoryAssetEntry>()
        fun entryForSlot(slot: Int): WritableAssetEntry =
            entries.getOrPut(slot) { InMemoryAssetEntry("input-$slot.json") }

        val saveManager = SaveManager("input" to ::entryForSlot)
        ManagersRegistry.withScope {
            register(saveManager)
        }

        val input = TestInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE))
        input.registerPersistence()

        saveManager.save("input", 0)
        input.clearMappings()
        saveManager.load("input", 0)

        assertEquals(setOf("jump"), input.actionStates.keys)
        assertEquals(InputState.Released, input.getActionState("jump"))
    }
}
