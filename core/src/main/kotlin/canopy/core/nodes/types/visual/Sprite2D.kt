package anchors.framework.nodes.types.visual

import anchors.framework.nodes.core.Behavior
import anchors.framework.nodes.core.Node
import anchors.framework.systems.RenderSystem
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.Vector2
import ktx.log.logger

class Sprite2D(
    name: String,
    // Node specific props
    val texture: Texture,
    // Node base props
    script: (node: Sprite2D) -> Behavior<Sprite2D>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2(1f, 1f),
    rotation: Float = 0f,
    groups: MutableList<String> = mutableListOf(),
    // DSL
    block: Node<*>.() -> Unit = {},
) : Node<Sprite2D>(
        name,
        script,
        position,
        scale,
        rotation,
        groups,
        block,
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
