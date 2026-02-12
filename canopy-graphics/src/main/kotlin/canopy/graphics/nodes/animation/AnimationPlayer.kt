package canopy.graphics.nodes.animation

import canopy.core.nodes.core.Behavior
import canopy.core.nodes.core.Node
import com.badlogic.gdx.math.Vector2
import ktx.log.logger
import kotlin.math.abs

class AnimationPlayer(
    name: String,
    script: (node: AnimationPlayer) -> Behavior<AnimationPlayer>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1f, 1f),
    rotation: Float = 0f,
    groups: MutableList<String> = mutableListOf(),
    block: AnimationPlayer.() -> Unit = {},
) : Node<AnimationPlayer>(
        name,
        script,
        position,
        scale,
        rotation,
        groups,
        block,
    ) {
    private val animations = mutableMapOf<String, Animation>()
    private val logger = logger<AnimationPlayer>()

    private var time = 0f
    private var direction = 1
    private var playing = true

    // Signals
    val onAnimationChanged = createSignal<Animation>()
    val onAnimationFinished = createSignal<Animation?>()

    var currentAnimation: Animation? = null
        private set

    val normalizedTime: Float
        get() {
            val anim = currentAnimation ?: return 0f
            return if (anim.length == 0f) 0f else time / anim.length
        }

    init {
        if (!sceneManager.hasSystem(AnimationSystem::class)) {
            logger.error {
                """
                [ANIMATION PLAYER]
                No AnimationSystem registered.
                Animations will not update!
                """.trimIndent()
            }
        }
    }

    fun addAnimation(animation: Animation) {
        animation.finalizeTracks()
        animations[animation.name] = animation
    }

    fun play(name: String) {
        val anim = animations[name] ?: error("Animation<*> '$name' not found")

        currentAnimation = anim
        playing = true

        when (anim.playMode) {
            PlayMode.REVERSED,
            PlayMode.LOOP_REVERSED,
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
                PlayMode.NORMAL -> {
                    time = anim.length
                    playing = false
                }

                PlayMode.REVERSED -> {
                    time = 0f
                    playing = false
                }

                PlayMode.LOOP -> {
                    anim.loopback()
                    time = remaining % anim.length
                }

                PlayMode.LOOP_REVERSED -> {
                    anim.loopback()
                    time = anim.length - (remaining % anim.length)
                }

                PlayMode.LOOP_PINGPONG -> {
                    anim.loopback()
                    direction *= -1
                }
                else -> {}
            }
        }
        onAnimationFinished.emit(currentAnimation)
    }
}
