package canopy.engine.data.saving

import kotlin.reflect.KClass
import canopy.engine.core.managers.Manager
import canopy.engine.data.core.parsers.JsonParser
import com.badlogic.gdx.files.FileHandle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Responsible for handling data saving and loading.
 *
 * The structure of data serialization and deserialization are configured through individual modules.
 *
 * @see SaveModule
 */
class SaveManager(vararg destinations: Pair<String, (slot: Int) -> FileHandle>) : Manager {
    private val destinationsMap: MutableMap<String, (slot: Int) -> FileHandle> =
        mutableMapOf(*destinations)

    /** Holds module info, and maps to data to be saved */
    private val dataRegistry:
        MutableMap<String, MutableMap<SaveModule<*>, @Serializable Any>> =
        mutableMapOf()

    // Register new save module
    internal fun registerSaveModule(destination: String, module: SaveModule<*>) {
        val registry = dataRegistry.getOrPut(destination) { mutableMapOf() }
        registry[module] = Unit // placeholder
    }

    fun cleanModules(destination: String) {
        dataRegistry[destination] = mutableMapOf()
    }

    /**
     * Loads data from a given save slot.
     *
     * Each registered module has its onLoad method called, with the parsed data passed directly.
     */
    @Suppress("UNCHECKED_CAST")
    fun load(destination: String, slot: Int) {
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
    fun <T : Any> loadData(destination: String, clazz: KClass<T>): T {
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
    fun save(destination: String, slot: Int) {
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
                        JsonParser.encodeJsonElement(typedModule.serializer, data)
                    )
                }
            }
        JsonParser.toFile(JsonObject(jsonMap), fileLocation)
    }

    fun saveAll(slot: Int) {
        destinationsMap.keys.forEach { save(it, slot) }
    }

    fun loadAll(slot: Int) {
        destinationsMap.keys.forEach { it -> load(it, slot) }
    }
}
