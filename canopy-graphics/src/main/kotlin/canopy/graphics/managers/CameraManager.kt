package canopy.graphics.managers

import canopy.core.managers.Manager
import canopy.core.signals.asNullableSignalVal
import canopy.graphics.nodes.camera.Camera2D
import ktx.log.logger

class CameraManager : Manager {
    val activeCamera = null.asNullableSignalVal<Camera2D>()

    private val logger = logger<CameraManager>()

    internal fun registerCamera(cam: Camera2D) {
        if (activeCamera.value == cam) return
        val oldCamera = activeCamera.value

        // Deactivate old camera
        if (oldCamera != null) {
            oldCamera.active.value = false
        }

        activeCamera.value = cam
        logger.debug { "Active camera set to ${cam.name}" }
    }

    internal fun unregisterCamera(cam: Camera2D) {
        if (activeCamera.value == cam) {
            logger.debug { "Camera ${cam.name} unregistered as active camera" }
            activeCamera.value = null
        }
    }
}
