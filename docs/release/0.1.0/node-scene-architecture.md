# Node and scene runtime: maintainer map

This note describes the current core implementation, not a new public API or a
complete redesign. UI presentation is a separate layer on the same Node tree.

## Responsibilities

- `Node` is the guarded public facade: identity, paths, hierarchy queries,
  lifecycle hooks, behavior dispatch, and owned resource helpers.
- `NodeState` is the engine-owned payload. It stores physical parent/children,
  ordered local groups, entry generations, behavior, and cleanup registrations.
  Child list/map snapshots are cached until a structural mutation invalidates them.
- `NodeHierarchy` validates and applies rename, attach, remove, reorder, and
  reparent operations. The facade retains destruction-phase detachment and path
  refresh because those must work while gameplay state is invalid.
- `SceneManager` owns live scene roots, retained node states, node/system indexes,
  frame and physics phases, queued destruction, and batched hierarchy signals.
  `TreeSystem` owns its own type-matched, ordered membership snapshot.
- `SceneGroups` owns the bidirectional scene group index. This is distinct from
  each node's local `groups` set.

## Lifecycle and mutation contracts

Construction records new nodes for rollback. First entry runs configuration
once, then registers the subtree and groups and calls entry hooks parent-first.
Readiness runs child-first once per entry. Exit runs child-first and attempts
callbacks and resource cleanup even if one fails; ordinary removal leaves nodes
reusable. `queueFree` drains at the outermost frame/physics boundary. Permanent
destruction clears the retained payload and invalidates the facade.

Physical mutations validate the applicable ownership, sibling-name, cycle, and
declarative managed-child restrictions before editing the tree. SceneManager
batches structural notifications so observers see a completed nested mutation.
Its internal `hierarchyGeneration` advances immediately on structural and
entered-state changes so non-reactive caches can invalidate before publication.
Same-tree reparenting preserves entry lifetimes and system registration; moving
outside the entered tree exits and unregisters the subtree. Path refresh follows
rename and attachment, including descendants.

Groups retain insertion order. An unchanged membership update neither reorders
members nor publishes a structural change; a real remove/re-add appends. Direct
`SceneManager.addToGroup` changes only the index, not `Node.groups`, and the next
`updateGroups` reconciles the index to the local set. `signalGroup` iterates a
snapshot, skips members removed during callbacks, and defers newly added members
until the next call. Removing the last member removes the group key, so signaling
that absent group fails.

## Performance status and next candidates

The group-index update applies set differences instead of removing and adding
every membership. Dispatch skips a second inherited pause check for nodes with
no behavior. These are algorithmic improvements, **not benchmark results**.

Potential hot paths still need profiling before changes: inherited pause checks
on deep trees, `SceneManager.hasDrawables` scanning live drawing nodes,
`systemsFor` scanning registered type keys when membership changes, and
context-transparent path lookup. Do not add caches or pools without measuring
their cost and proving invalidation across local and hierarchy changes. A later
lookup/query extraction may shorten `Node`, but must preserve reactive reads,
transparent scopes, path diagnostics, and inline API access.
