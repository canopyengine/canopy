package io.canopy.engine.logging.converters

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import io.canopy.engine.logging.util.LogColorUtils.ANSI_DIM
import io.canopy.engine.logging.util.LogColorUtils.ANSI_RESET
import io.canopy.engine.logging.util.LogColorUtils.colorizeValue

/**
 * Renders the MDC entry "logFields" as a colored [k=v, ...] block for console output.
 *
 * Expected format in MDC:
 *   logFields = "k1=v1, k2=v2, k3=v3"
 */
class ConsoleLogFieldsConverter : ClassicConverter() {

    override fun convert(event: ILoggingEvent): String {
        val raw = event.mdcPropertyMap?.get("logFields") ?: return ""
        if (raw.isBlank()) return ""

        val rendered = raw.split(", ")
            .joinToString(prefix = "[", postfix = "]", separator = ", ") { token ->
                val idx = token.indexOf('=')
                if (idx <= 0) return@joinToString token

                val key = token.substring(0, idx)
                val value = token.substring(idx + 1)

                val renderedKey = "$ANSI_DIM$key$ANSI_RESET"
                val renderedValue = colorizeValue(value)
                "$renderedKey=$renderedValue"
            }

        return rendered
    }
}
