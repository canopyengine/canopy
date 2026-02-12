package canopy.graphics.systems

import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.core.GlobalNodeSystem
import canopy.core.nodes.core.UpdatePhase
import canopy.graphics.managers.CameraManager
import canopy.graphics.nodes.visual.AnimatedSprite2D
import canopy.graphics.nodes.visual.Sprite2D
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
    // Misc
    private val logger = logger<RenderSystem>()
    private val batch = SpriteBatch()
    private val viewport = FitViewport(worldWidth, worldHeight)

    // Managers //
    private val cameraManager = ManagersRegistry.get(CameraManager::class)

    private fun updateViewportCamera() {
        val camera = cameraManager.activeCamera.value ?: return
        viewport.camera = camera.camera
    }

    private fun resizeViewport(
        width: Int,
        height: Int,
    ) {
        viewport.update(width, height, true)
        cameraManager.activeCamera.value?.resize(viewport.worldWidth, viewport.worldHeight)
    }

    override fun afterProcess(delta: Float) {
        val camera = cameraManager.activeCamera.value ?: return
        batch.projectionMatrix = camera.camera.combined

        batch.use { b ->
            matchingNodes.sortedByDescending { it.globalPosition.y }.forEach { node ->
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
        cameraManager.activeCamera.connect { updateViewportCamera() }

        // Hook into scene manager
        sceneManager.onResize.connect { w, h -> resizeViewport(w, h) }
        sceneManager.onSceneReplaced.connect {
            cameraManager.activeCamera.value = null
        } // Reset camera whenever a scene is replaced
        viewport.apply()
    }

    override fun onSystemClose() {
        super.onSystemClose()
        batch.dispose()
    }
}
