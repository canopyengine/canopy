# Canvas and component layout for 0.1.0

Agent-originated implementation notes prepared by Codex. This change belongs to
the 0.1.0 development line; it does not change the release version, publish artifacts, or
declare release acceptance.

## Current viewport UI placement

UI components can be children of an ordinary scene `Node`; a `UiRoot` or
`Canvas` ancestor is optional. Each topmost UI component without an explicit
root uses the host's viewport as its layout area. Ordinary grouping nodes do
not create a new UI coordinate system, and `Node2D` transforms do not affect
UI bounds or pointer hits. Direct constructor arguments initialize once;
reactive UI expressions belong in a component's retained `ui {}` content.

The painter draws ordinary scene content first, then rootless viewport UI in
current scene-tree order, then top-level explicit presentations sorted by layer
and `zIndex`. A `Canvas` defaults to the overlay layer; an explicit `UiRoot` can
also use that layer. Pointer routing visits this order from front to back. A
nested `Canvas` or a root reparented beneath another UI component is
local to that component, not another global presentation. Buttons can gain
keyboard focus in either an implicit or explicit presentation; hiding,
detaching, or moving a focused control out of its presentation clears focus.
Standalone `UiRoot.render()` remains supported, but a standalone rootless
component must belong to the current scene for automatic painting.

## Runtime responsibilities

- `UiNode` owns component state and lifecycle; `UiScope` retains keyed children
  and reactive bindings without introducing another presentation tree.
- `UiLayoutEngine` measures, lays out, and paints backend-neutral rectangles.
- `UiManager` discovers top-level presenters, schedules retained frames, and
  tracks implicit focus; `UiRoot` adds explicit layer, alignment, and focus.
- `UiTreeInput` traverses hits and clips. `InputFocus` orders routes and handles
  gameplay capture; neither changes the node ownership tree.
- `UiBackend` supplies host metrics, viewport mapping, and draw primitives;
  native resources and event translation remain in platform adapters.

## Presentation and platform boundary

`Canvas` is a retained node subtree. It follows the host viewport, draws above
gameplay, has no background unless styled, and does not capture gameplay input
merely by existing. Configuration runs once before child content and entry.
Existing node visibility, ownership, removal, and destruction rules apply.

Components contain no native terminal, window, or graphics implementation.
`UiBackend` supplies text/control measurements, viewport geometry, drawing, and
layout-bound resolution. Platform adapters translate native input into engine
events. Shared rectangle allocation, lifecycle and routing retain consistent
semantics across backends; these are not duplicated per platform.

Terminal geometry is resolved to cell boundaries by `TerminalUiBackend` before
painting and hit testing. Graphical/custom backends preserve fractional bounds
by default. Units remain terminal cells or backend pixels. There is no implicit
reference-resolution scaling, new rendering dependency, or desktop activation.

```kotlin
Canvas {
    Text("Score: $score", place = Place.At(Anchor.TopRight, inset = Insets(2.0)))
    Column(
        place = Place.At(Anchor.Center),
        size = Size.Content,
        align = CrossAlign.Center,
        gap = 1.0
    ) {
        Text("Paused")
        Button("Resume", onClick = { resume() })
    }
}
```

Imports for these examples are `io.canopy.engine.ui.*`; standalone component
subclasses live in `io.canopy.engine.ui.components`. `score` and `resume()` are
application state and callbacks. The declarative compiler plugin is required.

## Placement is separate from sizing

Every rectangular component exposes mutable `place` and `size` properties.
Changes belong on the lifecycle thread and invalidate layout. The immutable
configuration types prevent shared mutable vector state.

- `Place.Auto` inherits parent placement behavior.
- `Place.At(anchor, inset, offset, origin)` attaches a point on the child to a
  point in the parent's inset content area. Named anchors choose a matching
  child origin. The origin is a layout attachment, not a transform pivot.
- `Place.Stretch(inset)` fills the inset parent area, respecting maximum sizes.
- `Place.InFlow(align)` participates in a flow and optionally overrides its
  cross-axis alignment.

Canvas and Box accept anchors; Row, Column, Grid and FlowRow manage placement.
Flow parents reject anchors and stretching with an actionable error. Free-positioned
parents reject `Place.InFlow`. Put a Box inside a flow when an item needs a badge
or other overlapping child.

