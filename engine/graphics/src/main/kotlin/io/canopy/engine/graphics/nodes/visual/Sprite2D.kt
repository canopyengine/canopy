package io.canopy.engine.graphics.nodes.visual

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.graphics.systems.RenderSystem
import ktx.log.logger

class Sprite2D(
    name: String,
    // Node specific props
    texture: Texture,
    // Node base props
    block: Node<*>.() -> Unit = {},
) : Node<Sprite2D>(
    name,
    block
) {
    val sprite = Sprite(texture)
    private val logger = logger<Sprite2D>()

    init {
        if (!sceneManager.hasSystem(RenderSystem::class)) {
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
