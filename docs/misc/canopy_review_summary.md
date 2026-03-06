# Canopy review summary

## Features / areas to review or change

### 1. Reduce `Node` centralization
**Problem:** `Node` currently carries too many responsibilities: tree structure, transforms, groups, behavior hosting, lifecycle, path logic, prefab semantics, and scene integration.

**Goal:** Keep `Node` ergonomic, but make it less mandatory and less overloaded.

**Suggested changes:**
- Introduce a smaller base node abstraction for identity, parent/children, and lifecycle.
- Move optional concerns into capabilities or helpers, such as transform, behavior hosting, and group membership.
- Keep the current `Node` as the default high-level node type.
- Favor composition for optional features instead of forcing every node into the same shape.

### 2. Separate construction, build, attachment, and activation
**Problem:** object creation, subtree building, parent attachment, and lifecycle activation are tightly coupled.

**Goal:** Make lifecycle phases explicit and independently controllable.

**Suggested changes:**
- Separate these phases:
  1. instantiate
  2. build/configure
  3. attach
  4. activate
- Add explicit primitives such as `buildSubtree()`, `attachChild()`, `enterTreeSubtree()`, and `readySubtree()`.
- Keep today’s one-step convenience API as sugar over those lower-level operations.

### 3. Make manager lookup instance-based first, global second
**Problem:** `ManagersRegistry` is convenient, but global state can become architectural lock-in.

**Goal:** Preserve convenience while enabling multiple app instances, easier tests, and isolated runtime scopes.

**Suggested changes:**
- Introduce an instance-based `ManagerContainer`.
- Let `CanopyApp` own its own container.
- Keep `ManagersRegistry` as the default global convenience container.
- Make sure every global access path has an explicit instance-based equivalent.

### 4. Replace raw string context keys with typed keys
**Problem:** string-keyed context is flexible, but becomes brittle with typos, collisions, and weak refactors.

**Goal:** Keep context ergonomic while improving safety and discoverability.

**Suggested changes:**
- Introduce typed `ContextKey<T>`.
- Keep string keys only as a compatibility or convenience layer.
- Encourage module-scoped or namespaced keys for plugins and larger projects.

### 5. Make `SceneManager` less monolithic and more query-driven
**Problem:** `SceneManager` currently owns many responsibilities and system registration is mostly driven by node type.

**Goal:** Support more flexible matching and avoid turning `SceneManager` into another god object.

**Suggested changes:**
- Support multiple matching styles:
  - by type
  - by interface/capability
  - by predicate/query
  - by group/tag
- Internally split responsibilities into collaborators such as:
  - scene tree ownership
  - path index
  - group index
  - system registry
  - tick scheduler
- Keep the current type-based route as the default, not the only route.

### 6. Keep modularity architectural, not just Gradle-level
**Problem:** module boundaries exist, but lower-level modules can still become too aware of higher-level concerns.

**Goal:** Ensure modules stay independently reusable and backend-neutral where possible.

**Suggested changes:**
- Define strict dependency direction rules.
- Split API modules from implementation modules where useful, such as `logging-api` vs concrete logging backend.
- Keep `core` depending on small abstractions instead of concrete runtime details.
- Regularly ask whether a module can be used outside a full Canopy app.

### 7. Document the manual form of every convenience feature
**Problem:** frameworks feel limiting when users cannot tell how to bypass the sugar.

**Goal:** Make escape hatches official and discoverable.

**Suggested changes:**
- For every convenience API, document:
  - the simple path
  - the explicit/manual path
  - when to use each
  - extension points
- Apply this to DSL tree construction, behaviors, manager lookup, context lookup, and scene operations.

### 8. Prefer composition-first extension points
**Problem:** too much extension pressure naturally flows into inheritance.

**Goal:** Keep inheritance convenient, but make composition the stronger long-term extension model.

**Suggested changes:**
- Add extension surfaces like:
  - lifecycle listeners
  - node features/plugins
  - scene hooks
  - matcher/query strategies
  - manager factories
- Let subclassing remain the easiest route for common use, but not the only powerful one.

### 9. Keep the core broad enough for headless and non-visual use
**Problem:** some current abstractions still risk assuming a graphical 2D engine first.

**Goal:** Make headless, simulation, tooling, and future UI layers feel native rather than secondary.

