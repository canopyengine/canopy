package io.canopy.engine.logging.engine

import io.canopy.engine.logging.api.Logger
import io.canopy.engine.logging.api.Logs

object EngineLogs {
    fun subsystem(name: String): Logger = Logs.get("canopy.engine.$name")

    val lifecycle: Logger = subsystem("lifecycle")
    val node: Logger = subsystem("node")
    val physics: Logger = subsystem("physics")
    val render: Logger = subsystem("render")

    // ✅ new
    val system: Logger = subsystem("system")
    val managers: Logger = subsystem("managers")
    val scene: Logger = subsystem("scene")

    val app: Logger = subsystem("app")
}
