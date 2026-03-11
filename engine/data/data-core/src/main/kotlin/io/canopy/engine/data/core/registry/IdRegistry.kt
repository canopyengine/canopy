package io.canopy.engine.data.core.registry

import com.badlogic.gdx.files.FileHandle
import io.canopy.engine.data.core.parsers.JsonParser

/**
 * Simple ID-based registry that loads [IdEntry] items from JSON files.
 *
 * Use cases:
 * - Map stable string IDs (e.g. `"canopy:player"`) to concrete objects
 * - Load content definitions from disk (assets/configs/mods)
 *
 * Loading modes:
 * 1) In-memory (tests / programmatic):
 *    `loadRegistry(listOf(...))`
 *
 * 2) From disk:
 *    - if [source] is a file: loads that file (if it ends with `.json`)
 *    - if [source] is a directory: recursively loads all `.json` files inside it
 *
 * Notes:
 * - IDs must be unique across all loaded items.
 * - This registry does not currently support hot-reload or removal; it only adds.
 */
class IdRegistry<T : IdEntry>(
    /** File or directory to load from. Null is allowed for tests/programmatic use. */
    val source: FileHandle? = null,
) {

    /**
     * Registry storage: maps `id -> entry`.
     *
     * Example key: `"canopy:player"`
     */
    val map: MutableMap<String, T> = mutableMapOf()

    init {
        // Fail fast if a source was supplied but doesn't exist.
        if (source != null) check(source.exists()) { "'${source.path()}' not found!" }
    }

    /** Number of registered entries. */
    fun nEntries(): Int = map.size

    /**
     * Loads entries into the registry.
     *
     * @param registryItems If provided, adds those items directly (useful for tests).
     *                      If null, loads from [source] (which must not be null).
     *
     * @throws IllegalStateException if registryItems is null and [source] is null
     */
    inline fun <reified R : T> loadRegistry(registryItems: List<R>? = null) {
        // Programmatic load (tests / generated content).
        if (registryItems != null) {
            addItemsToRegistry(registryItems)
            return
        }

        // File-based load.
        check(source != null) { "No registry items passed - source shouldn't be null!" }

        val jsonFiles =
            if (source.isDirectory) {
                collectJsonFiles(source)
            } else {
                listOf(source)
            }

        jsonFiles
            .filter { it.extension() == "json" }
            .forEach { file ->
                // R is reified so JsonParser can decode List<R>.
                val items: List<R> = JsonParser.fromFile(file)
                addItemsToRegistry(items)
            }
    }

    /**
     * Adds items to the registry, enforcing uniqueness by [IdEntry.id].
     *
     * @throws IllegalArgumentException if any duplicate ID is found
     */
    fun addItemsToRegistry(items: List<T>) {
        items.forEach { item ->
            require(map.putIfAbsent(item.id, item) == null) {
                "Item with duplicate id found: ${item.id}"
            }
        }
    }

    /**
     * Recursively collects all `.json` files under [dir].
     *
     * Note:
     * - This scans subdirectories depth-first.
     */
    fun collectJsonFiles(dir: FileHandle): List<FileHandle> = dir.list()?.flatMap { file ->
        when {
            file.isDirectory -> collectJsonFiles(file)
            file.extension() == "json" -> listOf(file)
            else -> emptyList()
        }
    } ?: emptyList()

    /**
     * Resolves a list of IDs into a list of registry items, optionally applying a mutation block.
     *
     * @param ids IDs to resolve.
     * @param updateHandler Optional callback applied to each resolved item.
     *
     * @throws IllegalArgumentException if any ID is missing or has the wrong runtime type.
     */
    inline fun <reified R : T> mapIds(ids: List<String>, updateHandler: R.() -> Unit = {}): List<R> {
        if (ids.isEmpty()) return emptyList()

        return ids.map { id ->
            val item = map[id] as? R
                ?: throw IllegalArgumentException(
                    "Id '$id' not found in registry '${R::class.simpleName}'"
                )

            item.apply(updateHandler)
        }
    }
}
