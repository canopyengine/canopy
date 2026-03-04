package io.canopy.engine.input

import com.badlogic.gdx.Input
import io.canopy.engine.data.saving.registerSaveModule
import ktx.log.logger

/**
 * Maintains runtime mappings between **input actions** and **physical input binds**.
 *
 * Example mapping:
 * ```
 * "jump" -> [SPACE, GAMEPAD_A]
 * "shoot" -> [LEFT_MOUSE]
 * ```
 *
 * Responsibilities:
 * - Store action → bind relationships
 * - Resolve which actions correspond to a given bind
 * - Load/save mappings via the engine's save system
 *
 * Mappings are automatically registered into the save system under the
 * `"input"` destination so user keybindings can persist across sessions.
 */
class InputMapper {

    private val logger = logger<InputMapper>()

    /**
     * Maps action name → list of binds that trigger it.
     *
     * Example:
     * ```
     * move_left -> [A, LEFT_ARROW]
     * ```
     */
    internal val mappings: MutableMap<String, MutableList<InputBind>> = mutableMapOf()

    init {
        clearMappings()

        // Register this mapper with the SaveManager so input mappings can be persisted.
        registerSaveModule(
            destination = "input",
            id = "input",
            serializer = InputData.serializer(),
            onSave = { this.asData() },
            onLoad = ::loadData
        )
    }

    /**
     * Loads mappings from serialized [InputData].
     */
    private fun loadData(data: InputData) {
        mappings.clear()

        mappings.putAll(
            data.mappings.associate { entry ->
                entry.name to entry.binds.toMutableList()
            }
        )
    }

    /**
     * Returns all actions mapped to a given [bind].
     *
     * Example:
     * ```
     * mapToAction(SPACE) -> ["jump"]
     * ```
     */
    fun mapToAction(bind: InputBind): List<String> = mappings.entries
        .filter { bind in it.value }
        .map { it.key }

    /**
     * Clears all registered action mappings.
     */
    fun clearMappings() {
        mappings.clear()
    }

    /**
     * Maps an action to one or more input binds.
     *
     * @param action the logical action name (e.g. `"jump"`)
     * @param newBinds the physical inputs that trigger the action
     * @param replace if true, replaces existing binds for the action;
     *                otherwise appends to the current list
     */
    fun mapAction(action: String, newBinds: List<InputBind>, replace: Boolean = true) {
        logger.info {
            "Mapping action [$action] to: ${newBinds.map { Input.Keys.toString(it.code) }}"
        }

        val binds = mappings.computeIfAbsent(action) { mutableListOf() }

        if (replace) binds.clear()

        binds += newBinds
    }

    /**
     * Unmaps binds
     */
    fun unmapAction(action: String) {
        mappings.remove(action)
    }
}
