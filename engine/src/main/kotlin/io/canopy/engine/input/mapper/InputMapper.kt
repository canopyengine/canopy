package io.canopy.engine.input.mapper

import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputBindType
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.asData
import io.canopy.engine.logging.logger

class InputMapper {

    private val logger = logger<InputMapper>()

    private val mappings: MutableMap<String, MutableList<InputBind>> = mutableMapOf()

    val actions get() = mappings.mapValues { it.value.toList() }

    init {
        clearMappings()
    }

    fun toData(): InputData = asData()

    fun loadData(data: InputData) {
        mappings.clear()
        mappings.putAll(
            data.mappings.associate { entry ->
                entry.name to entry.binds.toMutableList()
            }
        )
    }

    fun mapToAction(bind: InputBind): List<String> = mappings.entries
        .asSequence()
        .filter { bind in it.value }
        .map { it.key }
        .toList()

    fun clearMappings() {
        mappings.clear()
    }

    fun mapAction(action: String, newBinds: List<InputBind>, replace: Boolean = true) {
        logger.info {
            "Mapping action [$action] to: ${newBinds.joinToString { it.describe() }}"
        }

        val binds = mappings.getOrPut(action) { mutableListOf() }

        if (replace) binds.clear()

        binds += newBinds
    }

    fun unmapAction(action: String) {
        mappings.remove(action)
    }

    private fun InputBind.describe(): String = when (type) {
        InputBindType.Keyboard -> "keyboard($code)"
        InputBindType.Mouse -> "mouse($code)"
    }
}
