package io.canopy.platforms.terminal.app

import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text

/** Composes clipped styled graphemes, then commits only changed physical cells after a successful write. */
internal class TerminalCellScreen(private val terminal: Terminal) {
    private data class Cell(val text: String = " ", val style: String = "", val width: Int = 1, val offset: Int = 0)
    private var front: Array<Array<Cell>>? = null
    private var back = emptyArray<Array<Cell>>()
    private var geometry: Pair<Int, Int>? = null
    private var nextGeometry = 0 to 0
    private var cursor: Pair<Int, Int>? = null

    fun begin(width: Int, height: Int) {
        nextGeometry = width to height
        back = Array(height) { Array(width) { Cell() } }
    }

    fun paint(text: String, x: Int, y: Int) {
        if (y !in back.indices || x !in back[y].indices) return
        var column = x
        val spans = Text(safeTerminalText(text), whitespace = Whitespace.PRE)
            .render(terminal, Int.MAX_VALUE).lines.firstOrNull().orEmpty()
        for (span in spans) {
            val style = span.style("x").substringBefore('x')
            for (match in GRAPHEMES.findAll(span.text)) {
                val glyph = match.value
                val width = Text(glyph, whitespace = Whitespace.PRE).measure(terminal, Int.MAX_VALUE).max
                if (width == 0) continue
                if (column + width > back[y].size) return
                for (index in column until column + width) {
                    val old = back[y][index]
                    val start = index - old.offset
                    for (cell in start until start + old.width) back[y][cell] = Cell()
                }
                for (offset in 0 until width) back[y][column + offset] = Cell(glyph, style, width, offset)
                column += width
            }
        }
    }

    fun flush(editorCursor: Pair<Int, Int>, output: (String) -> Unit) {
        val reset = geometry != nextGeometry || front == null
        val changed = Array(back.size) { y ->
            BooleanArray(back[y].size) { x -> reset || back[y][x] != front!![y][x] }
        }
        // If either half changes, rewrite the whole old/new wide glyph, including uncovered spaces.
        if (!reset) {
            back.forEachIndexed { y, row ->
                row.indices.filter { changed[y][it] }.forEach { x ->
                    for (cell in listOf(row[x], front!![y][x])) {
                        val start = x - cell.offset
                        for (index in start until start + cell.width) changed[y][index] = true
                    }
                }
            }
        }
        val frame = buildString {
            if (reset) append("\u001b[0m\u001b[2J\u001b[H")
            var activeStyle = ""
            var nextX = -1
            var nextY = -1
            back.forEachIndexed { y, row ->
                var x = 0
                while (x < row.size) {
                    val cell = row[x]
                    // A cleared screen already contains unstyled spaces.
                    if (cell.offset > 0 || !changed[y][x] || reset && cell == Cell()) {
                        x++
                        continue
                    }
                    if (nextX != x || nextY != y) {
                        append("\u001b[").append(y + 1).append(';').append(x + 1).append('H')
                    }
                    if (activeStyle != cell.style) {
                        append("\u001b[0m").append(cell.style)
                        activeStyle = cell.style
                    }
                    append(cell.text)
                    x += cell.width
                    nextX = x
                    nextY = y
                }
            }
            if (activeStyle.isNotEmpty()) append("\u001b[0m")
            if (isNotEmpty() || cursor != editorCursor) {
                append("\u001b[").append(editorCursor.second + 1).append(';')
                    .append(editorCursor.first + 1).append('H')
            }
        }
        if (frame.isEmpty()) return
        // One write is the commit boundary. Failed output invalidates the screen for a complete retry.
        try {
            output(frame)
        } catch (failure: Throwable) {
            front = null
            throw failure
        }
        front = back
        geometry = nextGeometry
        cursor = editorCursor
    }

    fun clear() {
        front = null
        geometry = null
        cursor = null
    }
}

private val GRAPHEMES = Regex("\\X")
