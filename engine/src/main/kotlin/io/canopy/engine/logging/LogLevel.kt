package io.canopy.engine.logging

/**
 * Logging levels used by the Canopy logging API.
 *
 * This enum acts as a framework-agnostic abstraction so the engine
 * does not depend directly on a specific logging backend (e.g. SLF4J,
 * Logback, Log4j).
 *
 * Logger implementations are responsible for mapping these levels
 * to the equivalent levels of the underlying logging system.
 *
 * Typical mapping (SLF4J/Logback):
 * - TRACE → trace
 * - DEBUG → debug
 * - INFO  → info
 * - WARN  → warn
 * - ERROR → error
 */
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}
