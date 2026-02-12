package canopy.graphics.systems

import canopy.core.nodes.core.GlobalNodeSystem
import canopy.core.nodes.core.Node
import canopy.core.nodes.core.UpdatePhase
import canopy.graphics.nodes.animation.AnimationPlayer
import canopy.utils.UnstableApi
import ktx.log.logger

@UnstableApi
class AnimationSystem :
    GlobalNodeSystem(
        UpdatePhase.AnimationBeforeScene,
        AnimationPlayer::class,
    ) {
    private val logger = logger<AnimationSystem>()
    private val queuedUpdates = mutableListOf<() -> Unit>()

    override fun processNode(
        node: Node<*>,
        delta: Float,
    ) {
        val player = node as AnimationPlayer
        val anim = player.currentAnimation ?: return

        val prevTime = player.normalizedTime
        player.update(delta)

        anim.tracks.forEach { track ->
            queuedUpdates += track.collectUpdates(prevTime, player.normalizedTime)
        }
    }

    override fun afterProcess(delta: Float) {
        queuedUpdates.forEach { it() }
        queuedUpdates.clear()
    }
}
