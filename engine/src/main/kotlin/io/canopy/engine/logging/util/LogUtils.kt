package io.canopy.engine.logging.util

import io.canopy.engine.logging.CanopyLogs
import io.canopy.engine.logging.LogContext

/* LOG UTILITY METHODS */

fun <T> CanopyLogs.withFrame(frame: Long, block: () -> T): T = LogContext.with("frame" to frame, block = block)

fun <T> CanopyLogs.withNode(path: String, block: () -> T): T = LogContext.with("nodePath" to path, block = block)
