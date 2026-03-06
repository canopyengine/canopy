package io.canopy.engine.logging.converters

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Renders the MDC entry "logFields" as a plain [k=v, ...] block for file logs.
 */
class LogFieldsConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String {
        val raw = event.mdcPropertyMap?.get("logFields") ?: return ""
        if (raw.isBlank()) return ""
        return "[$raw]"
    }
}
