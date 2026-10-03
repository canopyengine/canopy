package io.canopy.engine.input

import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.asData
import io.canopy.engine.logging.logger

/** Owns action-to-binding mappings and their serializable representation. Use on the engine thread. */
class InputMapper {
    private val logger = logger<InputMapper>()

    private val mappings: MutableMap<String, MutableList<InputBind>> = mutableMapOf()

    /** Returns a mapping copy with copied binding lists. */
    val actions: Map<String, List<InputBind>>
        get() = mappings.mapValues { it.value.toList() }

    init {
        clearMappings()
    }

    /** Creates a serializable snapshot of the mappings. */
    fun toData(): InputData = asData()

    /** Replaces all mappings with the supplied data, copying its binding lists. */
    fun loadData(data: InputData) {
        mappings.clear()
        mappings.putAll(
            data.mappings.associate { entry ->
                entry.name to entry.binds.toMutableList()
            }
        )
    }

    /** Removes every action mapping. */
    fun clearMappings() {
        mappings.clear()
    }

    /** Replaces or appends bindings for the supplied actions; other mappings are retained. */
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

    /** Removes the named action, if present. */
    fun unmapAction(action: String) {
        mappings.remove(action)
    }

    private fun InputBind.describe(): String = when (type) {
        InputBind.Type.Keyboard -> "keyboard(${name.lowercase()})"
        InputBind.Type.Mouse -> "mouse(${name.lowercase()})"
    }
}
