package io.canopy.engine.graphics.nodes.animation.tracks

class ActionTrack(block: ActionTrack.() -> Unit = {}) : Track<() -> Unit>() {
    companion object {
        private val currentParent = ThreadLocal.withInitial<ActionTrack?> { null }
    }

    init {
        animation.addTrack(this)

        val old = currentParent.get()
        currentParent.set(this)

        block()

        currentParent.set(old)
    }

    override fun collectUpdates(prevTime: Float, time: Float): List<() -> Unit> = keys
        .filter { it.time > prevTime && it.time <= time }
        .map { it.value }
}
