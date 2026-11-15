# UI cleanup and migration for 0.1.0

Agent-originated implementation notes prepared by Codex. This cleanup is scoped
to the existing 0.1.0 development line and leaves `0.1.0-dev4` unchanged. It removes
transitional APIs introduced or retained during the Canvas/Grid redesign; it is
a source-breaking cleanup, not a compatibility release or publication.

## Removed duplication

| Removed API/file | Replacement |
| --- | --- |
| LayoutNode | Extend UiNode; override protected measure and call invalidateMeasurement after custom content changes |
| LayoutItem and its UiScope helper | Box with explicit size and placement; ordinary node children retain their drawing area |
| UiAlignment | CrossAlign, with identical Start, Center, End and Stretch values |
| UiStyle.columns | Grid(columns = ...); column state belongs only to Grid |
| GridNode | Grid for both sparse glyphs and retained UI cells |
| GridStyle | Independent GridLines and explicit empty-cell glyph configuration |
| GridContent | GridSurface for synchronous backend glyph reads |
| Legacy measureGrid/drawGrid backend methods | measureGridLines, drawGridLines and drawGridCells |

GridSymbol now lives in `graphics/GridSymbol.kt` with its existing package and
printable-ASCII validation. Sparse storage, fill/clear semantics, clipping, lifecycle
guards and keyed UI identity remain covered by migrated tests.

The obsolete Grid branches in generic measurement and FlowRow packing are removed.
All Grid layout uses shared cell geometry. Alignment no longer converts between
duplicate enum types by matching their names. Grid no longer mirrors its column
count into UiStyle.

Canvas and UiRoot retain their already-shared runtime implementation. They were
not duplicated layout engines and are not removed by this cleanup.

## Ordinary drawing and custom measurement

```kotlin
Canvas {
    Box(
        size = Size(UiLength.Fixed(20.0), UiLength.Fixed(8.0)),
        place = Place.At(Anchor.Center)
    ) {
        MyDrawingNode()
    }
}
```

For a custom measured component, extend `UiNode<MyComponent>` and override
`measure(backend, available): UiSize?`. Natural dimensions exclude padding and use
backend logical units. Returning null retains standard container measurement.
Measurement must be deterministic and side-effect free. Drawing remains in the
normal `onDraw` callback; alternatively the component may contain ordinary UI
children without overriding drawing.

Call protected `invalidateMeasurement()` after content changes that affect natural
dimensions. The revision participates in measurement and presentation keys, so
invalidation repaints even when the component only renders retained children.
All updates belong on the lifecycle thread.

## Grid migration

```kotlin
Canvas {
    Grid(
        columns = 20,
        rows = 10,
        cellSize = Size(UiLength.Fixed(2.0), UiLength.Fixed(1.0)),
        lines = GridLines(outer = LineStyle.Single, inner = LineStyle.Dotted),
        block = {
            empty('.')
            set(3, 2, '@')
        }
    )
}
```

Choose empty glyphs explicitly; removing or changing dividers never changes cell
content. For terminal layouts that previously used dotted cells with columnGap,
use one-unit cell content width and `UiStyle(gap = oldGap, lineGap = 0.0)`.
For the former solid terminal layout, use content width `oldGap + 1`, zero gap,
and single outer/internal lines. These dimensions are terminal logical units;
graphical backends use their own metrics and pixel sizes.

UI component initialization requires a Canvas/UiRoot ancestor. A Grid prefab
can be constructed first, then attached before building its UI subtree. It is
not an unhosted world-space grid. Configuration, retained child content and action
callbacks remain separate.

The native terminal smoke source and external-launcher fixture in this repository
are migrated to Grid. Other repositories and downstream consumers must migrate
their removed type/method references and rebuild matching engine/compiler artifacts.
They have not been edited from this workspace.

## Platform and documentation boundary

Components stay platform agnostic. Platforms still own viewport resolution,
stroke rendering, glyph metrics and native input translation. There is one grid
rendering contract rather than legacy/new backend pipelines. Desktop remains
disabled, as before this work; no dependency or module is added.

The companion canopy-docs update must remove obsolete class references and update
the UI manual, backend extension guide and migration examples. That repository
is not present in this workspace; these notes do not claim published manuals
were updated.

## Verification

Verified on Windows, 2026-11-13, from the repository root:

```powershell
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle test ktlintCheck build coverageReport --console=plain
.\gradlew.bat --gradle-user-home C:\Users\M6500\.gradle --init-script build/verify-ui-cleanup.init.gradle.kts :platforms:terminal:verifyCleanupAcceptanceFixture --console=plain
git diff --check
```

All commands passed. Enabled-module reports contain 690 tests, zero failures and
zero skips. Aggregate line coverage is 8638/9515 lines (90.8%), above the configured
60% gate. The former grid tests were migrated to GridStorageTests and
TerminalGridRenderingTests, preserving storage, bounds, batching, color, clipping,
large-board and lifecycle coverage. A new UiNode-only regression covers measurement
invalidation and frame caching for retained children without a drawing callback.

The second command used a generated local init script to run the Kotlin compiler
with the current terminal runtime classpath and matching compiler plugin. It
compiled TerminalAcceptance.kt independently of the normal Gradle test sources;
this is not a claim of packaged-consumer or physical terminal acceptance.

Source scans found zero references to the removed types, legacy grid methods,
duplicate column field or enum-name conversions in Kotlin production/test sources
and launcher fixture code. Native terminal interaction and the already-disabled
desktop module remain outside these automated checks.
