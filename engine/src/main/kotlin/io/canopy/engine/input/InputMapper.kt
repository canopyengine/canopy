package io.canopy.engine.input

import java.util.Collections
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.asData
import io.canopy.engine.logging.logger

/** Owns action-to-binding mappings and their serializable representation. Use on the engine thread. */
class InputMapper {
    private val logger = logger<InputMapper>()

    private val mappings: MutableMap<String, MutableList<InputBind>> = mutableMapOf()

    private var frameMappings: Map<String, List<InputBind>>? = null

    /** Iterates a stable immutable snapshot; callback mutations become visible on the next pass. */
    @JvmSynthetic
    internal fun forEachAction(block: (String, List<InputBind>) -> Unit) {
        val current = frameMappings ?: Collections.unmodifiableMap(
            mappings.mapValues { (_, binds) -> Collections.unmodifiableList(binds.toList()) }
        ).also { frameMappings = it }
        current.forEach { (action, binds) -> block(action, binds) }
    }

    /** Returns a mapping copy with copied binding lists. */
    val actions: Map<String, List<InputBind>>
        get() = mappings.mapValues { it.value.toList() }

    /** Creates a serializable snapshot of the mappings. */
    fun toData(): InputData = asData()

    /** Replaces all mappings with the supplied data, copying its binding lists. */
    fun loadData(data: InputData) {
        frameMappings = null
        try {
            mappings.clear()
            mappings.putAll(
                data.mappings.associate { entry ->
                    entry.name to entry.binds.toMutableList()
                }
            )
        } finally {
            frameMappings = null
        }
    }

    /** Removes every action mapping. */
    fun clearMappings() {
        mappings.clear()
        frameMappings = null
    }

    /** Replaces or appends bindings for the supplied actions; other mappings are retained. */
    fun mapActions(vararg newMappings: Pair<String, List<InputBind>>, replace: Boolean = true) {
        newMappings.forEach { (action, newBinds) ->
            logger.info {
                "Mapping action [$action] to: ${newBinds.joinToString { it.describe() }}"
            }

            frameMappings = null
            try {
                val binds = mappings.getOrPut(action) { mutableListOf() }
                if (replace) binds.clear()
                binds += newBinds
            } finally {
                frameMappings = null
            }
        }
    }

    /** Removes the named action, if present. */
    fun unmapAction(action: String) {
        mappings.remove(action)
        frameMappings = null
    }

    private fun InputBind.describe(): String = when (type) {
        InputBind.Type.Keyboard -> "keyboard(${name.lowercase()})"
        InputBind.Type.Mouse -> "mouse(${name.lowercase()})"
    }
}
