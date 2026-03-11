package io.canopy.engine.logging

import io.canopy.engine.logging.core.DefaultProvider
import io.canopy.engine.logging.core.LogProvider
import io.canopy.engine.logging.core.Logger

/**
 * Entry point for obtaining [Logger] instances.
 *
 * The engine logging API is intentionally decoupled from any concrete
 * logging backend (e.g. SLF4J, Logback). This object delegates logger
 * creation to a pluggable [LogProvider].
 *
 * By default, the engine uses the SLF4J-based provider, but applications
 * or tests may replace it with a custom implementation.
 *
 * Typical usage:
 *
 * ```
 * private val log = logger<MyClass>()
 *
 * log.info { "Initialization complete" }
 * ```
 */
object Logs {

    /**
     * Active logger provider.
     *
     * Marked as [Volatile] so updates via [setProvider] are immediately
     * visible across threads.
     */
    @Volatile
    private var provider: LogProvider = DefaultProvider

    /**
     * Replaces the current logging provider.
     *
     * This is primarily intended for:
     * - Custom logging integrations
     * - Testing environments
     * - Alternative backends
     *
     * Should typically be called during application bootstrap.
     */
    fun setProvider(provider: LogProvider) {
        this.provider = provider
    }

    /**
     * Returns a logger associated with the given [name].
     *
     * The name typically represents a logging category, often a
     * fully-qualified class name.
     */
    fun get(name: String): Logger = provider.get(name)

    /**
     * Returns a logger using the fully-qualified class name of [T]
     * as the logger category.
     */
    inline fun <reified T : Any> of(): Logger = get(T::class.java.name)
}

/* ------------------------------------------------------------------
 * Convenience helpers
 * ------------------------------------------------------------------
 *
 * These helpers make it easy to obtain loggers without referencing
 * the [Logs] object explicitly.
 */

inline fun <reified T : Any> logger(): Logger = Logs.of<T>()

fun logger(name: String): Logger = Logs.get(name)
