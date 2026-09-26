# Task 22 Storage Provenance Lifecycle

## Identity model

- `OriginNetworkId` is the settled Task 4 `NetworkId`; positions, Bridge identities, route labels, and runtime `IGrid`
  object identities are never source keys.
- `SourceAliasId` is the persisted native registration node UUID plus callback index. It is obtained only while replaying
  the current active node's real `IStorageProvider.mountInventories` callback.
- `ExportSourceId` is the lowest callback-owned alias of one identity-equal native `MEStorage`. Multiple callback aliases
  merge to one `ExportSource` and preserve the highest native priority.
- `SourceGeneration` advances when the callback snapshot, native store handle, source nodes, or runtime Grid changes.
  `MountGeneration` independently advances on relationship unmount/remount so an old held projection cannot invalidate or
  authorize a newer mount.

## Lifecycle

- A native Grid rebound retains an ExportSource only when the new Grid has the same settled `NetworkId` and at least one
  callback-owned source identity derived from the same persisted native node lineage. The runtime Grid and native storage
  handles may change; the old source generation becomes non-current before new operations.
- Task 21 still owns exactly one directional `PolicyKey` provider. It now aggregates only `ExportSource` records, delegates
  operations directly to their native `MEStorage`, and validates both source and mount generations before every operation.
- `RelationshipStorageProvider`, `NativeStorageProvider`, and managed storage views implement explicit marker interfaces,
  so Federation imports and recursive projections are excluded before native source publication.

## Unsupported boundary

- One callback may repeat the same native `MEStorage`; aliases collapse and the maximum callback priority wins.
- A callback exposing multiple distinct untyped storage handles is conservatively classified as
  `OPAQUE_EXTERNAL_ALIAS`. The complete source domain is rejected atomically and the previous generation is invalidated.
  This intentionally does not claim arbitrary third-party wrapper deduplication. A future supported adapter must provide
  registration-owned source evidence rather than caller labels, wrapper descriptions, route strings, positions, or
  aggregate equality.

## Evidence

- Canonical cases: `provenance.multi-entry`, `provenance.native-rebind`, `provenance.exclude-import`, and
  `provenance.opaque-boundary`.
- Native property facts are correlated with independent `AE2F_PROVENANCE_NATIVE` and rejection records in each hash-bound
  execution log. The adversarial matrix fully rebinds identity, generation, import, opaque, quantity, capacity, malformed
  record, stale-generation, and cleanup mutations.
- Fresh Task 8 and Task 21 producer, consumer, and adversarial regressions passed after changing their source/mount boundary.

## Independent-review repair

- Callback coordinates are captured before exclusion. Each qualified entry carries its literal raw callback index through
  managed-view filtering, duplicate collapse, and `SourceAliasId` construction; a filtered list is never re-enumerated.
- The native negative replays `[managed, native-A]` from a persisted provider registration, tears down the original Grid,
  restores that exact provider lineage with `[native-B]`, and observes native slots `1` then `0`. The same settled origin
  does not substitute for callback continuity: discovery emits `UNPROVEN_GRID_REBOUND` and invalidates the prior domain.
- The mount lifecycle now captures projection A and its source/mount generations, replaces the native chest cell, and
  reconciles projection B with newer generations. Listing, insertion, and extraction through A all return zero; exact
  provider-removal counts remain unchanged after those stale calls, while B remains registered and performs native
  insert/list/extract operations.
- `AE2F_PROVENANCE_CALLBACK` and `AE2F_PROVENANCE_MOUNT` independently bind callback slots, projection identities,
  generations, provider removals, stale results, and current projection operations to persisted properties. Fully rebound
  callback-collision and stale-projection-acceptance probes must fail for their dedicated semantic reasons.

## Identity-settlement gating repair

- `ProvenanceStorageFixture.ready()` is a discovery-ready predicate, not merely an AE2 boot predicate. It requires an active
  node, a booted Grid, and the same present confirmed `NetworkId` that `NativeSourceDomainRegistry.discover()` requires.
- Provider-node attachment can merge or reconfigure the runtime Grid after initial chest readiness. During that window the
  Grid can be active and booted while Task 4 settlement is absent; the fixture waits through `succeedWhen` rather than
  catching `UNSETTLED_ORIGIN`, sleeping, retrying a fixed count, or weakening production rejection.
- Deterministic regression coverage source-binds fixture readiness to `FabricRegistryAccess.confirmedNetworkId(grid())`.
  Three serial exact four-case producers plus consumers passed; the final accepted repair evidence is
  `.omo/evidence/task-22-settlement-run-3/attempt-20260919T145629768Z/result.json`.
