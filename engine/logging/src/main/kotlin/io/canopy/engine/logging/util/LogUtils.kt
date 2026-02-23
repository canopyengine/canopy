package io.canopy.engine.logging.util

import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.api.Logs

fun <T> Logs.withFrame(frame: Long, block: () -> T): T = LogContext.with("frame" to frame, block = block)

fun <T> Logs.withNode(path: String, block: () -> T): T = LogContext.with("nodePath" to path, block = block)
