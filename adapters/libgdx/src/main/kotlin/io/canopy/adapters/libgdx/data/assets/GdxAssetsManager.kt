package io.canopy.adapters.libgdx.data.assets

import com.badlogic.gdx.files.FileHandle
import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.assets.AssetsManager
import io.canopy.engine.data.assets.FileSource
import ktx.assets.toAbsoluteFile
import ktx.assets.toClasspathFile
import ktx.assets.toExternalFile
import ktx.assets.toInternalFile
import ktx.assets.toLocalFile

class GdxAssetsManager : AssetsManager {

    override fun loadFile(path: String, source: FileSource, customOptions: AssetEntry.() -> Unit): AssetEntry =
        GdxAssetEntry(resolveFile(path, source)).apply(customOptions)

    fun resolveFile(path: String, source: FileSource): FileHandle = when (source) {
        FileSource.Internal -> path.toInternalFile()
        FileSource.External -> path.toExternalFile()
        FileSource.Classpath -> path.toClasspathFile()
        FileSource.Local -> path.toLocalFile()
        FileSource.Absolute -> path.toAbsoluteFile()
    }
}
