package io.canopy.engine.input

import io.canopy.engine.core.managers.Manager
import io.canopy.engine.data.saving.registerSaveModule
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.mapper.InputMapper

abstract class InputManager : Manager {
    val mapper: InputMapper = InputMapper()

    protected val trackedBinds get() = mapper.actions.values.flatten().toSet()

    abstract fun isPressed(bind: InputBind): Boolean
    abstract fun isJustPressed(bind: InputBind): Boolean
    abstract fun isJustReleased(bind: InputBind): Boolean
    fun isReleased(bind: InputBind): Boolean = !isPressed(bind)

    fun registerPersistence(destination: String = "input", moduleId: String = "input") {
        registerSaveModule(
            destination = destination,
            id = moduleId,
            serializer = InputData.serializer(),
            onSave = { mapper.toData() },
            onLoad = mapper::loadData
        )
    }
}
