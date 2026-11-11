# Platform modules

Platforms compose the shared engine with backend services and application hosts.
Adapters integrate external libraries; reusable Nodes and UiNodes stay in the
engine. This organization adds packages, not new Gradle modules or public APIs.

## Terminal

| Package under `io.canopy.platforms.terminal` | Responsibility |
| --- | --- |
| `app` | `TerminalApp`, lifecycle driving and the public `terminalApp` builder |
| `ui` | `TerminalUiBackend`, styled UI spans and command-prompt presentation |
| `graphics` | Bounded frame composition, styled terminal cells, changed-cell output and safe output text |
| `input` | Line-input publication and lifecycle-frame acknowledgement |
| `runtime` | Native terminal creation, macOS safe-input selection and Windows console output modes |
| `data.assets` | Terminal file/classpath assets and their manager |

The host supplies native ticks and operations; App/EngineLoop own preparation,
geometry notification, physics/update processing, presentation and abort. The UI backend draws
shared elements into spans; the graphics surface combines UI, world output and
command rows into one terminal frame. Native setup belongs to runtime support.
These implementation types remain internal. The public builder stays at
`io.canopy.platforms.terminal.app.terminalApp`.

Tests mirror these packages. Application lifecycle/input-shutdown/resize
integration tests stay in `app`; UI and cell-rendering tests sit beside their
corresponding responsibilities. Experimental JVM host code remains in test-only
`experiment`, not production. Synthetic cell benchmarks import the internal JVM
class from `graphics`; those probes are not a supported consumer API.

## Headless and desktop

Headless already has a small `app` package; its LibGDX host implementation lives
in the LibGDX adapter. It needs no empty UI/rendering folders.

Desktop already separates `app`, `graphics` and `physics`. Its app configuration
and startup helper now use the matching `io.canopy.platforms.desktop.app`
namespace; desktop tests now follow desktop graphics/physics packages instead
of obsolete backend paths. Desktop remains excluded
from the enabled build. This organization does not restore or validate desktop.
Add a responsibility package only when a platform actually implements it.
