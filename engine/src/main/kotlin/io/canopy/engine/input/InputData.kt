package io.canopy.engine.input

import io.canopy.engine.data.core.registry.IdEntry
import kotlinx.serialization.Serializable

/**
 * Serializable container representing the full input configuration.
 *
 * Typically loaded from / saved to a file.
 *
 * Example JSON structure:
 * ```
 * {
 *   "mappings": [
 *     {
 *       "name": "move_left",
 *       "binds": [ { "type": "KeyBind", "code": 21 } ]
 *     }
 *   ]
 * }
 * ```
 */
@Serializable
class InputData(
    /** List of action mappings. */
    val mappings: List<InputEntry>,
)

/**
 * Serializable definition of a single input action.
 *
 * An action can have multiple binds so that several inputs
 * trigger the same gameplay behavior.
 *
 * Example:
 * ```
 * move_left -> [A key, Left Arrow]
 * ```
 */
@Serializable
class InputEntry(
    /** Action name (e.g. "move_left", "jump", "shoot"). */
    override val name: String,

    /** All physical inputs bound to this action. */
    val binds: List<InputBind>,
) : IdEntry {

    /** Fixed registry domain for input actions. */
    override val domain: String = "input"
}

/**
 * Converts an [InputMapper] into a serializable [InputData] structure.
 *
 * Useful when exporting runtime mappings into a configuration file.
 */
fun InputMapper.asData(): InputData {
    val entries: List<InputEntry> =
        mappings.map { (action, binds) ->
            InputEntry(action, binds)
        }

    return InputData(entries)
}
