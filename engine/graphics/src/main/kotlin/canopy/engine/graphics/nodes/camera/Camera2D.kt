package canopy.engine.graphics.nodes.camera

import canopy.engine.core.managers.ManagersRegistry
import canopy.engine.core.nodes.core.Behavior
import canopy.engine.core.nodes.core.Node
import canopy.engine.core.nodes.core.NodeRef
import canopy.engine.core.signals.asSignalVal
import canopy.engine.graphics.managers.CameraManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

class Camera2D(
    name: String = "Camera2D",
    // Specific props
    active: Boolean = true,
    private var targetRef: NodeRef<*>? = null,
    var zoom: Float = 1f,
    var enableSmoothing: Boolean = true,
    var smoothingSpeed: Float = 8f,
    // Base props
    script: (node: Camera2D) -> Behavior<Camera2D>? = { null },
    position: Vector2 = Vector2.Zero,
    scale: Vector2 = Vector2.Zero,
    rotation: Float = 0f,
    groups: MutableList<String> = mutableListOf(),
    // DSL
    block: Camera2D.() -> Unit = {},
) : Node<Camera2D>(
    name,
    script,
    position,
    scale,
    rotation,
    groups,
    block
) {
    val camera = OrthographicCamera()

    val active = active.asSignalVal()

    private var isResizing = false

    var limitLeft: Float? = null
    var limitRight: Float? = null
    var limitTop: Float? = null
    var limitBottom: Float? = null

    // Managers
    private val cameraManager = ManagersRegistry.get(CameraManager::class)

    override fun nodeEnterTree() {
        super.nodeEnterTree()

        active.connect {
            if (it) {
                cameraManager.registerCamera(this)
            } else {
                cameraManager.unregisterCamera(this)
            }
        }
    }

    override fun nodeExitTree() {
        super.nodeExitTree()
        cameraManager.unregisterCamera(this)
    }

    override fun nodeUpdate(delta: Float) {
        updateCamera(delta)
    }

    private fun updateCamera(delta: Float) {
        if (isResizing) return // skip smoothing during resize

        val targetNode = targetRef?.get(this)

        camera.zoom = zoom
        val targetPos = targetNode?.globalPosition ?: globalPosition
        val pos = camera.position

        if (enableSmoothing) {
            pos.x += (targetPos.x - pos.x) * smoothingSpeed * delta
            pos.y += (targetPos.y - pos.y) * smoothingSpeed * delta
        } else {
            pos.x = targetPos.x
            pos.y = targetPos.y
        }

        applyLimits()
        camera.update()
    }

    private fun applyLimits() {
        limitLeft?.let { camera.position.x = maxOf(camera.position.x, it) }
        limitRight?.let { camera.position.x = minOf(camera.position.x, it) }
        limitBottom?.let { camera.position.y = maxOf(camera.position.y, it) }
        limitTop?.let { camera.position.y = minOf(camera.position.y, it) }
    }

    fun screenToWorld(screen: Vector2): Vector2 {
        camera.unproject(Vector3(screen.x, screen.y, 0f))
        return screen
    }

    fun worldToScreen(world: Vector2): Vector2 {
        camera.project(Vector3(world.x, world.y, 0f))
        return world
    }

    /** Snap camera to target immediately during resize */
    fun resize(worldWidth: Float, worldHeight: Float) {
        isResizing = true
        camera.setToOrtho(false, worldWidth, worldHeight)

        val targetNode = targetRef?.get(this)

        val targetPos = targetNode?.globalPosition ?: globalPosition
        camera.position.set(targetPos.x, targetPos.y, 0f)
        camera.update()
        isResizing = false
    }
}
