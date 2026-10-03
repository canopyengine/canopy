package io.canopy.platforms.desktop.graphics.nodes.animation

/** Animation key containing a time, a value, and mutable execution bookkeeping. */
class Key<T> internal constructor(val time: Float, val value: T) {
    var executed: Boolean = false
}
