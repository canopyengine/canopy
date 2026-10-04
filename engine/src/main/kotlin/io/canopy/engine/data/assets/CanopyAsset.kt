package io.canopy.engine.data.assets

/**
 * Backend-neutral loaded resource. [close] releases its backend or application resources on the lifecycle thread.
 * Values from delegates and leases are borrowed: consumers release their ownership, not this shared value.
 * Immutable parsed values may implement [close] as a no-op. Loaders clean up their own partial allocations on failure.
 */
interface CanopyAsset : AutoCloseable {
    /** Releases this resource; invoked once by its owning resource cache. */
    override fun close()
}

/** Synchronously constructs a resource for the exact declared type and validates its options on the lifecycle thread. */
fun interface AssetLoader<T : CanopyAsset> {
    /**
     * Loads [key], transferring exclusive disposal ownership of the returned instance to this cache entry.
     * Separate uncached loads, keys or managers must return independently disposable values, not a shared singleton.
     * Failures propagate and do not install a cached value.
     */
    fun load(key: AssetKey<T>): T
}
