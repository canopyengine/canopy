package canopy.engine.data.core.assets

import canopy.engine.core.managers.Manager
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import ktx.assets.*

class AssetsManager : Manager {
    fun loadTexture(path: String, source: FileSource, customOptions: Texture.() -> Unit = {}): Texture =
        Texture(loadFile(path, source)).apply { customOptions() }

    fun loadFile(path: String, source: FileSource, customOptions: FileHandle.() -> Unit = {}) = when (source) {
        FileSource.Internal -> path.toInternalFile()
        FileSource.External -> path.toExternalFile()
        FileSource.Classpath -> path.toClasspathFile()
        FileSource.Local -> path.toLocalFile()
        FileSource.Absolute -> path.toAbsoluteFile()
    }.apply { customOptions() }

    enum class FileSource {
        Internal,
        External,
        Classpath,
        Local,
        Absolute,
    }
}
