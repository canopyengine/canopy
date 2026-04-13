package io.canopy.engine.input

import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.asData
import io.canopy.engine.logging.logger

class InputMapper {

    private val logger = logger<InputMapper>()

    private val mutableMappings = mutableMapOf<String, MutableList<InputBind>>()
    val mappings: Map<String, List<InputBind>>
        get() = mutableMappings

    init {
        clearMappings()
    }

    fun toData(): InputData = asData()

    fun loadData(data: InputData) {
        mutableMappings.clear()
        mutableMappings.putAll(
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
        mutableMappings.clear()
    }

    fun mapActions(vararg newMappings: Pair<String, List<InputBind>>, replace: Boolean = true) {
        newMappings.forEach { (action, newBinds) ->
            logger.info {
                "Mapping action [$action] to: ${newBinds.joinToString { it.describe() }}"
            }

            val binds = mutableMappings.getOrPut(action) { mutableListOf() }

            if (replace) binds.clear()

            binds += newBinds
        }
    }

    fun unmapAction(action: String) {
        mutableMappings.remove(action)
    }

    private fun InputBind.describe(): String = when (type) {
        InputBind.Type.Keyboard -> "keyboard(${name.lowercase()})"
        InputBind.Type.Mouse -> "mouse(${name.lowercase()})"
    }
}
