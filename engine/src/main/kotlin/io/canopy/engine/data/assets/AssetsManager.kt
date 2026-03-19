package io.canopy.engine.data.core.assets

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import io.canopy.engine.core.managers.Manager
import ktx.assets.*

/**
 * Manages direct asset loading for the engine.
 *
 * This manager provides simple helpers for loading files and textures
 * from different libGDX file systems.
 *
 * Unlike libGDX's `AssetManager`, this class performs **immediate loading**
 * and does not manage asset lifetimes, caching, or async loading.
 *
 * Typical usage:
 *
 * ```kotlin
 * val texture = manager<AssetsManager>().loadTexture("player.png", FileSource.Internal)
 * ```
 */
class AssetsManager : Manager {

    /**
     * Loads a [Texture] from the given path and file source.
     *
     * @param path Path to the asset.
     * @param source File system to load the asset from.
     * @param customOptions Optional configuration applied to the texture after creation.
     */
    fun loadTexture(path: String, source: FileSource, customOptions: Texture.() -> Unit = {}): Texture =
        Texture(loadFile(path, source)).apply { customOptions() }

    /**
     * Loads a [FileHandle] from the specified file source.
     *
     * This is a thin wrapper over KTX's file helpers.
     *
     * @param path Path to the file.
     * @param source File system to load from.
     * @param customOptions Optional configuration applied to the resulting file handle.
     */
    fun loadFile(path: String, source: FileSource, customOptions: FileHandle.() -> Unit = {}): FileHandle =
        when (source) {
            FileSource.Internal -> path.toInternalFile()
            FileSource.External -> path.toExternalFile()
            FileSource.Classpath -> path.toClasspathFile()
            FileSource.Local -> path.toLocalFile()
            FileSource.Absolute -> path.toAbsoluteFile()
        }.apply { customOptions() }

    /**
     * Represents the libGDX file system used to resolve a path.
     *
     * See: https://libgdx.com/wiki/files/file-handling
     */
    enum class FileSource {

        /** Files bundled inside the application (assets folder). */
        Internal,

        /** User-accessible files outside the application (platform-dependent). */
        External,

        /** Files located on the application classpath (typically inside JARs). */
        Classpath,

        /** Files stored relative to the application's working directory. */
        Local,

        /** Files referenced using an absolute system path. */
        Absolute,
    }
}
