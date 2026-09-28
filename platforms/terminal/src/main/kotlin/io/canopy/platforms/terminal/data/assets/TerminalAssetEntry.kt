package io.canopy.platforms.terminal.data.assets

import java.io.File
import java.io.FileOutputStream
import java.net.URL
import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.assets.FileSource
import io.canopy.engine.data.assets.WritableAssetEntry
import io.canopy.engine.logging.EngineLogs

/**
 * Terminal-specific asset entry using standard Java I/O.
 * No libGDX dependencies - works purely on JVM filesystem/classpath.
 */
class TerminalAssetEntry(
    private val file: File,
    private val source: FileSource,
    private val classpathUrl: URL? = null,
) : WritableAssetEntry {

    private val log = EngineLogs.subsystem("assets.terminal")

    override val path: String = file.path
    override val name: String = file.name
    override val extension: String = file.extension ?: ""
    override val isDirectory: Boolean = file.isDirectory

    override fun exists(): Boolean = file.exists() || classpathUrl != null

    override fun readBytes(): ByteArray = if (classpathUrl != null) {
        classpathUrl.openStream().readAllBytes()
    } else {
        file.readBytes()
    }

    override fun readText(): String = readBytes().decodeToString()

    override fun list(): List<AssetEntry> = if (isDirectory) {
        file.listFiles()?.map { TerminalAssetEntry(it, source) } ?: emptyList()
    } else {
        emptyList()
    }

    override fun writeBytes(bytes: ByteArray, append: Boolean) {
        require(classpathUrl == null) { "Cannot write to classpath resource: $path" }
        require(!isDirectory) { "Cannot write to directory: $path" }

        file.parentFile?.mkdirs()
        FileOutputStream(file, append).use { it.write(bytes) }
        log.trace("event" to "asset.write", "path" to path, "bytes" to bytes.size) { "Wrote asset" }
    }

    override fun writeText(text: String, append: Boolean) {
        writeBytes(text.encodeToByteArray(), append)
    }

    companion object {
        fun create(path: String, source: FileSource): TerminalAssetEntry = when (source) {
            FileSource.Internal -> {
                // Internal: relative to working directory
                TerminalAssetEntry(File(path), source)
            }
            FileSource.External -> {
                // External: platform-specific external storage (use working dir for terminal)
                TerminalAssetEntry(File(path), source)
            }
            FileSource.Classpath -> {
                // Classpath: load from resources
                val url = Thread.currentThread().contextClassLoader.getResource(path)
                val file = if (url != null && url.protocol == "file") {
                    File(url.toURI())
                } else {
                    File(path) // fallback, will fail gracefully on read
                }
                TerminalAssetEntry(file, source, url)
            }
            FileSource.Local -> {
                // Local: user home directory
                TerminalAssetEntry(File(System.getProperty("user.home"), path), source)
            }
            FileSource.Absolute -> {
                // Absolute: absolute path
                TerminalAssetEntry(File(path), source)
            }
        }
    }
}
