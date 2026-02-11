package anchors.framework.nodes.types.animation.tracks

import anchors.framework.misc.interpolate
import anchors.framework.nodes.types.animation.Animation
import anchors.framework.nodes.types.animation.Key

abstract class Track<T> {
    protected val keys: MutableList<Key<T>> = mutableListOf()
    val maxLength: Float = keys.maxOfOrNull { it.time } ?: 0f

    val keyCount: Int
        get() = keys.size

    protected val animation: Animation
        get() =
            Animation.currentParent.get()
                ?: error("Track must be inside Animation block")

    internal open fun onAddTrack() = Unit

    /**
     * Add new key
     */
    fun key(
        time: Float,
        value: T,
    ) {
        check(keys.none { it.time == time }) { "Key $time already exists" }
        keys += Key(time, value)
    }

    /**
     * Collects side effects that should happen between prevTime and time.
     * Returned lambdas will be executed later.
     */
    abstract fun collectUpdates(
        prevTime: Float,
        time: Float,
    ): List<() -> Unit>

    fun loopback() = keys.forEach { it.executed = false }

    fun interpolateKeys(
        prevTime: Float,
        time: Float,
    ): T? {
        if (keys.isEmpty()) return null

        val prevKey = keys.lastOrNull { it.time <= time } ?: return null
        val nextKey = keys.firstOrNull { it.time > time } ?: prevKey

        val factor =
            if (prevKey === nextKey) {
                0f
            } else {
                (time - prevKey.time) / (nextKey.time - prevKey.time)
            }

        val prevValue = prevKey.value
        val nextValue = nextKey.value

        val value = prevValue.interpolate(nextValue, factor)

        return value
    }
}
