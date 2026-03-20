package io.canopy.engine.data.saving

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.data.assets.WritableAssetEntry
import io.canopy.engine.data.parsers.Json
import kotlinx.serialization.json.JsonObject

/**
 * Coordinates save/load of game data across multiple independent [SaveModule]s.
 *
 * Concepts:
 * - Destination: a named save "channel" (e.g. "profile", "world", "settings").
 *   Each destination maps a slot number -> writable asset entry.
 * - Slot: numeric save slot (e.g. 0..N).
 * - Module: a pluggable unit that knows how to serialize/deserialize one piece of data.
 *
 * On-disk format (per destination file):
 * {
 *   "<moduleIdA>": { ...module json... },
 *   "<moduleIdB>": { ...module json... }
 * }
 */
class SaveManager(vararg destinations: Pair<String, (slot: Int) -> WritableAssetEntry>) : Manager {

    /** Maps destination name -> slot -> file resolver. */
    private val destinationsMap: MutableMap<String, (slot: Int) -> WritableAssetEntry> =
        mutableMapOf(*destinations)

    /**
     * destination -> (module -> lastLoadedData)
     */
    private val dataRegistry: MutableMap<String, MutableMap<SaveModule<*>, Any>> =
        mutableMapOf()

    internal fun registerSaveModule(destination: String, module: SaveModule<*>) {
        val registry = dataRegistry.getOrPut(destination) { mutableMapOf() }
        registry[module] = Unit
    }

    fun cleanModules(destination: String) {
        dataRegistry[destination] = mutableMapOf()
    }

    @Suppress("UNCHECKED_CAST")
    fun load(destination: String, slot: Int) {
        val registry = dataRegistry[destination] ?: return
        if (registry.isEmpty()) return

        val file = destinationsMap[destination]?.invoke(slot) ?: return
        if (!file.exists()) return

        val jsonData = Json.rawParseFile(file)

        registry.keys.forEach { module ->
            val jsonElement = jsonData[module.id] ?: return@forEach

            val typedModule = module as SaveModule<Any>
            val decodedData = Json.decodeJsonElement(typedModule.serializer, jsonElement)

            registry[typedModule] = decodedData
            typedModule.onLoad(decodedData)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> loadData(destination: String, clazz: KClass<T>): T {
        val registry =
            dataRegistry[destination] ?: error("No registry for destination $destination")

        return registry.values.first { it::class == clazz } as T
    }

    fun save(destination: String, slot: Int) {
        val registry = dataRegistry[destination] ?: return
        if (registry.isEmpty()) return

        val file = destinationsMap[destination]?.invoke(slot) ?: return

        val jsonMap = buildMap {
            registry.keys.forEach { module ->
                @Suppress("UNCHECKED_CAST")
                val typedModule = module as SaveModule<Any>

                val data = typedModule.onSave()
                put(
                    typedModule.id,
                    Json.encodeJsonElement(typedModule.serializer, data)
                )
            }
        }

        Json.toFile(JsonObject(jsonMap), file)
    }

    fun saveAll(slot: Int) {
        destinationsMap.keys.forEach { save(it, slot) }
    }

    fun loadAll(slot: Int) {
        destinationsMap.keys.forEach { load(it, slot) }
    }
}
