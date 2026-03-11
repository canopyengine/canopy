package io.canopy.engine.data.core.registry

/**
 * Represents an entry that can be stored in an [IdRegistry].
 *
 * Each entry is identified by a **namespaced ID** composed of:
 *
 * ```
 * <domain>:<name>
 * ```
 *
 * Example:
 * ```
 * canopy:player
 * mygame:enemy
 * ```
 *
 * The **domain** usually identifies the owning system, module, or game,
 * while the **name** identifies the specific entry within that domain.
 *
 * This pattern prevents ID collisions between different modules or libraries.
 *
 * @see IdRegistry
 */
interface IdEntry {

    /** Namespace or module that owns this entry (e.g. `canopy`, `mygame`). */
    val domain: String

    /** Local identifier of the entry inside the domain. */
    val name: String

    /**
     * Fully-qualified ID composed of `domain:name`.
     */
    val id: String
        get() = "$domain:$name"
}
