package io.canopy.engine.ui.components

import io.canopy.engine.graphics.GridCells
import io.canopy.engine.graphics.GridSymbol
import io.canopy.engine.ui.*

/**
 * Unified sparse glyph board and retained UI cell layout. Coordinates start at the top-left.
 * Explicit rows bound a board; null rows derive its extent from glyphs and UI cells. No node is allocated
 * for an empty/glyph-only cell. Configuration runs once before [content] and entry. All mutations belong
 * on the lifecycle thread; simulation, occupancy and collision rules remain application responsibilities.
 */
open class Grid<N : Grid<N>>(
    columns: Int = 1,
    style: UiStyle? = null,
    name: String = "Grid",
    rows: Int? = null,
    place: Place = style?.place ?: Place.Auto,
    size: Size? = null,
    cellSize: Size? = null,
    lines: GridLines = GridLines(),
    cellPadding: Double = 0.0,
    block: N.() -> Unit = {},
    content: UiScope.() -> Unit = {},
) : UiNode<N>(
    name,
    (style ?: UiStyle(width = if (rows == null) UiLength.Fill else UiLength.Content))
        .withLayout(place, size),
    block = {
        block()
        if (this.content == null) ui(content)
    }
),
    GridSurface {
    private val cells by nodeProperty(GridCells())
    private var revision by nodeProperty(0L)
    private var storedRows by nodeProperty(rows)
    private var storedColumns by nodeProperty(columns)
    private var storedPadding by nodeProperty(cellPadding)
    private var glyphs by nodeProperty(false)
    internal var geometry by nodeProperty<GridGeometry?>(null)
    internal val hasGlyphMeasurements: Boolean get() = glyphs || rowLimit != null

    /**
     * Fixed row limit, or null for automatic extent. Glyph bounds validate before mutation; UI bounds validate
     * at the layout boundary so reactive child removals can finish in the same update batch.
     */
    var rowLimit: Int?
        get() = storedRows
        set(value) {
            require(value == null || value > 0) { "Grid rows must be positive" }
            require(value == null || cells.lastRow < value) { "Rows would exclude retained glyphs" }
            storedRows = value
            invalidate()
        }

    /** Natural dimensions for each cell's content; null measures content. Lines and padding are additional. */
    var cellSize by nodeProperty(cellSize)

    /** Independent outer, horizontal and vertical strokes rendered by the active platform backend. */
    override var lines by nodeProperty(lines)

    /** Nonnegative padding between cell lines and content, in host logical units. */
    var cellPadding: Double
        get() = storedPadding
        set(value) {
            require(value.isFinite() && value >= 0.0)
            storedPadding = value
            invalidate()
        }

    override val columns: Int get() = storedColumns
    override val rows: Int get() = rowLimit ?: maxOf(
        cells.lastRow + 1,
        positions(this.content?.elements().orEmpty()).maxOfOrNull { it.row + 1 } ?: 0
    )
    override val role: String get() = "grid"

    /** Fallback for empty cells when no custom empty symbol was supplied. */
    protected open val defaultSymbol: GridSymbol get() = GridSymbol(' ')
    internal override val kind: UiKind get() = UiKind.Grid

    init {
        require(columns > 0) { "Grid columns must be positive" }
        require(rows == null || rows > 0) { "Grid rows must be positive" }
        require(cellPadding.isFinite() && cellPadding >= 0.0)
        val owned = cells
        onDestroy { owned.clear() }
    }

    /** Sets empty-cell content independently of divider style, preserving explicit symbols. */
    fun empty(glyph: Char, color: UiColor? = null) = empty(GridSymbol(glyph, color))

    /** Sets the fallback symbol for empty and cleared cells. */
    fun empty(symbol: GridSymbol) {
        glyphs = true
        if (cells.empty != symbol) {
            cells.empty = symbol
            changed()
        }
    }

    override operator fun get(x: Int, y: Int): GridSymbol {
        validate(x, y, writing = false)
        return cells[x, y] ?: defaultSymbol
    }

    /** Updates display content only. Equal assignments do not request another presentation. */
    operator fun set(x: Int, y: Int, symbol: GridSymbol) {
        validate(x, y, writing = true)
        val previous = cells[x, y] ?: defaultSymbol
        glyphs = true
        cells.set(x, y, symbol)
        if (previous != symbol) changed()
    }

    /** Writes one printable ASCII glyph and optional portable color. */
    fun set(x: Int, y: Int, glyph: Char, color: UiColor? = null) = set(x, y, GridSymbol(glyph, color))

    /** Clears only glyph content at this coordinate; UI children and their state are unchanged. */
    fun clear(x: Int, y: Int) {
        validate(x, y, writing = false)
        val previous = get(x, y)
        cells.clear(x, y)
        if (previous != (cells.empty ?: defaultSymbol)) changed()
    }

    /** Clears sparse glyph overrides and fill without removing declarative UI content. */
    fun clear() {
        if (cells.clear()) changed()
    }

    /** Applies a glyph to the whole logical extent using constant storage. */
    fun fill(glyph: Char, color: UiColor? = null) = fill(GridSymbol(glyph, color))

    /** Applies a reusable symbol without constructing per-cell nodes or an entire cell array. */
    fun fill(symbol: GridSymbol) {
        glyphs = true
        val equal = cells.isUniform(symbol, defaultSymbol)
        cells.fill(symbol)
        if (!equal) changed()
    }

    private fun validate(x: Int, y: Int, writing: Boolean) {
        val limit = rowLimit ?: if (writing) Int.MAX_VALUE else rows
        require(x in 0 until columns && y in 0 until limit) { "Cell ($x, $y) is outside ${columns}x$limit grid" }
    }

    /** Marks custom/legacy presentation changed without invalidating natural glyph measurements. */
    protected fun changed() {
        revision++
        invalidate()
    }

    internal fun positions(children: List<UiNode<*>>): List<GridPosition> {
        validateColumns(columns)
        val explicit = children.any { it is GridCell }
        require(!explicit || children.all { it is GridCell }) { "Use either cell(x, y) or sequential Grid children" }
        val result = children.mapIndexed { index, child ->
            (child as? GridCell)?.position ?: GridPosition(index % columns, index / columns)
        }
        require(result.distinct().size == result.size) { "Duplicate Grid cell coordinates" }
        require(result.all { it.column < columns && (storedRows == null || it.row < storedRows!!) }) {
            "UI cell is outside the configured Grid dimensions"
        }
        return result
    }

    internal fun validateColumns(value: Int) {
        require(value > 0 && cells.fitsColumns(value)) { "Grid columns must be positive and include retained glyphs" }
    }

    internal fun configureColumns(value: Int) {
        validateColumns(value)
        storedColumns = value
    }

    internal override val contentMeasureKey: Any get() = listOf(
        columns,
        rowLimit,
        rows,
        cellSize,
        listOf(lines.outer, lines.horizontalStyle, lines.verticalStyle).map { it.pattern to it.width },
        cellPadding,
        hasGlyphMeasurements
    )
    internal override val presentationKey: Any get() = listOf(revision, contentMeasureKey, lines)
    internal override fun paintContent(backend: UiBackend, clip: UiRect, appearance: UiAppearance) {
        geometry?.let { geometry ->
            // The synchronous backend view already has validated geometry; avoid rewalking UI cells per glyph.
            val source = this
            val view = object : GridSurface {
                override val columns = geometry.columns
                override val rows = geometry.rows
                override val lines = source.lines
                override fun get(x: Int, y: Int) = cells[x, y] ?: defaultSymbol
            }
            backend.drawGridCells(view, geometry, contentBounds, clip.intersect(contentBounds), appearance)
        }
    }
}

/** Retained coordinate slot. Its children are ordinary composable UI nodes. */
internal class GridCell(val position: GridPosition, content: UiScope.() -> Unit) :
    UiNode<GridCell>("Cell(${position.column},${position.row})", block = {
        if (this.content == null) ui(content)
    })
