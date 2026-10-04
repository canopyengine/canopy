package io.canopy.engine.data.assets

import kotlin.reflect.KClass
import java.util.Collections

/**
 * Immutable resource identity: exact declared [type], raw [path], [source], and copied string [parameters].
 * Paths are not normalized; generic type arguments are erased by the JVM. Construct with [assetKey].
 * The parameter map is defensively copied and unmodifiable; equality compares all identity values.
 */
class AssetKey<T : CanopyAsset> private constructor(
    /** Exact declared resource type used for loader selection. */
    val type: KClass<T>,
    /** Backend/application path without normalization. */
    val path: String,
    /** Backend resolution location. */
    val source: FileSource,
    parameters: Map<String, String>,
) {
    /** Immutable copy of loader-specific string options. */
    val parameters: Map<String, String> = run {
        val snapshot = LinkedHashMap(parameters)
        // JVM callers can bypass generic bounds; metadata must never retain arbitrary objects.
        (snapshot as Map<*, *>).forEach { (key, value) ->
            require(key is String && value is String) { "Asset parameters must contain nonnull String keys and values" }
        }
        Collections.unmodifiableMap(snapshot)
    }

    @PublishedApi
    internal companion object {
        fun <T : CanopyAsset> create(
            type: KClass<T>,
            path: String,
            source: FileSource,
            parameters: Map<String, String>,
        ): AssetKey<T> = AssetKey(type, path, source, parameters)
    }

    override fun equals(other: Any?): Boolean = other is AssetKey<*> &&
        type == other.type &&
        path == other.path &&
        source == other.source &&
        parameters == other.parameters

    override fun hashCode(): Int = 31 * (31 * (31 * type.hashCode() + path.hashCode()) + source.hashCode()) +
        parameters.hashCode()

    override fun toString(): String = "AssetKey<${type.qualifiedName}>('$path', $source, $parameters)"
}

/** Creates an immutable exact-type resource identity, copying loader-specific string options. */
inline fun <reified T : CanopyAsset> assetKey(
    path: String,
    source: FileSource = FileSource.Internal,
    parameters: Map<String, String> = emptyMap(),
): AssetKey<T> = AssetKey.create(T::class, path, source, parameters)
