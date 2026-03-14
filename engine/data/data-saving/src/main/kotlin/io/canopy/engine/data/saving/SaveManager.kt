package io.canopy.engine.data.saving

import kotlin.reflect.KClass
import com.badlogic.gdx.files.FileHandle
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.data.core.parsers.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Coordinates save/load of game data across multiple independent [SaveModule]s.
 *
 * Concepts:
 * - Destination: a named save "channel" (e.g. "profile", "world", "settings").
 *   Each destination maps a slot number -> [FileHandle].
 * - Slot: numeric save slot (e.g. 0..N).
 * - Module: a pluggable unit that knows how to serialize/deserialize one piece of data.
 *
 * On-disk format (per destination file):
 * ```json
 * {
 *   "<moduleIdA>": { ...module json... },
 *   "<moduleIdB>": { ...module json... }
 * }
 * ```
 *
 * Notes:
 * - If a destination file does not exist, [load] is a no-op.
 * - Each module is stored under its own stable [SaveModule.id].
 * - Module iteration order is not relied upon (registry is a map).
 *
 * @see SaveModule
 */
class SaveManager(vararg destinations: Pair<String, (slot: Int) -> FileHandle>) : Manager {

    /** Maps destination name -> slot -> file path resolver. */
    private val destinationsMap: MutableMap<String, (slot: Int) -> FileHandle> =
        mutableMapOf(*destinations)

    /**
     * In-memory store of loaded module data.
     *
     * destination -> (module -> lastLoadedData)
     *
     * We store `Any` because each module has its own type. The module's serializer
     * determines how it is encoded/decoded.
     */
    private val dataRegistry: MutableMap<String, MutableMap<SaveModule<*>, @Serializable Any>> =
        mutableMapOf()

    /* ============================================================
     * Module registration
     * ============================================================ */

    /**
     * Registers a save module under a destination.
     *
     * This is typically called by the module itself (or a bootstrapper) during setup.
     * We store a placeholder value until the first load/save occurs.
     */
    internal fun registerSaveModule(destination: String, module: SaveModule<*>) {
        val registry = dataRegistry.getOrPut(destination) { mutableMapOf() }
        registry[module] = Unit // placeholder until a real value is loaded or saved
    }

    /**
     * Clears all modules registered under [destination].
     *
     * Useful in tests or when rebuilding save pipelines dynamically.
     */
    fun cleanModules(destination: String) {
        dataRegistry[destination] = mutableMapOf()
    }

    /* ============================================================
     * Loading
     * ============================================================ */

    /**
     * Loads a destination file for the given save [slot] and dispatches the data to modules.
     *
     * Behavior:
     * - If destination is unknown, no-op.
     * - If the file does not exist, no-op.
     * - For each registered module:
     *   - if the JSON contains `module.id`, decode it using the module serializer
     *   - store it in memory
     *   - call [SaveModule.onLoad]
     */
    @Suppress("UNCHECKED_CAST")
    fun load(destination: String, slot: Int) {
        val fileLocation = destinationsMap[destination]?.invoke(slot) ?: return
        if (!fileLocation.exists()) return

        val jsonData = Json.rawParseFile(fileLocation)
        val registry = dataRegistry[destination] ?: return

        registry.keys.forEach { module ->
            val jsonElement = jsonData[module.id] ?: return@forEach

            val typedModule = module as SaveModule<Any>
            val decodedData = Json.decodeJsonElement(typedModule.serializer, jsonElement)

            registry[typedModule] = decodedData
            typedModule.onLoad(decodedData)
        }
    }

    /**
     * Returns the last loaded data of type [T] for the given [destination].
     *
     * Intended usage:
     * - after [load] has already been called
     * - to access module data without holding module references
     *
     * @throws IllegalStateException if the destination has no registry
     * @throws NoSuchElementException if no stored entry matches the requested type
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> loadData(destination: String, clazz: KClass<T>): T {
        val registry =
            dataRegistry[destination] ?: error("No registry for destination $destination")

        return registry.values.first { it::class == clazz } as T
    }

    /* ============================================================
     * Saving
     * ============================================================ */

    /**
     * Saves all registered modules for [destination] into the given save [slot].
     *
     * Each module defines its payload via [SaveModule.onSave].
     * The module serializer is used to encode the payload, stored under [SaveModule.id].
     */
    fun save(destination: String, slot: Int) {
        val fileLocation = destinationsMap[destination]?.invoke(slot) ?: return
        val registry = dataRegistry[destination] ?: return

        if (registry.isEmpty()) return

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

        Json.toFile(JsonObject(jsonMap), fileLocation)
    }

    /** Saves all destinations for the given slot (e.g. profile + world + settings). */
    fun saveAll(slot: Int) {
        destinationsMap.keys.forEach { save(it, slot) }
    }

    /** Loads all destinations for the given slot. */
    fun loadAll(slot: Int) {
        destinationsMap.keys.forEach { load(it, slot) }
    }
}
