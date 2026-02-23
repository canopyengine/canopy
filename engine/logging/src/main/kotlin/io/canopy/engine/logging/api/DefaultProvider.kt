package io.canopy.engine.logging.api

import io.canopy.engine.logging.impl.Slf4jProvider

/**
 * Default backend used by the engine.
 * Kept in api package only as a small indirection.
 */
internal object DefaultProvider : LogProvider by Slf4jProvider
