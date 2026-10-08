package io.canopy.engine.data.saving

import kotlin.reflect.KClass
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.data.assets.WritableAssetEntry
import io.canopy.engine.data.parsers.Json
import kotlinx.serialization.json.Json as SerializationJson
import kotlinx.serialization.json.JsonElement
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

    /** A module retains its payload type through decoding, callbacks and encoding. Null means not loaded. */
    private class ModuleEntry<T : Any>(val module: SaveModule<T>) {
        var loaded: T? = null
            private set

        fun reset() {
            loaded = null
        }

        fun load(element: JsonElement) {
            val decoded = SerializationJson.decodeFromJsonElement(module.serializer, element)
            loaded = decoded
            module.onLoad(decoded)
        }

        fun save(): Pair<String, JsonElement> {
            val data = module.onSave()
            return module.id to SerializationJson.encodeToJsonElement(module.serializer, data)
        }
    }

    private val dataRegistry = mutableMapOf<String, MutableMap<SaveModule<*>, ModuleEntry<*>>>()

    internal fun <T : Any> registerSaveModule(destination: String, module: SaveModule<T>) {
        val registry = dataRegistry.getOrPut(destination) { mutableMapOf() }
        val existing = registry[module]
        if (existing == null) {
            val id = module.id
            require(
                registry.keys.none {
                    it.id == id
                }
            ) { "Save module ID '$id' is already registered for destination $destination" }
            registry[module] = ModuleEntry(module)
        } else {
            existing.reset()
        }
    }

    /** Removes registered modules and their cached loaded data for this destination. */
    fun cleanModules(destination: String) {
        dataRegistry[destination] = mutableMapOf()
    }

    /**
     * Loads registered modules in registration order. Missing files and module IDs retain prior loaded data.
     * Decoded data is cached before onLoad; a decoding or callback failure stops subsequent modules.
     */
    fun load(destination: String, slot: Int) {
        val registry = dataRegistry[destination] ?: return
        if (registry.isEmpty()) return

        val file = destinationsMap[destination]?.invoke(slot) ?: return
        if (!file.exists()) return

        val jsonData = Json.rawParseFile(file)

        registry.values.forEach { entry ->
            val jsonElement = jsonData[entry.module.id] ?: return@forEach
            entry.load(jsonElement)
        }
    }

    /** Returns the first loaded payload of exactly [clazz]; uninitialized modules do not provide data, including Unit. */
    fun <T : Any> loadData(destination: String, clazz: KClass<T>): T {
        val registry =
            dataRegistry[destination] ?: error("No registry for destination $destination")

        val loaded = registry.values.firstNotNullOfOrNull { entry -> entry.loaded?.takeIf { it::class == clazz } }
            ?: error("No loaded data of type ${clazz.simpleName} for destination $destination")
        return clazz.javaObjectType.cast(loaded)
    }

    /** Encodes modules in registration order; writes the destination only after all callbacks and encoding succeed. */
    fun save(destination: String, slot: Int) {
        val registry = dataRegistry[destination] ?: return
        if (registry.isEmpty()) return

        val file = destinationsMap[destination]?.invoke(slot) ?: return

        val jsonMap = buildMap {
            registry.values.forEach { entry ->
                val (id, data) = entry.save()
                put(id, data)
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
