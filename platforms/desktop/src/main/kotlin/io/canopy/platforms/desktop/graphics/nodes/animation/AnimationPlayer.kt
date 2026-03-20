package io.canopy.platforms.desktop.graphics.nodes.animation

import kotlin.math.abs
import io.canopy.engine.core.flow.events.event
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.utils.UnstableApi
import io.canopy.platforms.desktop.graphics.systems.AnimationSystem

@UnstableApi
class AnimationPlayer(name: String, block: AnimationPlayer.() -> Unit = {}) :
    Node<AnimationPlayer>(
        name,
        block
    ) {
    private val animations = mutableMapOf<String, io.canopy.platforms.desktop.graphics.nodes.animation.Animation>()
    private val logger = logger<AnimationPlayer>()

    private var time = 0f
    private var direction = 1
    private var playing = true

    // Signals
    val onAnimationChanged = event<io.canopy.platforms.desktop.graphics.nodes.animation.Animation>()
    val onAnimationFinished = event<io.canopy.platforms.desktop.graphics.nodes.animation.Animation?>()

    var currentAnimation: io.canopy.platforms.desktop.graphics.nodes.animation.Animation? = null
        private set

    val normalizedTime: Float
        get() {
            val anim = currentAnimation ?: return 0f
            return if (anim.length == 0f) 0f else time / anim.length
        }

    init {
        if (!sceneManager.hasSystem(
                _root_ide_package_.io.canopy.platforms.desktop.graphics.systems.AnimationSystem::class
            )
        ) {
            logger.error {
                """
                [ANIMATION PLAYER]
                No AnimationSystem registered.
                Animations will not update!
                """.trimIndent()
            }
        }
    }

    fun addAnimation(animation: io.canopy.platforms.desktop.graphics.nodes.animation.Animation) {
        animation.finalizeTracks()
        animations[animation.name] = animation
    }

    fun play(name: String) {
        val anim = animations[name] ?: error("Animation<*> '$name' not found")

        currentAnimation = anim
        playing = true

        when (anim.playMode) {
            _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.REVERSED,
            _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.LOOP_REVERSED,
            -> {
                time = anim.length
                direction = -1
            }

            else -> {
                time = 0f
                direction = 1
            }
        }

        onAnimationChanged.emit(anim)
    }

    fun stop() {
        playing = false
    }

    fun update(delta: Float) {
        val anim = currentAnimation ?: return
        if (!playing || anim.length <= 0f || delta == 0f) return

        var remaining = abs(delta)

        while (remaining > 0f && playing) {
            val toEdge =
                if (direction > 0) {
                    anim.length - time
                } else {
                    time
                }

            if (remaining < toEdge) {
                time += remaining * direction
                break
            }

            // hit edge
            time += toEdge * direction
            remaining -= toEdge

            when (anim.playMode) {
                _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.NORMAL -> {
                    time = anim.length
                    playing = false
                }

                _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.REVERSED -> {
                    time = 0f
                    playing = false
                }

                _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.LOOP -> {
                    anim.loopback()
                    time = remaining % anim.length
                }

                _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.LOOP_REVERSED -> {
                    anim.loopback()
                    time = anim.length - (remaining % anim.length)
                }

                _root_ide_package_.io.canopy.platforms.desktop.graphics.nodes.animation.PlayMode.LOOP_PINGPONG -> {
                    anim.loopback()
                    direction *= -1
                }

                else -> {}
            }
        }
        onAnimationFinished.emit(currentAnimation)
    }
}
