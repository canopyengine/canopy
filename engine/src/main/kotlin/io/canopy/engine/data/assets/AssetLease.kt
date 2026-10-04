package io.canopy.engine.data.assets

/**
 * One explicit ownership of a shared resource. Access and release are confined to the serialized lifecycle thread.
 * [value] is borrowed and fails after closure or manager shutdown; retaining a raw value does not extend ownership.
 * Closing is idempotent. Use Kotlin `use` for application or screen scopes.
 */
class AssetLease<T : CanopyAsset> private constructor(
    private var manager: ResourceManager?,
    private var entry: ResourceEntry?,
) : AutoCloseable {
    internal companion object {
        fun <T : CanopyAsset> create(manager: ResourceManager, entry: ResourceEntry): AssetLease<T> =
            AssetLease(manager, entry)
    }

    /** Borrowed resource; throws [IllegalStateException] after lease closure or manager shutdown. */
    @Suppress("UNCHECKED_CAST")
    val value: T get() = checkNotNull(manager) { "Asset lease is closed" }.read(checkNotNull(entry)) as T

    /** Releases ownership exactly once, even when final disposal fails. */
    override fun close() {
        val previous = entry ?: return
        val owner = manager
        entry = null
        manager = null
        owner?.release(previous)
    }
}
