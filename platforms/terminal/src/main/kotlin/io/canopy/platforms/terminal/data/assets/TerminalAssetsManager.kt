package io.canopy.platforms.terminal.data.assets

import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.assets.AssetsManager
import io.canopy.engine.data.assets.FileSource
import io.canopy.engine.logging.EngineLogs

/**
 * Terminal-native assets manager using standard Java I/O.
 * No libGDX dependencies - pure JVM filesystem/classpath access.
 */
class TerminalAssetsManager : AssetsManager {

    private val log = EngineLogs.subsystem("assets.terminal")

    override fun loadFile(path: String, source: FileSource, customOptions: AssetEntry.() -> Unit): AssetEntry {
        val entry = TerminalAssetEntry.create(path, source).apply(customOptions)
        log.debug("event" to "asset.load", "path" to path, "source" to source.name) { "Loaded terminal asset" }
        return entry
    }
}