`Size` contains `UiLength` policies: Content, Fixed, Fill, Fraction and Weight.
The existing `UiLength` name is retained. Fill and Weight divide remaining space
in Row/Column; Weight selects a positive proportion. Capped allocations give
unused space back to siblings. Content is natural measured size; a content-sized
container has no spare space to distribute. Row/Column retain their old fill-width
default, so use `Size.Content` for a tightly wrapped anchored menu.

```kotlin
Row(size = Size.FillWidth, gap = 2.0) {
    Text("Sidebar", size = Size(width = UiLength.Fixed(24.0)))
    Text("Main", size = Size(width = UiLength.Weight(2.0)))
    Text("Inspector", size = Size(width = UiLength.Weight(1.0)))
}
```

`arrange` distributes unused main-axis space: Start, Center, End, SpaceBetween,
SpaceAround or SpaceEvenly. `align` sets cross-axis Start, Center, End or Stretch.
Both are parent policies, distinct from a child's `Place`. Minimum `gap` is
reserved before arrangement. Oversized content retains its size and is clipped
according to the existing clipping policy; weights never create negative sizes.

## Wrapping, grids, and ordinary drawings

`FlowRow` wraps measured children and provides `lineGap`; `Grid(columns)` assigns
equal-width cells and sizes each row to its tallest child. Weights provide
proportional allocation in Row/Column, not a CSS flex-grow/shrink algorithm.
The subsequent [unified Grid change](unified-grid.md) adds glyph and explicit UI
cell content to Grid. The later [UI cleanup](ui-cleanup.md) removes the transitional
GridNode adapter and redundant layout wrappers.

`Box` gives an ordinary node subtree a declared rectangular drawing area.
Children draw in local logical coordinates through `DrawContext`; it does not
reinterpret Node2D world transforms or invent visual bounds for behavior nodes.

Custom measured drawing components can extend `UiNode`, overriding
`measure(backend, available)` and the normal `onDraw(context)` callback. The base
uses retained UiNode infrastructure but opts into neither focus nor pointer input.
Natural measurements exclude padding and must be deterministic and side-effect free.
Call protected `invalidateMeasurement()` when custom content changes its natural size.

```kotlin
Box(
    size = Size(UiLength.Fixed(20.0), UiLength.Fixed(8.0)),
    place = Place.At(Anchor.Center)
) {
    MyDrawingNode()
}
```

## Canvas boundaries, input, and identity

A top-level `Canvas(size = CanvasSize.Fixed(80.0, 25.0), anchor = Anchor.Center,
order = 10)` creates a fixed drawing area positioned within the viewport. Order
maps to root painter order; ties preserve registration order. Nested canvases
use their enclosing content area and are painted there once, not mounted as
independent global overlays. Descendants cannot escape their enclosing Canvas
through root order settings.

Buttons use the allocated bounds and ancestor clips for input. Focus and existing
input capture continue through InputFocus. Adding a Canvas alone is not a modal
operation. This change adds neither a modal API nor camera projection.

The compiler observes declarative text, sizing, placement and container configuration
expressions. Callbacks remain unobserved. Child content is composed once per
retained identity; conditional/keyed regions retain the existing reconciliation
rules. Direct constructors keep once-only configuration; reactive `place` and
`size` assignments in declarative scopes use targeted binding hooks.

## Migration and companion documentation

`toggleVisibility()` is inherited from `Node`, alongside `show()` and `hide()`.
It flips `isVisible` locally, so it works on Canvas, UI controls, and ordinary
nodes. A hidden ancestor still suppresses rendering when a child toggles back
on. Descendant local flags, processing, layout space, and retained UI state are
preserved. Use it on the lifecycle thread; destroyed nodes reject the call.

```kotlin
Button("Toggle HUD", onClick = {
    val canvas by ancestor<Canvas<*>>()
    canvas.toggleVisibility()
})
```

A button inside a hidden Canvas cannot receive clicks to show it again; put a
reopening control outside that Canvas or use a gameplay input callback.

Local node query delegates can be used inside synchronous node callbacks. In a
button action, the query starts from that button, just as a member delegate starts
from its owning node:

