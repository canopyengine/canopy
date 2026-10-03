package io.canopy.engine.data.assets

/** Backend-neutral file or directory handle. File operations retain backend-specific error behavior. */
interface AssetEntry {
    /** Path interpreted relative to the entry source. */
    val path: String

    /** Final path component, including its extension. */
    val name: String

    /** File extension without the leading dot. */
    val extension: String

    /** Whether this entry represents a directory. */
    val isDirectory: Boolean

    /** Checks whether the entry currently exists. */
    fun exists(): Boolean

    /** Reads file contents into a new byte array. */
    fun readBytes(): ByteArray

    /** Reads file contents using the backend text encoding. */
    fun readText(): String

    /** Lists direct children using backend directory semantics. */
    fun list(): List<AssetEntry>
}
