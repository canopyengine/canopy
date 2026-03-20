package io.canopy.engine.data.registry

import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.core.registry.IdEntry
import io.canopy.engine.data.parsers.Json

/**
 * Simple ID-based registry that loads [io.canopy.engine.data.core.registry.IdEntry] items from JSON files.
 */
class IdRegistry<T : IdEntry>(val source: AssetEntry? = null) {

    val map: MutableMap<String, T> = mutableMapOf()

    init {
        if (source != null) check(source.exists()) { "'${source.path}' not found!" }
    }

    fun nEntries(): Int = map.size

    inline fun <reified R : T> loadRegistry(registryItems: List<R>? = null) {
        if (registryItems != null) {
            addItemsToRegistry(registryItems)
            return
        }

        check(source != null) { "No registry items passed - source shouldn't be null!" }

        val jsonFiles =
            if (source.isDirectory) {
                collectJsonFiles(source)
            } else {
                listOf(source)
            }

        jsonFiles
            .filter { it.extension == "json" }
            .forEach { file ->
                val items: List<R> = Json.fromFile(file)
                addItemsToRegistry(items)
            }
    }

    fun addItemsToRegistry(items: List<T>) {
        items.forEach { item ->
            require(map.putIfAbsent(item.id, item) == null) {
                "Item with duplicate id found: ${item.id}"
            }
        }
    }

    fun collectJsonFiles(dir: AssetEntry): List<AssetEntry> = dir.list().flatMap { entry ->
        when {
            entry.isDirectory -> collectJsonFiles(entry)
            entry.extension == "json" -> listOf(entry)
            else -> emptyList()
        }
    }

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
