package io.canopy.engine.logging.converters

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Prints MDC entries except excluded keys.
 * Output is plain text so it is safe for both console and file appenders.
 */
class MdcExcludeConverter : ClassicConverter() {

    private val excludedKeys = mutableSetOf<String>()

    override fun start() {
        optionList?.mapTo(excludedKeys) { it.trim() }
        super.start()
    }

    override fun convert(event: ILoggingEvent): String {
        val mdc = event.mdcPropertyMap ?: return ""
        if (mdc.isEmpty()) return ""

        val filtered = mdc.filterKeys { it !in excludedKeys }
        if (filtered.isEmpty()) return ""

        return filtered.entries.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { (k, v) ->
            "$k=$v"
        }
    }
}