```kotlin
import io.canopy.engine.core.queries.ancestor
import io.canopy.engine.ui.Canvas

Canvas {
    Button("Resume", onClick = {
        val canvas by ancestor<Canvas<*>>()
        canvas.hide()
    })
}
```

Each read resolves again against the active owner; it does not capture a node
when the delegate is declared. Member delegates on nodes and behaviors continue
to use their explicit owners. A local delegate read without an active node
ownership context throws `IllegalStateException`, including optional queries.
Ownership is synchronous and does not carry into launched coroutines or other
threads. Optional queries return null only when an owner exists and the target
is missing. UI nodes follow the same hierarchy and lifetime rules as other nodes.

`UiRoot`, `UiStyle` and keyed composition remain supported. `CrossAlign` is the
single cross-axis alignment type. `UiStyle` still carries the sizing/appearance transport;
new placement/size arguments provide explicit APIs without removing old style code.
Named arguments and trailing content/callback lambdas are preferred; positional
calls that formerly passed callbacks or content in the middle of an argument list
must migrate to named arguments because placement and sizing add parameters.
Compiler/runtime ABI changed, so rebuild engine and compiler together and recompile
consumers. No version promotion is part of this change.

The companion canopy-docs update must extend
`markdown/manuals/concepts/app/declarative-ui.md` and the UI class references with
Canvas, Place, Size, Arrange, CrossAlign, FlowRow, Grid and UiNode measurement hooks,
plus local callback delegation in the node-query and NodeDependency references
and `toggleVisibility()` in the Node reference. That repository
is not present in this workspace; these local notes are not a claim that its
published manuals or examples have been updated.

## Historical Canvas verification

Verified on Windows, 2026-11-13, from the repository root:

```powershell
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle test ktlintCheck build coverageReport --console=plain
git diff --check
```

Both commands passed. Enabled-module test reports contain 675 tests, zero failures
and zero skips. Aggregate line coverage is 8385/9266 lines (90.5%), above the
configured 60% gate. Regressions cover backend-dependent measurements, resizing,
anchors, nested Canvas input/drawing, weighted/capped allocation, arrangement,
wrapping/grid geometry, invalid placements, ordinary drawing areas, custom measured
drawings and invalidation, reactive compiler arguments, and terminal cell bounds.

The desktop module remains disabled in settings, as before this change. These
checks do not constitute graphical desktop or physical terminal acceptance.

## Local delegate verification

Verified on Windows, 2026-11-13:

```powershell
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle :engine:ktlintFormat :engine:test --tests io.canopy.engine.core.queries.DependencyTests --tests io.canopy.engine.ui.integration.UiCompilerIntegrationTests --console=plain
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle test ktlintCheck build coverageReport --console=plain
git diff --check
```

The focused run passed 27 tests. The full checks passed with 694 tests, zero
failures/errors/skips, and 8646/9520 covered lines (90.8%). Four added regressions
cover actual compiled Button callbacks, nested ownership restoration after
exceptions, repeated resolution after reparenting, missing/destroyed owner rules,
and preservation of node/behavior member ownership. Desktop remains disabled.

## Visibility toggle verification

Verified on Windows, 2026-11-13:

```powershell
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle :engine:ktlintFormat :engine:test --tests io.canopy.engine.core.nodes.NodeVisibilityTests --tests io.canopy.engine.ui.CanvasLayoutTests --console=plain
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle :engine:test --tests io.canopy.engine.core.nodes.NodeVisibilityTests --tests io.canopy.engine.ui.CanvasLayoutTests :engine:ktlintCheck --console=plain
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle test ktlintCheck build coverageReport --console=plain
git diff --check
```

The first focused run exposed an incorrectly constructed test Button; after
correcting the fixture to use the declarative helper, all 16 focused tests and
engine lint passed. Full verification passed with 696 tests, zero
failures/errors/skips, and 8646/9522 covered lines (90.8%). Regressions cover
detached and entered toggles, hidden ancestors, unchanged descendant flags and
processing, destroyed-node rejection, Canvas focus clearing, and retained UI
identity/layout after hiding and showing. Desktop remains disabled.
