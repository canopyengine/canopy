package io.canopy.engine.ui.components

import io.canopy.engine.graphics.GridContent
import io.canopy.engine.graphics.GridStyle
import io.canopy.engine.graphics.GridSymbol
import io.canopy.engine.ui.*

/**
 * Extensible bounded character grid with automatic UI sizing and platform-owned drawing. Operations change retained
 * display content on the lifecycle thread; the next frame presents it. Simulation and occupancy rules
 * belong to the game. Dimensions initialize once; cells use sparse storage rather than one Node per cell.
 */
open class GridNode(columns: Int, rows: Int, name: String = "Grid", block: GridNode.() -> Unit = {}) :
    UiNode<GridNode>(name, block = block),
    GridContent {
    override val columns by nodeProperty(columns)
    override val rows by nodeProperty(rows)
    override val role: String get() = "grid"
    private val cells by nodeProperty(mutableMapOf<Long, GridSymbol?>())
    private var customEmptySymbol by nodeProperty<GridSymbol?>(null)
    private val emptySymbol: GridSymbol
        get() = customEmptySymbol ?: GridSymbol(if (gridStyle == GridStyle.Dotted) '.' else ' ', gridColor)
    private var filledSymbol by nodeProperty<GridSymbol?>(null)
    private var revision by nodeProperty(0L)
    private var storedGap by nodeProperty(1)
    private var storedGridStyle by nodeProperty(GridStyle.Dotted)
    private var storedGridColor by nodeProperty<UiColor?>(UiColor.Rgb(120, 120, 120))

    /** Selects dots or connected cell outlines. Changing this remeasures the grid without changing cell contents. */
    override var gridStyle: GridStyle
        get() = storedGridStyle
        set(value) {
            if (value != storedGridStyle) {
                storedGridStyle = value
                changed()
            }
        }

    /** Neutral decoration color by default; null inherits the component foreground. Does not recolor explicit symbols. */
    override var gridColor: UiColor?
        get() = storedGridColor
        set(value) {
            if (value != storedGridColor) {
                storedGridColor = value
                changed()
            }
        }

    init {
        require(columns > 0 && rows > 0) { "Grid dimensions must be positive" }
        val owned = cells
        onDestroy { owned.clear() }
    }

    override var columnGap: Int
        get() = storedGap
        set(value) {
            require(value >= 0) { "Column gap must be nonnegative" }
            if (value != storedGap) {
                storedGap = value
                changed()
            }
        }

    /** Configures the symbol used for empty cells and subsequent clear operations. */
    fun empty(glyph: Char, color: UiColor? = null) = empty(GridSymbol(glyph, color))

    /** Configures empty cells without replacing explicitly set symbols. */
    fun empty(symbol: GridSymbol) {
        if (customEmptySymbol != symbol) {
            customEmptySymbol = symbol
            changed()
        }
    }

    override operator fun get(x: Int, y: Int): GridSymbol {
        val key = index(x, y)
        return if (cells.containsKey(key)) cells[key] ?: emptySymbol else filledSymbol ?: emptySymbol
    }

    /** Replaces one cell. Invalid coordinates fail before mutation; equal symbols do not request another frame. */
    operator fun set(x: Int, y: Int, symbol: GridSymbol) {
        val index = index(x, y)
        val previous = get(x, y)
        cells[index] = symbol
        if (previous != symbol) changed()
    }

    /** Replaces one cell without constructing a reusable symbol explicitly. */
    fun set(x: Int, y: Int, glyph: Char, color: UiColor? = null) = set(x, y, GridSymbol(glyph, color))

    /** Restores one cell to the configured empty symbol, including after fill. */
    fun clear(x: Int, y: Int) {
        val key = index(x, y)
        val previous = get(x, y)
        if (filledSymbol == null) cells.remove(key) else cells[key] = null
        if (previous != emptySymbol) changed()
    }

    /** Restores the entire grid to its configured empty symbol. */
    fun clear() {
        if (cells.isEmpty() && filledSymbol == null) return
        cells.clear()
        filledSymbol = null
        changed()
    }

    /** Replaces every cell without changing the configured empty symbol. */
    fun fill(glyph: Char, color: UiColor? = null) = fill(GridSymbol(glyph, color))

    /** Replaces every cell in constant space, discarding individual cell overrides. */
    fun fill(symbol: GridSymbol) {
        val equal = cells.isEmpty() && (filledSymbol ?: emptySymbol) == symbol
        cells.clear()
        filledSymbol = symbol
        if (!equal) changed()
    }

    private fun index(x: Int, y: Int): Long {
        require(x in 0 until columns && y in 0 until rows) { "Cell ($x, $y) is outside ${columns}x$rows grid" }
        return y.toLong() * columns + x
    }

    private fun changed() {
        revision++
        invalidate()
    }

    internal override val contentMeasureKey: Any get() = columnGap to gridStyle
    internal override val presentationKey: Any get() = revision
    internal override fun measureContent(backend: UiBackend, available: UiSize) =
        backend.measureGrid(columns, rows, columnGap, gridStyle)
    internal override fun paintContent(backend: UiBackend, clip: UiRect, appearance: UiAppearance) =
        backend.drawGrid(this, contentBounds, clip.intersect(contentBounds), appearance)
}
