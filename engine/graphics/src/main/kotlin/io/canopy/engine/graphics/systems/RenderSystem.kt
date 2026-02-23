package io.canopy.engine.graphics.systems

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.viewport.FitViewport
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.nodes.core.TreeSystem
import io.canopy.engine.graphics.managers.CameraManager
import io.canopy.engine.graphics.nodes.visual.AnimatedSprite2D
import io.canopy.engine.graphics.nodes.visual.Sprite2D
import ktx.graphics.use
import ktx.log.logger

class RenderSystem(worldWidth: Int, worldHeight: Int) :
    TreeSystem(
        UpdatePhase.FramePost,
        0,
        Sprite2D::class,
        AnimatedSprite2D::class
    ) {
    // Misc
    private val logger = logger<RenderSystem>()
    private val batch = SpriteBatch()
    private val viewport = FitViewport(worldWidth.toFloat(), worldHeight.toFloat())

    // Managers //
    private val cameraManager by lazy { ManagersRegistry.get(CameraManager::class) }

    private fun updateViewportCamera() {
        val camera = cameraManager.activeCamera.value ?: return
        viewport.camera = camera.camera
    }

    private fun resizeViewport(width: Int, height: Int) {
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
                                it.regionHeight * node.scale.y
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onRegister() {
        super.onRegister()
        cameraManager.activeCamera.connect { updateViewportCamera() }

        // Hook into scene manager
        sceneManager.onResize.connect { w, h -> resizeViewport(w, h) }
        sceneManager.onSceneReplaced.connect {
            cameraManager.activeCamera.value = null
        } // Reset camera whenever a scene is replaced
        viewport.apply()
    }

    override fun onUnregister() {
        super.onUnregister()
        batch.dispose()
    }
}
