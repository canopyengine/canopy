package io.canopy.engine.data.saving

import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.manager
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * A unit of persisted data handled by [SaveManager].
 *
 * Each module is responsible for **one logical piece of save data** and defines:
 * - how to serialize/deserialize the data ([serializer])
 * - how to produce the data to persist ([onSave])
 * - how to apply loaded data back into runtime state ([onLoad])
 *
 * The module [id] is used as the key in the save file:
 *
 * ```json
 * {
 *   "<id>": { ...serialized module data... }
 * }
 * ```
 */
interface SaveModule<T : @Serializable Any> {

    /** Stable identifier used as the JSON key for this module (must be unique per destination). */
    val id: String

    /** Serializer used to encode/decode this module's payload. */
    val serializer: KSerializer<T>

    /** Called during save to produce the payload that will be written to disk. */
    val onSave: () -> T

    /** Called during load with the decoded payload. */
    val onLoad: (T) -> Unit
}

/**
 * Registers a [SaveModule] into the global [SaveManager] (via [ManagersRegistry]).
 *
 * This helper exists so callers can define save modules inline without creating a named class.
 *
 * Example:
 * ```
 * registerSaveModule(
 *   destination = "profile",
 *   id = "player.stats",
 *   serializer = PlayerStats.serializer(),
 *   onSave = { snapshotStats() },
 *   onLoad = { applyStats(it) }
 * )
 * ```
 *
 * @throws IllegalStateException if [SaveManager] is not registered in [ManagersRegistry]
 */
fun <T : @Serializable Any> registerSaveModule(
    destination: String,
    id: String,
    serializer: KSerializer<T>,
    onSave: () -> T,
    onLoad: (T) -> Unit = {},
) {
    val saveModule =
        object : SaveModule<T> {
            override val id: String = id
            override val serializer: KSerializer<T> = serializer
            override val onSave: () -> T = onSave
            override val onLoad: (T) -> Unit = onLoad
        }

    check(ManagersRegistry.has(SaveManager::class)) {
        """
        [SAVING]
        No SaveManager found in ManagersRegistry.

        To fix it: register SaveManager into the ManagersRegistry before calling registerSaveModule().
        """.trimIndent()
    }

    // Delegates storage/dispatch to the SaveManager.
    manager<SaveManager>().registerSaveModule(destination, saveModule)
}

inline fun <reified T : @Serializable Any> registerSaveModule(
    destination: String,
    id: String,
    noinline onSave: () -> T,
    noinline onLoad: (T) -> Unit = {},
) = registerSaveModule(destination, id, serializer = serializer<T>(), onSave = onSave, onLoad = onLoad)
