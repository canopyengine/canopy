package io.canopy.platforms.terminal.app

import kotlin.test.Test
import kotlin.test.assertEquals
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal

class TerminalFrameTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)

    @Test
    fun `shorter frame clears old text and removed rows`() {
        val screen = Screen()
        val surface = surface(screen)
        surface.renderWorld(listOf("long old line", "old second row", "old third row"))
        surface.renderWorld(listOf("new"))
        assertEquals(listOf("new"), screen.lines())
    }

    @Test
    fun `empty frame clears all prior content and resets cursor`() {
        val screen = Screen()
        val surface = surface(screen)
        surface.renderWorld(listOf("old", "other"))
        surface.renderWorld(emptyList())
        assertEquals(emptyList(), screen.lines())
        assertEquals(0 to 0, screen.cursor())
    }

    @Test
    fun `replacement erases wrapped rows and command output`() {
        val screen = Screen(width = 8)
        val surface = surface(screen, width = 8)
        surface.renderWorld(listOf("long line wraps across rows"))
        screen.apply("command output\nother output")
        surface.renderWorld(listOf("next"))
        assertEquals(listOf("next"), screen.lines())
    }

    private fun surface(screen: Screen, width: Int = 40) = TerminalSurface(
        terminal,
        { Size(width, 10) },
        screen::apply,
        { 3 },
        { false }
    )

    /** Small screen model interprets cursor positioning, erase operations, newlines and wrapping. */
    private class Screen(private val width: Int = 40) {
        private val rows = mutableMapOf<Int, CharArray>()
        private var x = 0
        private var y = 0
        fun cursor() = x to y
        fun lines(): List<String> {
            val last = rows.entries.filter { it.value.any { char -> char != ' ' } }.maxOfOrNull { it.key }
                ?: return emptyList()
            return (0..last).map { rows[it]?.concatToString()?.trimEnd() ?: "" }
        }
        fun apply(output: String) {
            var offset = 0
            while (offset < output.length) {
                if (output[offset] == '\u001b') {
                    val end = (offset + 2 until output.length).first { output[it].isLetter() }
                    val args = output.substring(offset + 2, end).split(';').map { it.toIntOrNull() ?: 0 }
                    when (output[end]) {
                        'J' -> if (args.first() == 2) rows.clear()
                        'K' -> rows[y] = CharArray(width) { ' ' }
                        'H', 'f' -> {
                            y = (args.first() - 1).coerceAtLeast(0)
                            x =
                                (args.getOrElse(1) { 1 } - 1).coerceAtLeast(0)
                        }
                    }
                    offset = end + 1
                } else {
                    when (val char = output[offset++]) {
                        '\n' -> {
                            y++
                            x = 0
                        }
                        '\r' -> x = 0
                        else -> {
                            if (x >= width) {
                                x = 0
                                y++
                            }
                            rows.getOrPut(y) { CharArray(width) { ' ' } }[x++] = char
                        }
                    }
                }
            }
        }
    }
}
