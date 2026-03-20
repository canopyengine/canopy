package io.canopy.engine.data.saving

import io.canopy.engine.data.assets.WritableAssetEntry

/**
 * In-memory implementation of [WritableAssetEntry].
 *
 * Useful for tests that should avoid disk IO.
 */
class InMemoryAssetEntry(
    override val path: String,
    initialText: String = "",
    override val isDirectory: Boolean = false,
    private val children: MutableList<InMemoryAssetEntry> = mutableListOf(),
) : WritableAssetEntry {

    private var bytes: ByteArray = initialText.encodeToByteArray()

    override val name: String
        get() = path.substringAfterLast('/').substringAfterLast('\\')

    override val extension: String
        get() = name.substringAfterLast('.', "")

    override fun exists(): Boolean = true

    override fun readText(): String = bytes.decodeToString()

    override fun readBytes(): ByteArray = bytes.copyOf()

    override fun writeText(text: String, append: Boolean) {
        bytes = if (append) {
            readText().plus(text).encodeToByteArray()
        } else {
            text.encodeToByteArray()
        }
    }

    override fun writeBytes(bytes: ByteArray, append: Boolean) {
        this.bytes = if (append) {
            this.bytes + bytes
        } else {
            bytes.copyOf()
        }
    }

    override fun list(): List<WritableAssetEntry> = if (isDirectory) children.toList() else emptyList()

    fun addChild(child: InMemoryAssetEntry) {
        require(isDirectory) { "Cannot add child to non-directory entry: $path" }
        children += child
    }

    fun clearChildren() {
        children.clear()
    }
}
