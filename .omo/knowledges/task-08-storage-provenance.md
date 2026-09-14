# Task 8 Storage Provenance

- AE2 `StorageService` includes global Grid-service mounts in its complete `NetworkStorage`; that aggregate is not a local
  source list. The qualified source-only seam is an actual node `IStorageProvider.mountInventories` callback, which exposes
  the real delegates and priorities without copying inventory.
- Native identity is qualified only from a provider resolved internally from an actual `IGridNode`. Federation providers cannot label arbitrary
  storage native; they can only create owner-bound projection or route views over a previously qualified handle.
- Managed projections are excluded. Four independent route providers deduplicate one native handle by object identity and
  preserve the highest callback priority. Complete aggregate mounts and unclassified opaque wrappers fail closed with typed
  diagnostics and no partial source list.
- Runtime evidence consumes ordinary semantic facts, injected `NetworkStorage` mount facts, actual delegate listing facts,
  and Federation provider callback mount identities/priorities. Tests do not register the opaque alias they must reject.
- CELLS regressions support source-level election, old/new snapshot ordering, separate topology/filter dirty states,
  coalesced reset reconciliation, and complete listener bootstrap. These remain inputs for Tasks 21-25, not Task 8 scope.
- Canonical repaired schema-v3 evidence: `.omo/evidence/task-08/attempt-20260914T032607415Z/result.json`. All four native
  children exited zero with 20 assertions; immediate consumption and the five-mutation adversarial self-test passed.
