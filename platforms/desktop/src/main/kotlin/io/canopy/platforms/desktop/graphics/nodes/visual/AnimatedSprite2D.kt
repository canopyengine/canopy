package io.canopy.platforms.desktop.graphics.nodes.visual

import com.badlogic.gdx.graphics.g2d.TextureRegion
import io.canopy.engine.core.nodes.Node2D
import io.canopy.platforms.desktop.graphics.systems.RenderSystem
import ktx.log.logger

/**
 * Extensible node exposing an optional texture region used as the current frame by RenderSystem.
 * @param N Concrete node type exposed to its initialization block.
 * @param block Runs once after nodeInit, before entry callbacks; not replayed on reentry.
 */
open class AnimatedSprite2D<N : AnimatedSprite2D<N, T>, T : TextureRegion>(
    name: String,
    // Node base props
    block: N.() -> Unit = {},
) : Node2D<N>(
    name,
    block
) {
    var currFrame: T? = null

    private val logger = logger<AnimatedSprite2D<*, T>>()

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
