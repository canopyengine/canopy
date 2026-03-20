package io.canopy.engine.data.assets

interface WritableAssetEntry : AssetEntry {
    fun writeBytes(bytes: ByteArray, append: Boolean = false)
    fun writeText(text: String, append: Boolean = false)
}
