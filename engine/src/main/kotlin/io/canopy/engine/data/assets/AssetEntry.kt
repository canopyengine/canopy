package io.canopy.engine.data.assets

interface AssetEntry {
    val path: String
    val name: String
    val extension: String
    val isDirectory: Boolean

    fun exists(): Boolean
    fun readBytes(): ByteArray
    fun readText(): String
    fun list(): List<AssetEntry>
}
