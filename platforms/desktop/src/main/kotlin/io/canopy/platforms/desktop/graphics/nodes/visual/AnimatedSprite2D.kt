package io.canopy.platforms.desktop.graphics.nodes.visual

import com.badlogic.gdx.graphics.g2d.TextureRegion
import io.canopy.engine.core.nodes.Node2D
import io.canopy.platforms.desktop.graphics.systems.RenderSystem
import ktx.log.logger

class AnimatedSprite2D<T : TextureRegion>(
    name: String,
    // Node base props
    block: AnimatedSprite2D<*>.() -> Unit = {},
) : Node2D<AnimatedSprite2D<T>>(
    name,
    block
) {
    var currFrame: T? = null

    private val logger = logger<AnimatedSprite2D<T>>()

    init {
        if (!sceneManager.hasSystem(
                _root_ide_package_.io.canopy.platforms.desktop.graphics.systems.RenderSystem::class
            )
        ) {
            logger.error {
                """
                The rendering pipeline wasn't setup - no visual elements will be rendered!
                Register RenderSystem in the SceneManager.
                """.trimIndent()
            }
        }
    }
}
