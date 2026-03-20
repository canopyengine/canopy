package io.canopy.engine.input.mapper

import io.canopy.engine.data.saving.registerSaveModule
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.mapper.InputMapper

/**
 * Registers input mapping persistence in the save system.
 *
 * This is intentionally separate from [InputMapper] so the mapper itself
 * remains a pure runtime data structure without side effects.
 */
fun InputMapper.registerPersistence(destination: String = "input", moduleId: String = "input") {
    registerSaveModule(
        destination = destination,
        id = moduleId,
        serializer = InputData.serializer(),
        onSave = { toData() },
        onLoad = ::loadData
    )
}
