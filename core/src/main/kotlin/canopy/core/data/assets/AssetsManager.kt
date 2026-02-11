package anchors.framework.data.assets

import anchors.framework.managers.Manager
import com.badlogic.gdx.graphics.Texture
import ktx.assets.toClasspathFile
import ktx.assets.toExternalFile
import ktx.assets.toInternalFile

class AssetsManager : Manager {
    fun loadTexture(
        path: String,
        source: FileSource,
        customOptions: Texture.() -> Unit = {},
    ): Texture =
        when (source) {
            FileSource.Internal -> Texture(path.toInternalFile(), true).apply(customOptions)
            FileSource.External -> Texture(path.toExternalFile(), true).apply(customOptions)
            FileSource.Classpath -> Texture(path.toClasspathFile(), true).apply(customOptions)
        }
}

enum class FileSource {
    Internal,
    External,
    Classpath,
}
