package io.canopy.adapters.libgdx.input

import com.badlogic.gdx.Gdx
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputBindType

class GdxInputManager : InputManager() {

    private val previousPressed = mutableMapOf<InputBind, Boolean>()
    private val currentPressed = mutableMapOf<InputBind, Boolean>()

    fun beginFrame() {
        trackedBinds.forEach { bind ->
            val wasPressed = currentPressed[bind] ?: false
            previousPressed[bind] = wasPressed
            currentPressed[bind] = pollPressed(bind)
        }
    }

    override fun isPressed(bind: InputBind): Boolean = currentPressed[bind] ?: pollPressed(bind)

    override fun isJustPressed(bind: InputBind): Boolean {
        val now = currentPressed[bind] ?: pollPressed(bind)
        val before = previousPressed[bind] ?: false
        return now && !before
    }

    override fun isJustReleased(bind: InputBind): Boolean {
        val now = currentPressed[bind] ?: pollPressed(bind)
        val before = previousPressed[bind] ?: false
        return !now && before
    }

    private fun pollPressed(bind: InputBind): Boolean = when (bind.type) {
        InputBindType.Keyboard -> Gdx.input.isKeyPressed(bind.code)
        InputBindType.Mouse -> Gdx.input.isButtonPressed(bind.code)
    }
}
