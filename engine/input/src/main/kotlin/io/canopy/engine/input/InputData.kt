package io.canopy.engine.input

import io.canopy.engine.data.core.registry.IdEntry
import kotlinx.serialization.Serializable

@Serializable
class InputData(val mappings: List<InputEntry>)

@Serializable
class InputEntry(override val name: String, val binds: List<InputBind>) : IdEntry {
    override val domain = "input"
}

fun InputMapper.asData(): InputData {
    val entries: List<InputEntry> = mappings.map { (action, binds) -> InputEntry(action, binds) }
    return InputData(entries)
}
