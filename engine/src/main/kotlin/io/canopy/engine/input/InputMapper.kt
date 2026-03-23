package io.canopy.engine.input

import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.asData
import io.canopy.engine.logging.logger

class InputMapper {

    private val logger = logger<InputMapper>()

    private val mappings: MutableMap<String, MutableList<InputBind>> = mutableMapOf()

    val actions: Map<String, List<InputBind>>
        get() = mappings.mapValues { it.value.toList() }

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

    fun mapActions(vararg newMappings: Pair<String, List<InputBind>>, replace: Boolean = true) {
        newMappings.forEach { (action, newBinds) ->
            logger.info {
                "Mapping action [$action] to: ${newBinds.joinToString { it.describe() }}"
            }

            val binds = mappings.getOrPut(action) { mutableListOf() }

            if (replace) binds.clear()

            binds += newBinds
        }
    }

    fun unmapAction(action: String) {
        mappings.remove(action)
    }

    private fun InputBind.describe(): String = when (type) {
        InputBind.Type.Keyboard -> "keyboard(${name.lowercase()})"
        InputBind.Type.Mouse -> "mouse(${name.lowercase()})"
    }
}
