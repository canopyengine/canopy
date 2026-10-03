package io.canopy.engine.data.assets

/** An asset entry that supports backend file writes. */
interface WritableAssetEntry : AssetEntry {
    /** Writes bytes, replacing existing contents unless append is true. */
    fun writeBytes(bytes: ByteArray, append: Boolean = false)

    /** Writes text with the backend encoding, replacing existing contents unless append is true. */
    fun writeText(text: String, append: Boolean = false)
}
