package io.canopy.engine.logging.api

/**
 * Pluggable provider for creating Logger instances.
 * Keeps your API independent of SLF4J/Logback.
 */
fun interface LogProvider {
    fun get(name: String): Logger
}
