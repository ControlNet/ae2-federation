# Task 13 Fabric Registry

## Registry model

`FabricRegistry` maintains direct Bridge Fabrics separately from physical Hub/Cable components. Direct Bridges have a
stable `FabricSourceId`; physical components are built only from loaded reciprocal Federation-port evidence. Memberships
are indexed incrementally by settled `NetworkId`, while each attachment retains its own `FabricSourceId`.

Invalidation removes affected Fabric snapshots and network indexes before bounded recomputation. `FabricReference`
contains the generation of the observed snapshot, so topology changes reject stale routing inputs immediately. Node and
port visit limits are explicit in `FabricRecomputeBudget`; exhaustion leaves the affected component invalid rather than
publishing partial truth.

## Native identity integration

Federation-owned Bridge and Hub boundary nodes are adapters, not independent native networks. When created beside an
already settled native Grid in a loaded adjacent position, they receive a fresh node lineage under that Grid's durable
`NetworkId` before AE2 node creation. Both Hub and Bridge seeding prove `ServerLevel.isLoaded` before querying the exposed
neighbor node, so initialization never retrieves or force-loads an unloaded chunk. Existing managed-node NBT is preserved
on reload. Genuine native Grid merges remain governed by Task 4 and continue to fail closed as ambiguous.

## Runtime QA

The Bridge diamond uses color-isolated native cables so all five native Grids settle before Bridge insertion and no
temporary native join/split contaminates identity evidence. Repeated Hub membership extends one native bypass segment per
tick, preventing concurrent temporary Grid lineages.

The exact cases are `fabric.bridge-diamond`, `fabric.hub-merge-split`, `fabric.redundant-membership`,
`fabric.partial-unload`, and `fabric.reject-stale-route`. Canonical schema-v3 evidence is
`.omo/evidence/task-13/loaded-guard-repair-20260915/attempt-20260914T174455385Z/result.json`. Fresh production, persisted
consumption, adversarial membership/identity/trace/attempt/stale-route probes, and the strict build all pass. The Bridge
diamond lives in `FabricBridgeGameTests`; the remaining Hub/Cable Fabric cases live in `FabricGameTests`, keeping both
test modules below the 250 pure-LOC ceiling without changing registered test IDs.

## 2026-09-30 AE2-style registry (supersedes the all-or-nothing reciprocity above)

- A link exists only while both ports name each other. A one-sided declaration is pending: it links nothing, is
  reported on its node as `NON_RECIPROCAL_EDGE` in `invalidations`, and no longer withdraws the rest of the component.
- An `Unsettled` native port contributes no membership and is reported as `IDENTITY_UNSETTLED` on its node; the
  domain stays installed with its other members.
- A change still recomputes the affected components by a bounded BFS (budget exhaustion still installs nothing), but
  each component inherits an old id, scored by equal member-network set, then shared members, shared nodes, older id.
  Physical ids are `physical:<sequence>` and never reused.
- A domain's generation changes only when its set of member networks changes (or it is new). Cables and Endpoints
  joining or leaving keep references current; a split that separates members, and a merge, stale them at once.
- `topologyRevision` advances only on an actual snapshot change. Energy, crafting and storage authority no longer
  compare it; they hold their domains by reference (Phase 3 commit `8501eb0`).
- The Task 13 GameTests now assert that left and right stop sharing a domain in the same tick, not that their indexes
  are empty. `verifyTaskThirteenEvidence` filtered artifacts by `native-domain` while they are named
  `native-federationdomain…`, so the gate could never pass; fixed, and the canonical set passes.
- Tests: `FederationDomainRegistryTest` (incl. a 4000-step randomized comparison against a from-scratch partition of
  mutual links) and the `topology.*` GameTests.

