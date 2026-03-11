package io.canopy.engine.logging.slf4j

import io.canopy.engine.logging.core.LogProvider
import io.canopy.engine.logging.core.Logger
import org.slf4j.LoggerFactory

/**
 * SLF4J-backed implementation of [LogProvider].
 *
 * This provider acts as the bridge between the engine's logging abstraction
 * and the SLF4J logging ecosystem.
 *
 * For each requested logger name, it creates a [Slf4jLogger] that delegates
 * logging operations to an SLF4J logger instance obtained via [LoggerFactory].
 *
 * Note:
 * SLF4J internally caches logger instances, so calling [LoggerFactory.getLogger]
 * repeatedly for the same name is inexpensive.
 */
internal object Slf4jProvider : LogProvider {

    /**
     * Returns a logger associated with the given [name].
     *
     * The name typically represents the logger category, usually the
     * fully-qualified class name requesting the logger.
     */
    override fun get(name: String): Logger = Slf4jLogger(LoggerFactory.getLogger(name))
}
