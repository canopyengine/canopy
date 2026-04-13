package io.canopy.adapters.libgdx.input

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import io.canopy.engine.input.binds.InputBind
import io.mockk.every
import io.mockk.mockk

class GdxInputManagerTests {

    private val originalInput = Gdx.input

    @AfterTest
    fun cleanup() {
        Gdx.input = originalInput
    }

    @Test
    fun `keyboard binds delegate to Gdx key polling`() {
        val input = mockk<Input>()
        every { input.isKeyPressed(Input.Keys.SPACE) } returns true
        every { input.isKeyPressed(Input.Keys.FORWARD_DEL) } returns false
        every { input.isButtonPressed(any()) } returns false
        Gdx.input = input

        val manager = GdxInputManager()

        assertTrue(manager.isPressed(InputBind.SPACE))
        assertFalse(manager.isPressed(InputBind.DELETE))
    }

    @Test
    fun `mouse binds delegate to Gdx button polling`() {
        val input = mockk<Input>()
        every { input.isButtonPressed(Input.Buttons.RIGHT) } returns true
        every { input.isButtonPressed(Input.Buttons.MIDDLE) } returns false
        every { input.isKeyPressed(any()) } returns false
        Gdx.input = input

        val manager = GdxInputManager()

        assertTrue(manager.isPressed(InputBind.RIGHT_MOUSE))
        assertFalse(manager.isPressed(InputBind.MIDDLE_MOUSE))
    }
}
