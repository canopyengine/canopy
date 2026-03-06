package io.canopy.engine.logging.util

import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.Logs

/* LOG UTILITY METHODS */

fun <T> Logs.withFrame(frame: Long, block: () -> T): T = LogContext.with("frame" to frame, block = block)

fun <T> Logs.withNode(path: String, block: () -> T): T = LogContext.with("nodePath" to path, block = block)

object LogUtils {
    const val FILE_LOG_PATTERN =
        "%d{yy.MM.dd.HH:mm:ss.SSS} %-5level %logger{36} " +
        "%mdcx{runId,engineVersion,runDir} %msg%ex{short}%n"



    const val CONSOLE_LOG_PATTERN =
        "%d{yy.MM.dd.HH:mm:ss.SSS} " +
            "%highlight(%-5level) " +
            "%boldCyan(%logger{1}) " +
            "%cmdcx{runId,engineVersion,runDir} " +
            "%msg%ex{short}%n"
}
