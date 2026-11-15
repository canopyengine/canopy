package io.canopy.engine.graphics

/** Sparse glyph storage owned and cleared by its Grid component. */
internal class GridCells {
    private val cells = mutableMapOf<GridKey, GridSymbol?>()
    private data class GridKey(val x: Int, val y: Int)
    var empty: GridSymbol? = null
    private var filled: GridSymbol? = null
    val lastRow: Int get() = cells.keys.maxOfOrNull { it.y } ?: -1
    fun fitsColumns(columns: Int): Boolean = cells.keys.all { it.x < columns }
    operator fun get(x: Int, y: Int): GridSymbol? {
        val key = GridKey(x, y)
        return if (cells.containsKey(key)) cells[key] ?: empty else filled ?: empty
    }
    fun set(x: Int, y: Int, symbol: GridSymbol) {
        cells[GridKey(x, y)] = symbol
    }
    fun clear(x: Int, y: Int) {
        if (filled == null) cells.remove(GridKey(x, y)) else cells[GridKey(x, y)] = null
    }
    fun isUniform(symbol: GridSymbol, default: GridSymbol): Boolean =
        cells.isEmpty() && (filled ?: empty ?: default) == symbol
    fun clear(): Boolean {
        val changed = cells.isNotEmpty() || filled != null
        cells.clear()
        filled = null
        return changed
    }
    fun fill(symbol: GridSymbol) {
        cells.clear()
        filled = symbol
    }
}
