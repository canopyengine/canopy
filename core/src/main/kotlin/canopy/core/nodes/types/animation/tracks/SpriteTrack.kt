package anchors.framework.nodes.types.animation.tracks

import anchors.framework.nodes.types.visual.AnimatedSprite2D
import com.badlogic.gdx.graphics.g2d.TextureRegion

class SpriteTrack<T : TextureRegion>(
    val sprite: AnimatedSprite2D<T>,
    val frames: Array<T>,
    block: SpriteTrack<T>.() -> Unit = {},
) : Track<T>() {
    companion object {
        private val currentParent = ThreadLocal.withInitial<SpriteTrack<*>?> { null }
    }

    init {
        animation.addTrack(this)

        val old = currentParent.get()
        currentParent.set(this)
        block()
        currentParent.set(old)
    }

    fun keyFrame(
        time: Float,
        frameIdx: Int,
    ) {
        require(frameIdx in 0 until frames.size) { "frameIdx $frameIdx out of range" }
        key(time, frames[frameIdx])
    }

    override fun collectUpdates(
        prevTime: Float,
        time: Float,
    ): List<() -> Unit> {
        val interpolatedValue = interpolateKeys(prevTime, time)
        return listOf { sprite.currFrame = interpolatedValue ?: sprite.currFrame }
    }
}
