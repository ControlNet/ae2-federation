# Task 21 Native Storage Mount

## Runtime model

- One `StorageMountService` tracks observed directional relationships by `PolicyKey` and owns at most one
  `RelationshipStorageProvider` per active key. Repeated Bridge or Hub observations reconcile the same logical entry.
- Bridge publication observes both directions between its two native Grids. Hub publication observes ordered pairs of
  native member Grids. Accepted Policy edits and deletes immediately reconcile already observed relationships.
- A relationship mounts only when Task 14 activation is `ACTIVE` and Task 8 discovery finds active, booted native
  `IGridNode` providers. Inactive, disconnected, revoked, or backend-unready relationships remove their global provider.

## Native authority

- Source discovery iterates `providerGrid.getNodes()`, resolves each node's `IStorageProvider`, and qualifies its callbacks
  through `NativeStorageProvenance`. The complete Grid aggregate, managed projections, and opaque aliases remain excluded.
- `AuthorizedStorageProjection` delegates directly to native `MEStorage`, preserving `AEKey`, amount, `Actionable`,
  `IActionSource`, accepted quantities, source priority, preferred-storage behavior, and available-stack values.
- Every operation rechecks current Policy activation, operation permissions, filters, and source-node readiness. A held
  projection therefore fails closed after revocation even after its global provider has been removed.
- Multiple qualified native delegates are combined only in an AE2 `NetworkStorage`; no custom allocator, cached inventory,
  transaction engine, or shadow mount table is introduced.

## Runtime fixture lesson

- An ME Chest with an item cell registers `IStorageProvider`, but its node is not an exportable ready backend until the
  native Grid is powered. Task 21's Bridge fixture explicitly installs cells and adjacent creative AE power before Bridge
  observation. This mirrors the proven Task 8 fixture and avoids sleeps or retries.

## Evidence

- Canonical cases: `storage.native-access`, `storage.priority`, `storage.view-only`, `storage.simulate`, and
  `storage.reject-revoked`.
- Current exact producer and persisted-consumer evidence:
  `.omo/evidence/task-21/attempt-20260919T090525880Z/result.json`.
- `federationTaskTwentyOneEvidenceSelfTest` fully rebinds and rejects fabricated native access, priority, VIEW-only,
  simulation, revocation, and missing runtime-trace claims.
- Focused contract, exact producer, persisted consumer, adversarial self-test, strict `check build`, and Java diagnostics
  all pass with strict dependency verification and no configuration cache.

## Independent-review repair

- `StorageFabricObserver` retains only loaded, identity-current Grids and derives ordered relationships from current Task
  13 Fabric snapshots. Federation Cable publication/invalidation now triggers topology reconciliation, so separately loaded
  Hubs mount after reciprocal component completion and fail closed on split.
- Held projections re-run qualified discovery and compare callback storage identity plus priority on every operation.
  Changed or invalid callbacks and inactive nodes remove the registered provider before denying the operation.
- `LevelEvent.Unload` closes the per-level mount service, unregisters every global provider, and removes mount/Fabric static
  entries. The exact repair evidence is
  `.omo/evidence/task-21-repair-final/attempt-20260919T101729997Z/result.json`.
- Runtime evidence now uses two cable-connected Hubs, two redundant Bridge routes, priority 37, a native `PlayerSource`,
  allow/deny filters, route removal, repeated revocation/reactivation, invalid second callbacks, native cell callback
  replacement, provider inactivity, and five level-unload cleanup receipts.

## Repair round 2 evidence closure

- The redundant-route case inserts 13 iron into the native provider while both routes coexist. The provider and consumer
  aggregate each report 13. Simulated aggregate insertion capacity is `16243`, exactly provider remaining capacity `8115`
  plus consumer-local capacity `8128`; a duplicated provider would violate this equation.
- `StorageLevelLifecycle.close` is the shared production/test teardown path. Its receipt proves a service and registry were
  present, one mounted provider was removed, the exact registered entries were removed, and no observation recreates them.
  The held native consumer aggregate visibly changes from 5 to 0 without mutating the provider source.
- `verifyTaskTwentyOneEvidence` reconciles operation-derived quantities and cleanup facts from hash-bound properties and
  per-case runtime traces. The self-test fully rebinds doubled/missing quantity/capacity and false cleanup receipts.
- Final canonical round-2 evidence is regenerated after all source and documentation edits under
  `.omo/evidence/task-21-repair-round-2-final/`.
