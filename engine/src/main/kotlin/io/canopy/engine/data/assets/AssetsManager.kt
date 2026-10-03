package io.canopy.engine.data.assets

import io.canopy.engine.core.managers.Manager

/** Resolves backend-neutral entries for the supported file sources. */
interface AssetsManager : Manager {
    /** Resolves an entry and applies customOptions before returning it; does not eagerly read its contents. */
    fun loadFile(
        path: String,
        source: FileSource = FileSource.Internal,
        customOptions: AssetEntry.() -> Unit = {},
    ): AssetEntry
}
