package canopy.data.registry

import canopy.data.parsers.JsonParser
import com.badlogic.gdx.files.FileHandle

/**
 * Loads defined items from .json files into registry maps.
 * Useful for mapping ids into concrete objects
 */
class IdRegistry<T : IdEntry>(
    val source: FileHandle? = null, // null for tests
) {
    /**
     * Maps a class / subclass of T to an id-map
     */
    val map: MutableMap<String, T> = mutableMapOf()

    init {
        if (source != null) check(source.exists()) { "'${source.path()}' not found!" }
    }

    fun nEntries(): Int = map.size

    /**
     * Loads registry - either by passing a list of items,
     * or passing **null** if to load from 'source' field of the repository
     */
    inline fun <reified R : T> loadRegistry(registryItems: List<R>? = null) {
        if (registryItems != null) {
            addItemsToRegistry(registryItems)
            return
        }

        check(source != null) { "No registry items passed - source shouldn't be null!" }
        val jsonFiles = if (source.isDirectory) collectJsonFiles(source) else listOf(source)

        jsonFiles
            .filter { it.extension() == "json" }
            .forEach { file ->
                val items: List<R> = JsonParser.parseFile(file) // R is reified
                addItemsToRegistry(items)
            }
    }

    fun addItemsToRegistry(items: List<T>) {
        items.forEach { item ->
            require(map.putIfAbsent(item.id, item) == null) { "Item with duplicate id found: ${item.id}" }
        }
    }

    fun collectJsonFiles(dir: FileHandle): List<FileHandle> =
        dir.list()?.flatMap { file ->
            when {
                file.isDirectory -> collectJsonFiles(file) // recurse into subfolders
                file.extension() == "json" -> listOf(file)
                else -> emptyList()
            }
        } ?: emptyList()

    inline fun <reified R : T> mapIds(
        ids: List<String>,
        updateHandler: R.() -> Unit = {},
    ): List<R> {
        if (ids.isEmpty()) return emptyList()
        return ids.map { id ->
            val item =
                map[id] as? R ?: throw IllegalArgumentException(
                    "Id '$id' not found in registry '${R::class.simpleName}'",
                )
            item.apply(updateHandler)
        }
    }
}
