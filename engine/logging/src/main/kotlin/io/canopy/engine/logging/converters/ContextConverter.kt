package io.canopy.engine.logging.converters

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import io.canopy.engine.logging.util.LogColorUtils.ANSI_DIM
import io.canopy.engine.logging.util.LogColorUtils.ANSI_RESET
import io.canopy.engine.logging.util.LogColorUtils.colorizeValue

class ContextConverter : ClassicConverter() {

    private var color: Boolean = false

    private val reservedKeys = setOf(
        "runId",
        "engineVersion",
        "fields"
    )

    override fun start() {
        color = optionList?.any { it.equals("color=true", ignoreCase = true) } == true
        super.start()
    }

    override fun convert(event: ILoggingEvent): String {
        val mdc = event.mdcPropertyMap ?: return ""

        val filtered = mdc
            .filterKeys { it !in reservedKeys }
            .toSortedMap()

        if (filtered.isEmpty()) return ""

        val rendered = filtered.entries.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { (k, v) ->
            if (!color) {
                "$k=$v"
            } else {
                val renderedKey = "$ANSI_DIM$k$ANSI_RESET"
                val renderedValue = colorizeValue(v)
                "$renderedKey=$renderedValue"
            }
        }

        return rendered
    }
}
