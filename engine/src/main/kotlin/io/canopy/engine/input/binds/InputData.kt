package io.canopy.engine.input.binds

import io.canopy.engine.input.mapper.InputMapper
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
 * Converts an [InputMapper] into a serializable [InputData] structure.
 *
 * Useful when exporting runtime mappings into a configuration file.
 */
fun InputMapper.asData(): InputData {
    val entries: List<InputEntry> =
        actions.map { (action, binds) ->
            InputEntry(action, binds)
        }

    return InputData(entries)
}
