package io.canopy.backends.desktop.graphics.nodes.animation

class Key<T> internal constructor(val time: Float, val value: T) {
    var executed: Boolean = false
}
