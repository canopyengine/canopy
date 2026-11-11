package io.canopy.engine.app

/**
 * Native frame dimensions supplied by the host. Units belong to the platform: terminal cells or graphical pixels.
 * Zero dimensions represent an unavailable drawable extent; negative dimensions are rejected.
 */
data class FrameSize(val width: Int, val height: Int) {
    init {
        require(width >= 0 && height >= 0) { "Frame dimensions must be non-negative" }
    }
}
