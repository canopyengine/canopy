package io.canopy.engine.graphics.nodes.visual

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import io.canopy.engine.core.nodes.core.Behavior
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.graphics.systems.RenderSystem
import ktx.log.logger

class AnimatedSprite2D<T : TextureRegion>(
    name: String,
    // Node base props
    script: (node: AnimatedSprite2D<T>) -> Behavior<AnimatedSprite2D<T>>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1f, 1f),
    rotation: Float = 0f,
    groups: MutableList<String> = mutableListOf(),
    block: Node<*>.() -> Unit = {},
) : Node<AnimatedSprite2D<T>>(
    name,
    script,
    position,
    scale,
    rotation,
    groups,
    block
) {
    var currFrame: T? = null

    private val logger = logger<AnimatedSprite2D<T>>()

    init {
        if (!sceneManager.hasSystem(RenderSystem::class)) {
            logger.error {
                """
                The rendering pipeline wasn't setup - no visual elements will be rendered!
                Register RenderSystem in the SceneManager.
                """.trimIndent()
            }
        }
    }
}
