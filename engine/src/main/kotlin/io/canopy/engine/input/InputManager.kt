package io.canopy.engine.input

import io.canopy.engine.core.managers.Manager
import io.canopy.engine.data.saving.registerSaveModule
import io.canopy.engine.input.InputMapper
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.math.Vector2

abstract class InputManager : Manager {

    private val mapper = InputMapper()

    private val mutableActionStates = mutableMapOf<String, InputState>()
    val actionStates: Map<String, InputState>
        get() = mutableActionStates

    /**
     * Backend-specific raw polling.
     */
    protected abstract fun pollPressed(bind: InputBind): Boolean

    /**
     * Recomputes all mapped action states for the current frame.
     */
    fun updateActions() {
        mapper.mappings.forEach { (action, binds) ->
            val rawPressed = binds.any(::pollPressed)
            val previousState = getActionState(action)

            val nextState = getNextState(
                previousState = previousState,
                rawState = if (rawPressed) InputState.Pressed else InputState.Released
            )

            mutableActionStates[action] = nextState
        }
    }

    fun getActionState(action: String): InputState = mutableActionStates[action] ?: InputState.Released

    fun isActionPressed(action: String): Boolean {
        val state = getActionState(action)
        return state == InputState.Pressed || state == InputState.JustPressed
    }

    fun isActionJustPressed(action: String): Boolean = getActionState(action) == InputState.JustPressed

    fun isActionJustReleased(action: String): Boolean = getActionState(action) == InputState.JustReleased

    fun isActionReleased(action: String): Boolean {
        val state = getActionState(action)
        return state == InputState.Released || state == InputState.JustReleased
    }

    fun isPressed(bind: InputBind): Boolean = pollPressed(bind)

    fun getAxis(negativeAction: String, positiveAction: String): Float {
        val negativePressed = isActionPressed(negativeAction)
        val positivePressed = isActionPressed(positiveAction)

        return when {
            positivePressed && !negativePressed -> 1f
            negativePressed && !positivePressed -> -1f
            else -> 0f
        }
    }

    fun getInputVector(negativeX: String, positiveX: String, negativeY: String, positiveY: String): Vector2 = Vector2(
        getAxis(negativeX, positiveX),
        getAxis(negativeY, positiveY)
    )

    fun mapActions(vararg actions: Pair<String, List<InputBind>>, replace: Boolean = true) {
        mapper.mapActions(*actions, replace = replace)

        if (replace) mutableActionStates.clear()

        actions.forEach { (action, _) ->
            mutableActionStates[action] = InputState.Released
        }
    }

    operator fun Pair<String, List<InputBind>>.unaryPlus() {
        mapActions(this)
    }

    fun unmapAction(action: String) {
        mapper.unmapAction(action)
        mutableActionStates.remove(action)
    }

    fun clearMappings() {
        mapper.clearMappings()
        mutableActionStates.clear()
    }

    fun registerPersistence(destination: String = "input", moduleId: String = "input") {
        registerSaveModule(
            destination = destination,
            id = moduleId,
            serializer = InputData.serializer(),
            onSave = { mapper.toData() },
            onLoad = {
                mapper.loadData(it)
                mutableActionStates.clear()
                mapper.mappings.keys.forEach { action ->
                    mutableActionStates[action] = InputState.Released
                }
            }
        )
    }

    private fun getNextState(previousState: InputState, rawState: InputState): InputState {
        val previousWasPressed =
            previousState == InputState.Pressed || previousState == InputState.JustPressed

        val previousWasReleased =
            previousState == InputState.Released || previousState == InputState.JustReleased

        return when (rawState) {
            InputState.Pressed ->
                if (previousWasReleased) InputState.JustPressed else InputState.Pressed

            InputState.Released ->
                if (previousWasPressed) InputState.JustReleased else InputState.Released

            else -> InputState.Released
        }
    }
}
