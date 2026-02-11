package anchors.framework.systems

import anchors.framework.nodes.core.GlobalNodeSystem
import anchors.framework.nodes.core.UpdatePhase
import anchors.framework.nodes.types.camera.Camera2D
import anchors.framework.nodes.types.visual.AnimatedSprite2D
import anchors.framework.nodes.types.visual.Sprite2D
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.graphics.use
import ktx.log.logger

class RenderSystem(
    worldWidth: Float,
    worldHeight: Float,
) : GlobalNodeSystem(
        UpdatePhase.FrameAfterScene,
        Sprite2D::class,
        AnimatedSprite2D::class,
    ) {
    private val logger = logger<RenderSystem>()
    private val batch = SpriteBatch()
    private val viewport = FitViewport(worldWidth, worldHeight)

    private fun updateViewportCamera(cam: Camera2D?) {
        if (cam != null) {
            viewport.camera = cam.camera
        }
    }

    private fun resizeViewport(
        width: Int,
        height: Int,
    ) {
        viewport.update(width, height, true)
        sceneManager.activeCamera?.resize(viewport.worldWidth, viewport.worldHeight)
    }

    override fun afterProcess(delta: Float) {
        val camera = sceneManager.activeCamera ?: return
        batch.projectionMatrix = camera.camera.combined

        batch.use { b ->
            matchingNodes
                .sortedByDescending { it.globalPosition.y }
                .forEach { node ->
                    when (node) {
                        is Sprite2D -> {
                            val sprite = node.sprite
                            sprite.setCenter(node.globalPosition.x, node.globalPosition.y)
                            sprite.draw(b)
                        }

                        is AnimatedSprite2D<*> -> {
                            node.currFrame?.let {
                                batch.draw(
                                    it,
                                    node.globalPosition.x,
                                    node.globalPosition.y,
                                    it.regionWidth * node.scale.x,
                                    it.regionHeight * node.scale.y,
                                )
                            }
                        }
                    }
                }
        }
    }

    override fun onSystemInit() {
        sceneManager.onCameraChanged.connect { cam -> updateViewportCamera(cam) }
        sceneManager.onResize.connect { w, h -> resizeViewport(w, h) }
        viewport.apply()
    }

    override fun onSystemClose() {
        super.onSystemClose()
        batch.dispose()
    }
}
