package io.canopy.platforms.desktop.graphics.systems

import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.engine.utils.UnstableApi
import io.canopy.platforms.desktop.graphics.nodes.animation.AnimationPlayer
import ktx.log.logger

@UnstableApi
class AnimationSystem :
    TreeSystem(
        UpdatePhase.FramePre,
        1,
        _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.AnimationPlayer::class
    ) {
    private val logger = logger<AnimationSystem>()
    private val queuedUpdates = mutableListOf<() -> Unit>()

    override fun processNode(node: Node<*>, delta: Float) {
        val player = node as io.canopy.platforms.desktop.graphics.nodes.animation.AnimationPlayer
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
