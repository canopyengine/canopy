package io.canopy.engine.input.binds

import io.canopy.engine.data.core.registry.IdEntry
import kotlinx.serialization.Serializable

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
