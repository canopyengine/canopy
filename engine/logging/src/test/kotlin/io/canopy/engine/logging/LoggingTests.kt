package io.canopy.engine.logging

fun main() {
    CanopyLogging.init()

    val logger = logger("User")
    val engineLogger = EngineLogs.subsystem("Engine")

    logger.info("a" to 1, "b" to 2, "c" to 3) { "User Log" }
    engineLogger.info("a" to 1, "b" to 2, "c" to 3) { "Engine log" }
}
