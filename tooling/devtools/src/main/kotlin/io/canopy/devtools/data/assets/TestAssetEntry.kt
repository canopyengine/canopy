package io.canopy.devtools.data.assets

import java.io.File
import io.canopy.engine.data.assets.WritableAssetEntry

/**
 * Simple JVM test implementation of [WritableAssetEntry].
 */
class TestAssetEntry(private val file: File) : WritableAssetEntry {

    override val path: String
        get() = file.path

    override val name: String
        get() = file.name

    override val extension: String
        get() = file.extension

    override val isDirectory: Boolean
        get() = file.isDirectory

    override fun exists(): Boolean = file.exists()

    override fun readText(): String = file.readText()

    override fun readBytes(): ByteArray = file.readBytes()

    override fun writeText(text: String, append: Boolean) {
        file.parentFile?.mkdirs()
        if (append) {
            file.appendText(text)
        } else {
            file.writeText(text)
        }
    }

    override fun writeBytes(bytes: ByteArray, append: Boolean) {
        file.parentFile?.mkdirs()
        if (append) {
            file.appendBytes(bytes)
        } else {
            file.writeBytes(bytes)
        }
    }

    override fun list(): List<WritableAssetEntry> = file.listFiles()?.map(::TestAssetEntry) ?: emptyList()
}
