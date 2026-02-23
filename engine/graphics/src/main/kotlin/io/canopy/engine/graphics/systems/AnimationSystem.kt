package io.canopy.engine.graphics.systems

import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.nodes.core.TreeSystem
import io.canopy.engine.graphics.nodes.animation.AnimationPlayer
import io.canopy.engine.utils.UnstableApi
import ktx.log.logger

@UnstableApi
class AnimationSystem :
    TreeSystem(
        UpdatePhase.FramePre,
        1,
        AnimationPlayer::class
    ) {
    private val logger = logger<AnimationSystem>()
    private val queuedUpdates = mutableListOf<() -> Unit>()

    override fun processNode(node: Node<*>, delta: Float) {
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
