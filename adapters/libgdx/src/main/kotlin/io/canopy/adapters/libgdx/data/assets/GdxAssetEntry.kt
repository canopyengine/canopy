package io.canopy.adapters.libgdx.data.assets

import com.badlogic.gdx.files.FileHandle
import io.canopy.engine.data.assets.WritableAssetEntry

class GdxAssetEntry(private val fileHandle: FileHandle) : WritableAssetEntry {

    override val path: String
        get() = fileHandle.path()

    override val name: String
        get() = fileHandle.name()

    override val extension: String
        get() = fileHandle.extension()

    override val isDirectory: Boolean
        get() = fileHandle.isDirectory

    override fun exists(): Boolean = fileHandle.exists()

    override fun readBytes(): ByteArray = fileHandle.readBytes()

    override fun readText(): String = fileHandle.readString()

    override fun writeBytes(bytes: ByteArray, append: Boolean) {
        fileHandle.writeBytes(bytes, append)
    }

    override fun writeText(text: String, append: Boolean) {
        fileHandle.writeString(text, append)
    }

    override fun list(): List<WritableAssetEntry> = fileHandle.list().map(::GdxAssetEntry)

    fun unwrap(): FileHandle = fileHandle
}