**Suggested changes:**
- Check whether core APIs assume graphics, LibGDX types, spatial nodes, or one specific loop model.
- Move rendering- or 2D-specific assumptions above the lowest core layer.
- Treat headless support as a design constraint that keeps the architecture honest.

## Suggested priority order

### Phase 1: clarity without major rewrites
- Document lifecycle phases clearly.
- Document manual forms for convenience APIs.
- Add typed `ContextKey`.
- Clarify which APIs are stable, experimental, or internal.
- Explicitly frame globals as convenience rather than foundation.

### Phase 2: reduce coupling
- Introduce instance-based `ManagerContainer`.
- Separate build / attach / activate operations.
- Refactor `SceneManager` internally into collaborators.
- Keep current APIs as wrappers to preserve ergonomics.

### Phase 3: broaden the core
- Introduce a smaller base node abstraction.
- Move transforms / behavior / groups into optional capabilities.
- Keep the current `Node` as the default higher-level node type.

---

# Canopy architecture review rubric

Use this rubric when reviewing a subsystem, API, or feature.

## A. Core abstraction quality
Score each item from **0 to 2**.

### 1. Generic enough
- **0** = encodes one use case too narrowly
- **1** = somewhat reusable
- **2** = clearly usable across games, sims, tools, and headless flows

### 2. Policy separated from mechanism
- **0** = only one built-in way
- **1** = some customization
- **2** = reusable primitives with replaceable policies/defaults

### 3. Low-level manual form exists
- **0** = only DSL or implicit path
- **1** = partial explicit API
- **2** = complete explicit API under the convenience layer

### 4. Easy to opt out of defaults
- **0** = hard-wired
- **1** = awkward to bypass
- **2** = easy to bypass or replace

## B. Extensibility

### 5. Extension points are explicit
- **0** = users must read internals to extend
- **1** = some hooks are visible
- **2** = stable, intentional, documented extension surfaces

### 6. Composition works better than inheritance
- **0** = inheritance required
- **1** = mixed approach
- **2** = composition-first, inheritance optional

### 7. Weird use cases can work without forking
- **0** = no
- **1** = maybe, with hacks
- **2** = yes, reasonably

## C. Dependency and modularity health

### 8. Independently adoptable
- **0** = drags much of the engine with it
- **1** = somewhat independent
- **2** = easy to use standalone

### 9. Dependency direction is clean
- **0** = lower layers know too much about higher layers
- **1** = some leakage
- **2** = clean layering

### 10. Global state is optional
- **0** = mandatory
- **1** = partially optional
- **2** = clearly optional, with instance-scoped alternative

## D. Runtime behavior

### 11. Lifecycle is explicit and predictable
- **0** = hidden or ambiguous
- **1** = mostly understandable
- **2** = well-defined and independently controllable

### 12. Ordering is deterministic
- **0** = surprising
- **1** = mostly stable
- **2** = explicit and documented

### 13. Testable in isolation
- **0** = painful
- **1** = possible with setup tricks
- **2** = easy to instantiate and test in isolation

## E. Long-term maintainability

### 14. Custom data is first-class
- **0** = rigid
- **1** = some escape hatches
- **2** = natural support for user-defined metadata, services, and config

### 15. Public API is intentionally bounded
- **0** = internals leak everywhere
- **1** = somewhat controlled
- **2** = compact public surface with clear internal boundaries

### 16. Avoids creating a god object
- **0** = yes, strongly concentrated responsibility
- **1** = maybe / somewhat overloaded
- **2** = responsibilities are well-bounded

## Score interpretation
- **28–32**: very healthy
- **22–27**: good, but watch rough edges
- **16–21**: useful, but likely to become limiting
- **0–15**: likely to create framework friction later

## PR review template

```md
## Architecture review

**Subsystem:**
**Purpose:**

### Scores
- Generic enough:
- Policy vs mechanism:
- Manual form exists:
- Opt-out possible:
- Extension points clear:
- Composition-first:
- Weird use cases possible:
- Independent adoption:
- Dependency direction clean:
- Global state optional:
- Lifecycle explicit:
- Ordering deterministic:
- Testable in isolation:
- Custom data support:
- Public API bounded:
- Avoids god object:

**Total:** / 32

### Main strengths
-
-

### Main lock-in risks
-
-

### Recommended next step
-
```

