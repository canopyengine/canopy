package io.canopy.engine.data.assets

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.nodes.CleanupFailures

/**
 * Shares synchronous exact-type resources on the serialized engine lifecycle thread.
 * [idleTimeout] must be finite and nonnegative; zero disposes at final release, otherwise monotonic real time
 * starts at final release. Frames evict expired entries even while gameplay is paused; late acquisition evicts too.
 * Configure explicitly in the app's manager builder; no default loaders or backend dependencies are installed.
 * Available before its first [onEnter], which preserves startup acquisitions. Entry after shutdown opens a fresh
 * cache generation with retained loaders. Shutdown invalidates every lease and attempts reverse-load-order disposal.
 */
class ResourceManager(private val idleTimeout: Duration = Duration.ZERO) : Manager {
    private val loaders = mutableMapOf<KClass<*>, AssetLoader<*>>()
    private val entries = linkedMapOf<AssetKey<*>, ResourceEntry>()
    private val loading = mutableSetOf<AssetKey<*>>()
    private var active = true
    private var stopping = false
    private var disposalDepth = 0
    private var generation = 0L
    internal var clock: () -> Long = System::nanoTime

    init {
        require(idleTimeout.isFinite() && idleTimeout >= Duration.ZERO) {
            "Asset idle timeout must be finite and nonnegative"
        }
    }

    /** Registers one loader for this exact type; duplicates and teardown/disposal-time registration are rejected. */
    fun <T : CanopyAsset> registerLoader(type: KClass<T>, loader: AssetLoader<T>) {
        check(!stopping && disposalDepth == 0) { "Cannot register asset loaders during cleanup" }
        require(type !in loaders) { "Asset loader already registered for ${type.qualifiedName}" }
        loaders[type] = loader
    }

    /** Reified overload of [registerLoader]. */
    inline fun <reified T : CanopyAsset> registerLoader(loader: AssetLoader<T>) = registerLoader(T::class, loader)

    /**
     * Acquires explicit ownership, loading synchronously once per key. Use [AssetLease.close] to release it.
     * Missing loaders, recursive same-key loads and cleanup-time acquisition fail descriptively.
     * Loader errors propagate; loaders own cleanup of partial allocations.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : CanopyAsset> acquire(key: AssetKey<T>): AssetLease<T> {
        check(active && !stopping && disposalDepth == 0) { "Cannot acquire assets after shutdown or during disposal" }
        check(key !in loading) { "Recursive asset load for $key" }
        entries[key]?.let { existing ->
            if (!expired(existing)) return lease(existing)
            dispose(existing)
        }
        val loader = loaders[key.type] as? AssetLoader<T>
            ?: throw IllegalStateException("No asset loader registered for ${key.type.qualifiedName}")
        val startedGeneration = generation
        loading += key
        try {
            val asset = loader.load(key)
            if (!active || stopping || startedGeneration != generation || !key.type.isInstance(asset)) {
                val failure = IllegalStateException("Asset load invalidated by shutdown or returned wrong type: $key")
                try {
                    closeValue(asset)
                } catch (error: Throwable) {
                    if (error !== failure) failure.addSuppressed(error)
                }
                throw failure
            }
            val entry = ResourceEntry(key, asset, generation)
            entries[key] = entry
            return lease(entry)
        } finally {
            loading -= key
        }
    }

    private fun <T : CanopyAsset> lease(entry: ResourceEntry): AssetLease<T> {
        entry.owners++
        entry.idleSince = null
        return AssetLease.create(this, entry)
    }

    internal fun read(entry: ResourceEntry): CanopyAsset {
        check(active && !stopping && entry.generation == generation) { "Asset lease belongs to a stopped manager" }
        return checkNotNull(entry.asset) { "Asset lease is invalid" }
    }

    internal fun release(entry: ResourceEntry) {
        if (entry.asset == null || entry.generation != generation || !active) return
        check(entry.owners > 0) { "Asset ownership already released" }
        entry.owners--
        if (entry.owners == 0) {
            entry.idleSince = clock()
            if (idleTimeout == Duration.ZERO) dispose(entry)
        }
    }

    private fun expired(entry: ResourceEntry): Boolean = entry.idleSince?.let {
        (clock() - it).nanoseconds >= idleTimeout
    } ?: false

    private fun dispose(entry: ResourceEntry) {
        val asset = entry.asset ?: return
        entries.remove(entry.key)
        entry.asset = null
        entry.owners = 0
        closeValue(asset)
    }

    private fun closeValue(asset: CanopyAsset) {
        disposalDepth++
        try {
            asset.close()
        } finally {
            disposalDepth--
        }
    }

    /** Preserves initial startup ownership; only entry after completed shutdown creates a new generation. */
    override fun onEnter() {
        check(!stopping && disposalDepth == 0) { "Cannot enter resource manager during cleanup" }
        if (!active) {
            generation++
            active = true
        }
    }

    /** Evicts idle entries using monotonic elapsed time, ignoring gameplay [delta] and pause. */
    override fun onUpdate(delta: Float) {
        if (!active || stopping) return
        check(disposalDepth == 0) { "Cannot sweep assets during disposal" }
        val failures = CleanupFailures()
        entries.values.toList().forEach { entry ->
            if (entry.asset != null && expired(entry)) failures.attempt { dispose(entry) }
        }
        failures.rethrow()
    }

    /** Invalidates all leases before callbacks, then attempts every disposal in reverse successful-load order. */
    override fun onExit() {
        if (!active || stopping) return
        check(disposalDepth == 0) { "Cannot shut down resource manager during disposal" }
        stopping = true
        active = false
        val values = entries.values.toList().asReversed().mapNotNull { entry ->
            val asset = entry.asset
            entry.asset = null
            entry.owners = 0
            asset
        }
        entries.clear()
        val failures = CleanupFailures()
        try {
            values.forEach { asset -> failures.attempt { closeValue(asset) } }
        } finally {
            stopping = false
        }
        failures.rethrow()
    }
}

/** Engine-owned cache state; leases cannot retain disposed values after invalidation. */
internal class ResourceEntry(val key: AssetKey<*>, var asset: CanopyAsset?, val generation: Long) {
    var owners = 0
    var idleSince: Long? = null
}
