package io.canopy.engine.data.assets

import kotlin.reflect.KProperty
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node

/**
 * Final read-only asset delegate retaining only immutable key metadata.
 * Node and behavior reads share one engine-owned lease per node/key, including [resources] preloads.
 * First acquisition requires a valid entered node, and is rejected during exit. Already owned values remain
 * readable during normal exit callbacks; destruction rejects every read with NodeDestroyedException.
 * Delegates do not keep resolved resources or owners alive. All operations run on the serialized lifecycle thread.
 */
class AssetDelegate<T : CanopyAsset> private constructor(private val key: AssetKey<T>) {
    internal companion object {
        fun <T : CanopyAsset> create(key: AssetKey<T>): AssetDelegate<T> = AssetDelegate(key)
    }

    /** Returns the borrowed value from this node's shared owner slot. */
    operator fun getValue(thisRef: Node<*>, property: KProperty<*>): T = ownedAsset(thisRef, key)

    /** Returns the borrowed value using the behavior's node; a behavior without a node cannot acquire. */
    operator fun getValue(thisRef: Behavior<*>, property: KProperty<*>): T =
        ownedAsset(checkNotNull(thisRef.dependencyOwner()) { "Behavior '${property.name}' has no resource owner" }, key)
}

/** Creates a read-only node/behavior delegate for this immutable resource identity. */
fun <T : CanopyAsset> asset(key: AssetKey<T>): AssetDelegate<T> = AssetDelegate.create(key)

/** Shorthand for [asset] with an exact-type immutable key and defensively copied string options. */
inline fun <reified T : CanopyAsset> asset(
    path: String,
    source: FileSource = FileSource.Internal,
    parameters: Map<String, String> = emptyMap(),
): AssetDelegate<T> = asset(assetKey(path, source, parameters))

/**
 * Immediate preload handler sharing the node's resource slots with delegates and behaviors.
 * Each successful preload stays owned if a later one fails; this handler is not an implicit transaction.
 */
class ResourcePreloader internal constructor(private val owner: Node<*>) {
    /** Acquires once for this node/key; requires valid entered membership when no slot exists. */
    fun <T : CanopyAsset> preload(key: AssetKey<T>) {
        ownedAsset(owner, key)
    }
}

/** Immediately preloads resources on the lifecycle thread, owned until this node's exit or destruction. */
fun Node<*>.resources(block: ResourcePreloader.() -> Unit) {
    requireValid("preload resources")
    ResourcePreloader(this).block()
}

/** Immediately preloads resources using the behavior's node; a behavior without a node has no owner. */
fun Behavior<*>.resources(block: ResourcePreloader.() -> Unit) {
    checkNotNull(dependencyOwner()) { "Behavior has no resource owner" }.resources(block)
}

private data class ResourceSlotKey(val key: AssetKey<*>)

private fun <T : CanopyAsset> ownedAsset(owner: Node<*>, key: AssetKey<T>): T =
    owner.lifetimeSlot(ResourceSlotKey(key)) { manager<ResourceManager>().acquire(key) }.value
