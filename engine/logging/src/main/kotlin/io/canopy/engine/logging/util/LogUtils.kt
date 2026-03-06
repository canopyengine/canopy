package io.canopy.engine.logging.util

import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.Logs

/* LOG UTILITY METHODS */

fun <T> Logs.withFrame(frame: Long, block: () -> T): T = LogContext.with("frame" to frame, block = block)

fun <T> Logs.withNode(path: String, block: () -> T): T = LogContext.with("nodePath" to path, block = block)
