# Unified Grid for 0.1.0

Agent-originated implementation notes prepared by Codex. This extends the
[Canvas/component layout work](canvas-layout.md) in the existing 0.1.0 development
line. It does not promote `0.1.0-dev4` or publish artifacts.

## One component, independent content and lines

`Grid` supports sparse printable-ASCII glyphs, retained UI components, or both.
Glyphs and components use the same measured cell geometry. UI content paints over
glyph content. Empty/glyph-only cells do not allocate nodes.

```kotlin
import io.canopy.engine.ui.*

Canvas {
    Grid(
        columns = 8,
        rows = 8,
        place = Place.At(Anchor.Center),
        cellSize = Size(UiLength.Fixed(3.0), UiLength.Fixed(1.0)),
        lines = GridLines(
            outer = LineStyle.Single,
            inner = LineStyle.Dotted
        ),
        block = {
            empty(' ')
            set(3, 2, '@')
        }
    ) {
        cell(0, 0) {
            Text("HUD")
        }
    }
}
```

`block` configures the Grid once before its retained child content and tree entry.
Content is the trailing `UiScope` lambda. Callbacks remain separate and unobserved.
Changes belong on the lifecycle thread. The grid owns presentation, not simulation,
collision, occupancy, or game rules.

## Outer border versus dividers

`GridLines.outer` describes the whole perimeter. `inner` supplies a default for
both divider axes. Nullable `horizontal` and `vertical` override that default;
null means inheritance, including after `copy(inner = ...)`.

```kotlin
GridLines(
    outer = LineStyle.Double,
    horizontal = LineStyle.Dotted,
    vertical = LineStyle.None
)
```

Line presets are None, Single, Double, Rounded and Dotted. Presets can be copied
with a portable color and positive finite width. Lines reserve layout space;
`cellPadding` separates them from glyphs and controls. Gap adds space between cell
tracks in addition to the reserved divider bands. Connected edges are drawn once.
For individual cell highlights, normal UI content can use a Box border; the shared
grid line network is configured at Grid level.

An empty-cell dot is content (`empty('.')`), not a divider. Changing GridLines
does not change any stored symbol or the configured empty symbol.

## Cells, dimensions, and identity

- `columns` is positive and columns have equal width.
- Explicit positive `rows` bounds the board, including empty cells. Omitted rows
  derive the extent from UI positions and sparse glyph writes. An empty automatic
  grid has zero rows and no line extent.
- UI-only grids default to fill width. Bounded boards default to content sizing.
- `cellSize` sizes the content area. Lines and `cellPadding` are additional. Without
  an explicit cell size, content is measured using backend metrics; natural row
  heights accommodate their UI contents.
- Sequential children occupy row-major cells. Alternatively use `cell(x, y)`,
  which groups ordinary UI children inside an explicit coordinate slot. These two
  addressing modes cannot be mixed within one grid.
- Duplicate slots and out-of-range coordinates fail clearly. Stable item keys
  preserve UI identity through reorder; coordinate slots retain their own children.
- `set`, indexed access, `empty`, `fill`, `clear(x, y)`, and `clear()` operate on
  glyph state only. They do not destroy UI children or reset control state.
- Glyph fill uses constant storage. Natural geometry uses sparse row-height
  overrides; painting visits visible glyph candidates rather than the whole board.

Mutable `rowLimit` changes a board's bound. Glyphs that would be excluded are
rejected before mutation. UI bounds are checked at layout after reactive child
removals finish. Cells are clipped to their reserved content area for painting
and pointer input, even if a contained component disables its own clipping.

## Platform responsibilities

Engine components carry portable geometry, glyph content, line intent, lifecycle
and input semantics. There are no native terminal/graphics types in the component API.

Backends resolve line widths through `measureGridLines`, draw the network through
`drawGridLines`, and may override `drawGridCells` for native batching. The default
glyph path uses backend text primitives and visits only visible candidates.
Backends without grid-line support reject nonempty lines explicitly.

The terminal backend resolves each enabled stroke to one cell, including wider
requested strokes. It handles single/double junctions, rounded perimeter corners,
dotted strokes, clipping and portable colors. At intersections the perimeter
style wins, followed by the vertical divider style. Mixed line weights therefore
have a deterministic terminal approximation rather than guaranteed typographic
equivalence to graphical strokes. Graphical adapters retain their own metrics and
rendering choices; the desktop module remains disabled.

## Migration and documentation follow-up

The subsequent [UI cleanup](ui-cleanup.md) removes the transitional GridNode
adapter, GridStyle, GridContent and old backend grid hooks. Use Grid with explicit
empty content, spacing and GridLines. Grid never selects its empty symbol
implicitly from line styling. The cleanup guide records source-breaking migration steps.

Rebuild engine and compiler together and recompile consumers after the compiler
binding ABI change. No new dependency or module is required.

Companion canopy-docs work must update the declarative UI manual, Grid
references, and backend extension documentation. That repository is not present
in this workspace; this local guide does not claim its published documentation
was updated.

## Pre-cleanup verification

Verified on Windows, 2026-11-13, from the repository root:

```powershell
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle test ktlintCheck build coverageReport --console=plain
git diff --check
```

All gates passed. Enabled-module reports contain 689 tests, zero failures and
zero skips. Aggregate line coverage is 8764/9651 lines (90.8%), exceeding the
configured 60% gate. Tests cover mixed glyph/UI cells, sparse huge boards,
coordinate validation, retained identity, once-only configuration, reactive line
changes, full-cell anchoring, exact terminal junctions/dotted strokes, independent
divider axes, rounded corners, fractional cell rounding and legacy GridNode behavior.

The already-disabled desktop module remains disabled. No physical terminal or
graphical desktop acceptance is claimed by these automated checks.
