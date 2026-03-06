package io.canopy.engine.logging.converters

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import io.canopy.engine.logging.util.LogColorUtils.ANSI_DIM
import io.canopy.engine.logging.util.LogColorUtils.ANSI_RESET
import io.canopy.engine.logging.util.LogColorUtils.colorizeValue

class FieldsConverter : ClassicConverter() {

    private var color: Boolean = false

    override fun start() {
        color = optionList?.any { it.equals("color=true", ignoreCase = true) } == true
        super.start()
    }

    override fun convert(event: ILoggingEvent): String {
        val raw = event.mdcPropertyMap?.get("fields") ?: return ""
        if (raw.isBlank()) return ""

        val rendered = raw.split(", ").joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { token ->
            val idx = token.indexOf('=')
            if (idx <= 0) {
                token
            } else {
                val key = token.substring(0, idx)
                val value = token.substring(idx + 1)

                if (!color) {
                    "$key=$value"
                } else {
                    val renderedKey = "$ANSI_DIM$key$ANSI_RESET"
                    val renderedValue = colorizeValue(value)
                    "$renderedKey=$renderedValue"
                }
            }
        }

        return rendered
    }
}
