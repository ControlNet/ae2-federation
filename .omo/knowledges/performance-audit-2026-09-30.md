# Performance audit (2026-09-30, static, commit 97b78ff)

Static read-only audit; nothing was profiled yet. "Verified" = re-read by the lead session, not only a subagent.
Remote isolated benchmark host offered by the user (address and credentials kept out of the repository):
(60 cores, 117 GB, Java 21). `~/ae2f` already holds Task 37 `snapshots/`, `evidence/`, `.gradle` from 2026-09-23..25;
do not overwrite them, use a new dated subdirectory.

## Energy (highest risk)
- Verified: `EnergyBindingService.extract` (`energy/EnergyBindingService.java:135`) calls `reconcileAll()` on every
  call. Reconcile rebuilds relationships from `FederationDomainRegistry.snapshot()` (full copy), runs
  `NativeEnergyBackendRegistry.discover` (walks every provider-grid node, `identity.lineage`, string sort) and
  `selectSource` (walks every consumer-grid node) for every SUPPLY relationship in the level. `EnergyCapabilityBinding
  .extract` calls `isCurrent()` twice, each re-running `discover`. `getAECurrentPower` = SIMULATE extract of
  `Double.MAX_VALUE`, same path.
- Verified: `EnergyCapabilityBinding.extract` pulls from the provider's whole AE2 `IEnergyService`, which includes the
  provider grid's own `DirectionalEnergySource`. `EnergyRouteGuard` only blocks re-entering the same directed
  `PolicyKey`, so a mutual SUPPLY mesh explores every edge-simple trail (exponential in mesh size), each hop paying the
  full reconcile. `Double.MAX_VALUE` simulate can never be satisfied, so the whole search always runs.
- Fix direction: no reconcile inside extract (dirty flag from topology/policy/grid events), O(1) currency stamps,
  extract only from `backend.sources()` (native storages) or a visited-grid set.

## Observability
- Verified: `LevelObservabilityService.recordAccepted` builds `new FederationDomainStateProjector(level).snapshot(scope)`
  and `synchronizeProjection` on every accepted storage/energy/processing operation, even with no subscriber.
  Projector copies the policy map, scans all providers/lanes/endpoints, SHA-256 ids (`MessageDigest.getInstance` each).
  Fix: record meter only, mark scope dirty, project once per tick in `sweep()` only for subscribed scopes.
- `dataRevision` in graph/choices strings and `laneSent/laneReturned` in `workspaceChoices`, plus `age` in
  `pairFlowText`, make LDLib2 bindings resend and the client rebuild panels constantly during flow.
- Verified: `FederationDomainPolicyMenuHolder` binds `session.workspaceChoices()` / `graphSnapshotText()` every tick
  without the `CHOICES_TICKS` throttle that `FederationProviderMenuHolder` already has (pattern decode each tick).
- Unpruned: `NativeTransportMeter.windows`, `ObservationSubscriptionService.projections` (keyed by domain generation),
  `FederationDomainRegistry.invalidations` (SOURCE_UNLOADED per removed node).
- `ObservationStateSupport.canonical` throws above 256 members/providers/endpoints or 512 lanes; via recordAccepted it
  can throw inside storage/energy operations (suspected).

## Topology publish
- Every block publish runs Storage + Crafting + Energy `reconcileAll` even when `upsertNode` saw equal evidence
  (Cable/Endpoint/Router/Provider publish methods). Provider republishes on every `GRID_BOOT` without evidence compare.
  `MultipartBridgePart.onNeighborChanged/onUpdateShape` refresh without `onlyIfChanged`.
- `FederationDomainRegistry.recompute` flood-fills the whole previous component per upsert: chunk load of N nodes is
  O(N^2). Domains over 4,096 nodes hit `BUDGET_EXHAUSTED`.
- Fix direction: per-level dirty flag, one recompute + one reconcile at end of tick.

## Storage
- `NativeStorageNotificationHub.MAX_RETAINED_KEYS = 64`: sources over 64 types fail closed, and each reconcile
  recreates and re-snapshots the binding (`getAvailableStacks` full copy).
- Per insert/extract authorization re-walks source nodes and allocates `PolicyService`; `getAvailableStacks` copies
  through a fresh `KeyCounter` plus a `PolicyResource` per key even when the filter allows all.
- Fine: no recursive storage walk, native mount changes coalesced per tick, no steady-state remount churn.

## Client
- `FederationMapPreview` draws one `graphics.fill` per cell (unmanaged flush per call in 1.21.1): thousands of draw
  calls per frame. Resample is counted in frames (`RESAMPLE_FRAMES`), not ticks.
- `FederationTopologyView` render re-derives pairs/network lookups/polylines each frame; `FederationProcessingGraph`
  linear endpoint lookup per wire per frame. `CableFlowRenderer` view distance 256 and per-frame neighbour mask.
- Fine: no chunk re-meshing, idle BE ticks O(1) (capability caches), layout only on structure change.
