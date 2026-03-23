package io.canopy.engine.logging.util

internal object LogColorUtils {

    const val ANSI_RESET = "\u001B[0m"
    const val ANSI_YELLOW = "\u001B[33m"
    const val ANSI_CYAN = "\u001B[36m"
    const val ANSI_MAGENTA = "\u001B[35m"
    const val ANSI_GREEN = "\u001B[32m"
    const val ANSI_DIM = "\u001B[2m"

    fun colorizeValue(v: String): String = when {
        // Boolean
        v.toBooleanStrictOrNull() != null -> "$ANSI_MAGENTA$v$ANSI_RESET"
        // Long
        v.toLongOrNull() != null || v.toDoubleOrNull() != null -> "$ANSI_YELLOW$v$ANSI_RESET"
        // Null
        v == "null" -> "$ANSI_DIM$v$ANSI_RESET"
        else -> "$ANSI_GREEN$v$ANSI_RESET"
    }
}
