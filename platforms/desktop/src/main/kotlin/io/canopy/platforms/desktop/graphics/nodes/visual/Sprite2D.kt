package io.canopy.platforms.desktop.graphics.nodes.visual

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import io.canopy.engine.core.nodes.Node2D
import io.canopy.platforms.desktop.graphics.systems.RenderSystem
import ktx.log.logger

class Sprite2D(
    name: String,
    // Node specific props
    texture: Texture,
    // Node base props
    block: Sprite2D.() -> Unit = {},
) : Node2D<Sprite2D>(
    name,
    block
) {
    val sprite = Sprite(texture)
    private val logger = logger<Sprite2D>()

    init {
        if (!sceneManager.hasSystem(
                _root_ide_package_.io.canopy.platforms.desktop.graphics.systems.RenderSystem::class
            )
        ) {
            logger.error {
                """

                [VISUAL NODE]
                The rendering pipeline wasn't setup - no visual elements will be rendered!

                To fix it: register it into a Scene Manager!

                """.trimIndent()
            }
        }
    }
}
