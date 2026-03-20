package io.canopy.engine.logging.logback

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Custom Logback converter that adds ANSI colors to structured fields in log messages.
 *
 * This converter detects field patterns like [key=value, key2=value2] in log messages
 * and applies colors:
 * - Keys: dimmed (gray)
 * - String values: green
 * - Numeric values: yellow
 * - Null values: red
 *
 * Usage in canopy-logback.xml:
 * ```xml
 * <conversionRule conversionWord="coloredMsg"
 *                 converterClass="io.canopy.engine.logging.logback.ColoredFieldsConverter" />
 * <pattern>%d{HH:mm:ss.SSS} %-5level - %coloredMsg%n</pattern>
 * ```
 */
class ColoredFieldsConverter : ch.qos.logback.classic.pattern.MessageConverter() {

    companion object {
        // ANSI color codes
        private const val ANSI_RESET = "\u001B[0m"
        private const val ANSI_DIM = "\u001B[2m"
        private const val ANSI_GREEN = "\u001B[32m"
        private const val ANSI_YELLOW = "\u001B[33m"
        private const val ANSI_RED = "\u001B[31m"

        // Regex to match [key=value, key2=value2] at the start of the message
        private val FIELDS_REGEX = Regex("""^\[([^]]+)]\s*(.*)$""")
        private val FIELD_PAIR_REGEX = Regex("""(\w+)=([^,\]]+)""")
    }

    override fun convert(event: ILoggingEvent): String {
        val message = event.formattedMessage

        val match = FIELDS_REGEX.matchEntire(message) ?: return message

        val fieldsString = match.groupValues[1]
        val restOfMessage = match.groupValues[2]

        // Colorize each field
        val colorizedFields = FIELD_PAIR_REGEX.replace(fieldsString) { matchResult ->
            val key = matchResult.groupValues[1]
            val value = matchResult.groupValues[2].trim()

            val coloredKey = "$ANSI_DIM$key$ANSI_RESET"
            val coloredValue = colorizeValue(value)

            "$coloredKey=$coloredValue"
        }

        return "[$colorizedFields] $restOfMessage"
    }

    private fun colorizeValue(value: String): String = when {
        value == "null" -> "$ANSI_RED$value$ANSI_RESET"
        value.toIntOrNull() != null || value.toDoubleOrNull() != null -> "$ANSI_YELLOW$value$ANSI_RESET"
        else -> "$ANSI_GREEN$value$ANSI_RESET"
    }
}
