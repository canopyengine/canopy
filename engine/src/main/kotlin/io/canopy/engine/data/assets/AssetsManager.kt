package io.canopy.engine.data.assets

import io.canopy.engine.core.managers.Manager

interface AssetsManager : Manager {
    fun loadFile(
        path: String,
        source: FileSource = FileSource.Internal,
        customOptions: AssetEntry.() -> Unit = {},
    ): AssetEntry
}
