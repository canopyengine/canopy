package canopy.graphics.nodes.animation.tracks

import kotlin.reflect.KMutableProperty0

class PropertyTrack<P>(val property: KMutableProperty0<P>, block: PropertyTrack<P>.() -> Unit = {}) : Track<P>() {
    companion object {
        private val currentParent = ThreadLocal.withInitial<PropertyTrack<*>?> { null }
    }

    init {
        animation.addTrack(this)

        val old = currentParent.get()
        currentParent.set(this)

        block()

        currentParent.set(old)
    }

    override fun collectUpdates(prevTime: Float, time: Float): List<() -> Unit> {
        val interpolatedValue = interpolateKeys(prevTime, time)
        return listOf { property.set(interpolatedValue ?: property.get()) }
    }
}
