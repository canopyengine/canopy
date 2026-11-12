package io.canopy.tooling.diagnostics

/** Host-independent presentation data; contains no compiler or Gradle classes. */
internal data class Diagnostic(
    val code: String,
    val title: String,
    val source: SourceLine? = null,
    val label: String? = null,
    val help: String? = null,
    val replacement: String? = null,
    val note: String? = null,
    val warning: Boolean = false,
)

internal data class SourceLine(
    val path: String,
    val number: Int,
    val column: Int,
    val text: String,
    val length: Int = 1,
)

/** One compact layout for terminals, IDE build messages and plain logs. */
internal object DiagnosticFormat {
    fun render(diagnostic: Diagnostic, color: Boolean = false, includeSeverity: Boolean = true): String = buildString {
        fun styled(text: String, style: String) = if (color) "\u001B[${style}m$text\u001B[0m" else text
        val severity = if (diagnostic.warning) "warning" else "error"
        val severityColor = if (diagnostic.warning) "1;33" else "1;31"
        if (includeSeverity) append(styled(severity, severityColor))
        append(styled("[${clean(diagnostic.code)}]", if (includeSeverity) "2" else severityColor))
        append(": ${clean(diagnostic.title)}")
        val source = diagnostic.source
        if (source != null) {
            val rawStart = (source.column - 1).coerceIn(0, source.text.length)
            val rawEnd = rawStart + source.length.coerceAtLeast(1).coerceAtMost(source.text.length - rawStart)
            val left = if (source.text.length <= 110) 0 else (rawStart - 35).coerceAtLeast(0)
            val right = (left + 110).coerceAtMost(source.text.length)
            val prefix = if (left > 0) "..." else ""
            val suffix = if (right < source.text.length) "..." else ""
            val leftWidth = width(expand(source.text.substring(0, left)))
            val before = prefix + expand(source.text.substring(left, rawStart), leftWidth)
            val startWidth = leftWidth + width(before.removePrefix(prefix))
            val underline = expand(source.text.substring(rawStart, rawEnd.coerceAtMost(right)), startWidth)
            val gutter = source.number.toString().length
            appendLine()
            append(styled("  --> ${clean(source.path)}:${source.number}:${source.column}", "34"))
            appendLine()
            append(" ".repeat(gutter + 1)).append(styled("|", "34"))
            appendLine()
            append(source.number).append(styled(" | ", "34"))
            append(before).append(styled(underline, severityColor))
            append(expand(source.text.substring(rawEnd.coerceAtMost(right), right), startWidth + width(underline)))
                .append(suffix)
            appendLine()
            append(" ".repeat(gutter + 1)).append(styled("| ", "34"))
            append(" ".repeat(width(before)))
            append(styled("^".repeat(width(underline).coerceIn(1, 75)), severityColor))
            diagnostic.label?.let { append(' ').append(clean(it)) }
        } else {
            diagnostic.label?.let {
                appendLine()
                appendLine()
                append(clean(it))
            }
        }
        diagnostic.help?.let {
            appendLine()
            appendLine()
            append(styled("help", "36")).append(": ").append(clean(it))
        }
        // A one-line preview must represent the entire edit. Keep help without inventing a partial correction.
        if (source != null &&
            diagnostic.replacement != null &&
            source.column >= 1 &&
            source.length >= 0 &&
            source.column - 1 <= source.text.length &&
            source.length <= source.text.length - (source.column - 1) &&
            diagnostic.replacement.none { it == '\n' || it == '\r' }
        ) {
            val start = (source.column - 1).coerceIn(0, source.text.length)
            val end = (start + source.length).coerceIn(start, source.text.length)
            val updated = source.text.replaceRange(start, end, diagnostic.replacement)
            appendLine()
            val left = if (source.text.length <= 110) 0 else (start - 35).coerceAtLeast(0)
            val right = (left + 110).coerceAtMost(source.text.length)
            val prefix = if (left > 0) "..." else ""
            val suffix = if (right < source.text.length) "..." else ""
            val leftWidth = width(expand(source.text.substring(0, left)))
            val oldLine = prefix + expand(source.text.substring(left, right), leftWidth) + suffix
            val newRight = (right + diagnostic.replacement.length - (end - start)).coerceIn(left, updated.length)
            val newLine = prefix + expand(updated.substring(left, newRight), leftWidth) + suffix
            append(styled("${source.number} - $oldLine", "31"))
            appendLine()
            append(styled("${source.number} + $newLine", "32"))
        }
        diagnostic.note?.let {
            appendLine()
            append(styled("note", "2")).append(": ").append(clean(it))
        }
    }

    /** Keep terminal control characters out of source excerpts and provider-supplied messages. */
    private fun clean(text: String): String = buildString {
        text.forEach { char ->
            when {
                char == '\t' || char == '\n' || char == '\r' -> append(' ')
                char.isISOControl() || char in '‪'..'‮' || char in '⁦'..'⁩' -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
    }

    private fun expand(text: String, initialWidth: Int = 0): String = buildString {
        var column = initialWidth
        text.forEach { char ->
            val expanded = if (char == '\t') " ".repeat(4 - column % 4) else clean(char.toString())
            append(expanded)
            column += width(expanded)
        }
    }

    private fun width(text: String): Int = text.codePoints().map { code ->
        when {
            Character.getType(code) in setOf(
                Character.NON_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
                Character.FORMAT.toInt()
            ) -> 0
            code in 0x1100..0x115F ||
                code in 0x2E80..0xA4CF ||
                code in 0xAC00..0xD7A3 ||
                code in 0xF900..0xFAFF ||
                code in 0xFE10..0xFE6F ||
                code in 0xFF01..0xFF60 ||
                code in 0x1F300..0x1FAFF -> 2
            else -> 1
        }
    }.sum()
}

/** Conservative automatic color selection: a rich terminal must be confirmed by the execution host. */
internal object DiagnosticColor {
    fun enabled(mode: String, rich: Boolean, plain: Boolean, noColor: Boolean, ci: Boolean, ide: Boolean): Boolean {
        require(mode in setOf("auto", "always", "never")) { "Choose auto, always or never" }
        if (plain || noColor || ide) return false
        return when (mode) {
            "always" -> true
            "never" -> false
            else -> rich && !ci
        }
    }
}
