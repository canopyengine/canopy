# Class-based screen navigation

This integration replaces Screen controllers with ordinary Node roots. ScreenManager
navigates and caches by the requested class; SceneManager still owns active-tree
processing and platform presentation. Import the helpers from `io.canopy.engine.app`.

```kotlin
terminalApp {
    initialScreen<Aquarium>()
}.launch()
```

Aquarium is a normal `Node<Aquarium>`, using existing managers and contexts for
dependencies. UI components still belong under UiRoot. No Screen superclass,
wrapper, catalogue or instance registration is needed.

| Operation | Behavior |
| --- | --- |
| `initialScreen<T>()` | Select lazy startup before App launch |
| `navigateTo<T>()` or `navigateTo(T::class)` | Create on first visit, otherwise resume |
| `navigateTo<T> { T(arguments) }` | Supply a constructor factory when needed |
| `reloadScreen()` | Destroy/recreate the current tree with its remembered factory |
| `unloadScreen<T>()` | Destroy an inactive instance, retaining construction information |

Helpers work on App and active Nodes. ScreenManager also exposes typed/class-based
operations. Automatic construction requires a public concrete class and public
no-argument constructor. A supplied factory only runs for creation; cached instances
ignore replacement constructor arguments. Capture stable values or App-owned services,
not fields of a Node that may be destroyed before a remembered factory runs again.

A request commits after manager updates and before UI painting. The latest request
wins; requests from entry wait for the next boundary. Cached inactive trees do not
update, draw or receive input. Per-visit connections/jobs leave with Node activity;
entry/readiness callbacks establish them again. nodeInit content is not rebuilt on
return. App shutdown destroys all active/inactive roots and clears stored factories.

Migrate `Screen.onEnter` construction to Node `nodeInit`; per-visit work uses Node
entry/readiness. Replace `screens { start(instance) }` with `initialScreen<T> { ... }`
and old start/register/remove helpers with navigate/reload/unload. Resize logic reads
SceneManager.sceneSize on entry and subscribes to its resize event. Prompts and other
content are children of the active root; replacing it directly while a screen is
active is rejected. Low-level raw root APIs remain for embedding without screens.

A construction failure keeps the previous selection and rolls back partial Nodes.
Activation/cleanup failures follow existing App diagnostics and complete remaining
cleanup. Shared manager data is not reset automatically. There is one retained
instance per requested class, with no automatic eviction, history or async loading.
