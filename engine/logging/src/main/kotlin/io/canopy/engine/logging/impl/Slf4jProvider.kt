package io.canopy.engine.logging.impl

import io.canopy.engine.logging.api.LogProvider
import io.canopy.engine.logging.api.Logger
import org.slf4j.LoggerFactory

internal object Slf4jProvider : LogProvider {
    override fun get(name: String): Logger = Slf4jLogger(LoggerFactory.getLogger(name))
}
