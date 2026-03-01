package io.canopy.engine.graphics.nodes.visual

import com.badlogic.gdx.graphics.g2d.TextureRegion
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.graphics.systems.RenderSystem
import ktx.log.logger

class AnimatedSprite2D<T : TextureRegion>(
    name: String,
    // Node base props
    block: Node<*>.() -> Unit = {},
) : Node<AnimatedSprite2D<T>>(
    name,
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
