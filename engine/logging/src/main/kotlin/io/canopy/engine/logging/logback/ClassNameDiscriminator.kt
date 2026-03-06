package io.canopy.engine.logging.logback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.sift.Discriminator

/**
 * Custom discriminator that extracts the simple class name from the logger name.
 *
 * Examples:
 * - "io.canopy.engine.logging.App" → "App"
 * - "canopy.engine.physics" → "physics"
 * - "LoggingTests" → "LoggingTests"
 */
class ClassNameDiscriminator : Discriminator<ILoggingEvent> {

    private var started = false
    private var key = "className"

    override fun getDiscriminatingValue(event: ILoggingEvent): String {
        val loggerName = event.loggerName ?: return "unknown"

        // Extract the last segment after the last dot
        val lastDotIndex = loggerName.lastIndexOf('.')
        return if (lastDotIndex >= 0 && lastDotIndex < loggerName.length - 1) {
            loggerName.substring(lastDotIndex + 1)
        } else {
            loggerName
        }
    }

    override fun getKey(): String = key

    fun setKey(key: String) {
        this.key = key
    }

    override fun start() {
        started = true
    }

    override fun stop() {
        started = false
    }

    override fun isStarted(): Boolean = started
}

