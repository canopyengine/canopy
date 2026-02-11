package anchors.framework.systems

import anchors.framework.nodes.core.GlobalNodeSystem
import anchors.framework.nodes.core.Node
import anchors.framework.nodes.core.UpdatePhase
import anchors.framework.nodes.types.animation.AnimationPlayer
import ktx.log.logger

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
