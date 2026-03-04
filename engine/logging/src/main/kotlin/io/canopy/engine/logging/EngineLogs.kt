package io.canopy.engine.logging

import io.canopy.engine.logging.core.Logger

/**
 * Centralized access point for engine loggers.
 *
 * All loggers created here live under the `canopy.engine.*` namespace,
 * which allows the logging system to:
 *
 * - Route engine-origin logs to dedicated files (engine.log / engine.jsonl)
 * - Filter them out from user-facing console output
 * - Keep engine logging consistent across subsystems
 *
 * Contributors:
 * - Add new engine subsystems here instead of creating ad-hoc loggers.
 * - Subsystem names become part of the logger category
 *   (e.g. `canopy.engine.physics`, `canopy.engine.render`).
 *
 * Example usage:
 *
 * ```
 * EngineLogs.physics.debug { "Stepping physics simulation" }
 * EngineLogs.render.info { "Renderer initialized" }
 * ```
 */
object EngineLogs {

    /**
     * Creates a logger for a specific engine subsystem.
     *
     * @param name subsystem identifier (e.g. "physics", "render", "scene")
     */
    fun subsystem(name: String): Logger = Logs.get("canopy.engine.$name")

    /* ------------------------------------------------------------
     * Core engine subsystems
     * ------------------------------------------------------------ */

    val lifecycle: Logger = subsystem("lifecycle")
    val node: Logger = subsystem("node")
    val physics: Logger = subsystem("physics")
    val render: Logger = subsystem("render")

    /* ------------------------------------------------------------
     * Engine infrastructure
     * ------------------------------------------------------------ */

    val system: Logger = subsystem("system")
    val managers: Logger = subsystem("managers")
    val scene: Logger = subsystem("scene")

    /* ------------------------------------------------------------
     * Application / runtime
     * ------------------------------------------------------------ */

    val app: Logger = subsystem("app")
}
