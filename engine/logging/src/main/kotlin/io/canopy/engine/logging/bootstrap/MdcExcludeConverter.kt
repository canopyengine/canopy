package io.canopy.engine.logging.bootstrap

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class MdcExcludeConverter : ClassicConverter() {

    private val cyan = "\u001B[36m"
    private val reset = "\u001B[0m"

    override fun convert(event: ILoggingEvent): String {
        val exclude = (firstOption ?: "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val mdc = event.mdcPropertyMap ?: return ""
        val extras = mdc.entries
            .asSequence()
            .filter { (k, v) -> k !in exclude && !v.isNullOrBlank() }
            .sortedBy { it.key }
            .toList()

        if (extras.isEmpty()) return ""

        return extras.joinToString(" ") { (k, v) ->
            "[$cyan$k$reset=$v]"
        }
    }
}
