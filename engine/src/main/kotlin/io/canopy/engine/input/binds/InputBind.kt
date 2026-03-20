package io.canopy.engine.input.binds

import kotlinx.serialization.Serializable

@Serializable
data class InputBind(val type: InputBindType, val code: Int) {
    companion object {
        fun keyboardBind(code: Int) = InputBind(InputBindType.Keyboard, code)
        fun mouseBind(code: Int) = InputBind(InputBindType.Mouse, code)
    }
}

@Serializable
enum class InputBindType {
    Keyboard,
    Mouse,
}
