package io.canopy.platforms.terminal.app

import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text

/** Applies every incremental write, rather than treating the final diff as a complete frame. */
internal fun terminalTestRows(terminal: Terminal, output: List<String>): Map<Int, String> {
    val cells = mutableMapOf<Int, MutableMap<Int, String>>()
    var row = 0
    var column = 0
    val pattern = Regex("\u001b\\[([?0-9;:]*)([A-Za-z])|([^\u001b]+)")
    pattern.findAll(output.joinToString("")).forEach { match ->
        when (match.groupValues[2]) {
            "J" -> cells.clear()
            "H" -> {
                row = (match.groupValues[1].substringBefore(';').toIntOrNull() ?: 1) - 1
                column = (match.groupValues[1].substringAfter(';', "1").toIntOrNull() ?: 1) - 1
            }
            "" -> Regex("\\X").findAll(match.groupValues[3]).forEach { cluster ->
                val width = Text(cluster.value, whitespace = Whitespace.PRE).measure(terminal, 100).max
                val line = cells.getOrPut(row) { mutableMapOf() }
                line[column] = cluster.value
                for (offset in 1 until width) line[column + offset] = ""
                column += width
            }
        }
    }
    return cells.mapNotNull { (index, line) ->
        val text = (0..(line.keys.maxOrNull() ?: -1)).joinToString("") { line[it] ?: " " }.trimEnd()
        if (text.isEmpty()) {
            null
        } else {
            val width = Text(text, whitespace = Whitespace.PRE).measure(terminal, 100).max
            index to if (index == row) text + " ".repeat((column - width).coerceAtLeast(0)) else text
        }
    }.toMap()
}
