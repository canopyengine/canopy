package canopy.engine.data.saving

import canopy.engine.core.managers.ManagersRegistry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Represents a module of the persisted data.
 */
interface SaveModule<T : @Serializable Any> {
    val id: String
    val serializer: KSerializer<T>
    val onSave: () -> T
    val onLoad: (T) -> Unit
}

fun <T : @Serializable Any> registerSaveModule(
    destination: String,
    id: String,
    serializer: KSerializer<T>,
    onSave: () -> T,
    onLoad: (T) -> Unit = {},
) {
    val saveModule =
        object : SaveModule<T> {
            override val id = id
            override val serializer = serializer
            override val onSave = onSave
            override val onLoad = onLoad
        }
    check(ManagersRegistry.has(SaveManager::class)) {
        """

        [SAVING]
        No save manager found!

        To fix it: register it into the Managers Registry!

        """.trimIndent()
    }

    ManagersRegistry.get(SaveManager::class).registerSaveModule(destination, saveModule)
}
