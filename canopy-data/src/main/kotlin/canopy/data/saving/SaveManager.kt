package canopy.data.saving

import canopy.core.managers.Manager
import canopy.data.parsers.JsonParser
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

const val SAVE_LOCATION = "saves"

enum class SaveDestination {
    PlayerData,
    InputBinding,
}

/**
 * Responsible for handling data saving and loading.
 *
 * The structure of data serialization and deserialization are configured through individual modules.
 *
 * @see SaveModule
 */
class SaveManager(
    vararg destinations: Pair<SaveDestination, (slot: Int) -> FileHandle>,
) : Manager {
    private val destinationsMap: MutableMap<SaveDestination, (slot: Int) -> FileHandle> =
        if (destinations.isEmpty()) {
            mutableMapOf(
                SaveDestination.PlayerData to { slot ->
                    Gdx.files.local("$SAVE_LOCATION/player_save_$slot.json")
                },
                SaveDestination.InputBinding to { _ ->
                    Gdx.files.local("$SAVE_LOCATION/input_bindings.json")
                },
            )
        } else {
            mutableMapOf(*destinations)
        }

    /** Holds module info, and maps to data to be saved */
    private val dataRegistry:
        MutableMap<SaveDestination, MutableMap<SaveModule<*>, @Serializable Any>> =
        mutableMapOf()

    // Register new save module
    fun register(
        destination: SaveDestination,
        module: SaveModule<*>,
    ) {
        val registry = dataRegistry.getOrPut(destination) { mutableMapOf() }
        registry[module] = Unit // placeholder
    }

    fun cleanModules(destination: SaveDestination) {
        dataRegistry[destination] = mutableMapOf()
    }

    /**
     * Loads data from a given save slot.
     *
     * Each registered module has its onLoad method called, with the parsed data passed directly.
     */
    @Suppress("UNCHECKED_CAST")
    fun load(
        destination: SaveDestination,
        slot: Int,
    ) {
        val fileLocation = destinationsMap[destination]?.invoke(slot) ?: return
        if (!fileLocation.exists()) return

        val jsonData = JsonParser.rawParseFile(fileLocation)
        val registry = dataRegistry[destination] ?: return

        registry.keys.forEach { module ->
            val jsonElement = jsonData[module.id] ?: return@forEach

            val typedModule = module as SaveModule<Any>
            val decodedData =
                JsonParser.decodeJsonElement(typedModule.serializer, jsonElement)

            registry[typedModule] = decodedData
            typedModule.onLoad(decodedData)
        }
    }

    /**
     * Loads specific data - useful for reading data after initial load.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> loadData(
        destination: SaveDestination,
        clazz: KClass<T>,
    ): T {
        val registry =
            dataRegistry[destination]
                ?: error("No registry for destination $destination")

        return registry.values.first { it::class == clazz } as T
    }

    /**
     * Saves data into a given slot.
     *
     * JSON structure is defined based on registration order.
     *
     * Data to be saved on each module is defined by the return value of the **onSave** method.
     */
    fun save(
        destination: SaveDestination,
        slot: Int,
    ) {
        val fileLocation = destinationsMap[destination]?.invoke(slot) ?: return
        val registry = dataRegistry[destination] ?: return

        val jsonMap =
            buildMap {
                registry.keys.forEach { module ->
                    @Suppress("UNCHECKED_CAST")
                    val typedModule = module as SaveModule<Any>
                    val data = typedModule.onSave()
                    put(
                        typedModule.id,
                        JsonParser.encodeJsonElement(typedModule.serializer, data),
                    )
                }
            }
        JsonParser.toFile(JsonObject(jsonMap), fileLocation)
    }

    fun saveAll(slot: Int) {
        SaveDestination.entries.forEach { save(it, slot) }
    }

    fun loadAll(slot: Int) {
        SaveDestination.entries.forEach { load(it, slot) }
    }
}
