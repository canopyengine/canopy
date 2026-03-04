package io.canopy.engine.logging.core

import io.canopy.engine.logging.slf4j.Slf4jProvider

/**
 * Default [LogProvider] used by the engine.
 *
 * This object acts as a small indirection layer between the engine core
 * and the concrete logging implementation (currently SLF4J).
 *
 * The goal is to:
 * - Avoid directly coupling the core module to a specific logging backend
 * - Allow the logging provider to be swapped or extended in the future
 * - Keep the engine API stable while delegating implementation details
 *
 * All logging calls from the engine resolve through this provider.
 *
 * Implementation note:
 * This uses Kotlin delegation so all [LogProvider] behavior is delegated
 * to [Slf4jProvider].
 */
internal object DefaultProvider : LogProvider by Slf4jProvider
