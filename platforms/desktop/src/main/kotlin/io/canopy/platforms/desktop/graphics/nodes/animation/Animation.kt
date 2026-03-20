package io.canopy.platforms.desktop.graphics.nodes.animation

import io.canopy.engine.graphics.nodes.animation.tracks.Track

typealias PlayMode = com.badlogic.gdx.graphics.g2d.Animation.PlayMode

@Suppress("UNCHECKED_CAST")
open class Animation(
    val name: String,
    maxLength: Float? = null,
    val playMode: io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode = _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.NORMAL,
    block: Animation.() -> Unit = {},
) {
    constructor(
        name: String,
        playMode: io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode = _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.NORMAL,
        block: Animation.() -> Unit = {},
    ) : this(name, null, playMode, block)

    private var _length: Float? = maxLength

    open var length: Float
        get() = _length ?: tracks.maxOfOrNull { it.maxLength } ?: 0f
        set(value) {
            _length = value
        }

    val tracks = mutableListOf<Track<*>>()

    companion object {
        val currentParent: ThreadLocal<Animation?> = ThreadLocal.withInitial { null }
    }

    init {
        val old = currentParent.get()
        currentParent.set(this)

        block(this)

        currentParent.set(old)
    }

    fun finalizeTracks() {
        tracks.forEach { it.onAddTrack() }
    }

    fun addTrack(track: Track<*>) {
        tracks += track
    }

    fun loopback() {
        tracks.forEach { it.loopback() }
    }
}
