package io.canopy.engine.logging.core

/**
 * Factory interface responsible for creating [Logger] instances.
 *
 * This abstraction allows the engine to remain independent of any
 * specific logging implementation (such as SLF4J or Logback).
 *
 * Concrete providers are responsible for adapting the engine's
 * [Logger] interface to a particular logging backend.
 *
 * Example implementations:
 * - SLF4J provider
 * - Test / in-memory logger provider
 * - Custom logging adapters
 *
 * The [name] typically represents the logger category, usually the
 * fully-qualified class name requesting the logger.
 */
fun interface LogProvider {

    /**
     * Returns a logger associated with the given [name].
     *
     * @param name the logger category, usually a class or component name
     */
    fun get(name: String): Logger
}
