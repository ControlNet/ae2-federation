# Federation energy sharing (Quartz Fiber style), 2026-10-01

Replaces the directional ME power supply of Task 31 (`.omo/knowledges/task-31-directional-energy.md` is history).

## Model

- An enabled ME power rule (`PolicyCapability.ME_POWER` with `SUPPLY`) between two networks of a common domain, in
  either direction, whose activation is `ACTIVE`, is an undirected edge. Grids connected by edges share one AE2
  `EnergyOverlayGrid`: mutual and transitive, exactly as Quartz Fibers. One rule is enough; the pair editor shows one
  "share energy" switch per pair, and switching it off on the server turns off a rule held the other way round too.
- `FederationEnergyConnection` implements AE2's `IEnergyOverlayGridConnection` (@ApiStatus.Internal, pinned to
  AE2 19.2.17). It is added as a node service on every Router face boundary node and both Bridge nodes. AE2 registers
  it in `EnergyService.addNode` and asks `connectedEnergyServices()` while it builds a pool (`EnergyOverlayGrid
  .buildCache`), which walks the connections and assigns one sorted service list to every member.
- `EnergySharingService` (per level) derives each Grid's peers from the relationships the domain observer reports,
  the rules and activation, and whether both Grids carry a Federation connection (`getMachineNodes` over the node
  owner classes, so no node scan). Peers must be symmetric: AE2 logs an error when a member already has an overlay.

## Timing (nothing runs per energy operation)

- A rule edit reconciles at once (`PolicyService` -> `reconcileIfPresent`); a changed peer list calls
  `EnergyService.invalidateOverlayEnergyGrid()` on that Grid, which clears the pool from all its members.
- Every `FederationDomainRegistry` mutation (node/bridge evidence change) calls `EnergySharingService
  .topologyChanged(level)` through `onMutation`, which dissolves the level's pools without reading the registry (it
  runs inside the mutation). The next energy operation rebuilds the pool, and `peers()` reconciles first when the
  registry topology revision, policy watermark or identity epoch changed. So a cut path splits a pool in the same tick
  (`topologyBreakCutsPower` asserts this), and a removed Bridge splits it because AE2 removes its connection node.
- `tickAll()` after `FederationBindingRefresh.flushAll()` reconciles levels whose inputs changed.

## Removed

`DirectionalEnergySource`, `EnergyBindingService`, bindings/revisions, provider generations/ledger, route guard,
native backend registry; per-operation energy flow observation (energy no longer reports flows: the graph draws a
shared pair's link energy-green with a breathing glow and two sparks running opposite ways, and the edge pill shows
"⇄ Shared energy"). Diagnostic `ENERGY_SOURCE_MISSING` is gone; `CONSUMER_ENERGY_INTERFACE_MISSING` became
`ENERGY_CONNECTION_MISSING` (one network has no Federation node that can join the pool).

## UI

- `SharedEnergySwitch` (client/policy, unit-tested) picks the rule the pair's one switch reads and writes: the one
  that is on, else the configured one, else first-to-second; a pair shares while either rule's health is ACTIVE.
- Pair editor: the direction sections no longer list ME power; `policy_section_energy` holds one row
  (`policy_row_energy`, `policy_state_energy`, `policy_switch_energy`). UI helpers map `me_power` to these ids.
- Network cards: `NetworkOverview` adds `energyAvailable` (SIMULATE extract of `Double.MAX_VALUE`, which includes the
  pool). A card whose own cells are below 25% but can draw more than they hold reads "Online · Shared energy"
  (green) instead of the low-energy warning. The card's state line hides overflow, so keep that text short.
- Evidence: `.omo/evidence/gui-energy-sharing/` (`ui-policy-runtime-energy-shared`, `ui-graph-energy-shared-link`).

## Tests

- GameTests (`SharedEnergyGameTests`): `energysharedmutual`, `energycoldstart` (reverse rule powers a cold Grid),
  `energysharedtransitive` (rules A-B and B-C; A draws C's 300 AE of 400 requested), `energyruleoffsplits`,
  `energydisconnectsplits`, `energyswitchpairlevel` (session `setPolicy` off from the reverse side turns off both).
- Task 31 QA verifier (`gradle/federation-qa.gradle`) checks the new exact fact schemas and arithmetic;
  `federationTaskThirtyOneEvidenceSelfTest` mutates them.
- `observe.native-flow-once` now measures one Storage projection extraction (8 items, `exactAmount`).

## Pitfalls

- `tools/perf_benchmark.py --remote` rsyncs a file list, which cannot delete files the tree no longer has; deleted
  sources kept compiling remotely. `prune()` now deletes remote files under `<module>/src` absent locally.
- A test that computes a `PolicyKey` from `PolicyBridgeFixtures.mainNetwork()` before the cables have nodes fails
  with "Native neighbor must expose a node on the attachment side": compute it inside `succeedWhen`.

## Performance (remote isolated benchmark host, opsScale 100, 3 runs, medians)

| metric | Federation de0ed9d | native Quartz Fiber | ratio | before (0d7a85f) |
|---|---|---|---|---|
| energy extract MODULATE | 45.0 ns | 39.8 ns | 1.13 | 3.07 |
| energy extract SIMULATE | 23.2 ns | 22.2 ns | 1.05 | 3.86 |
| reconcileSharing (whole level, unchanged pools) | 5.9 us | - | - | - |

- Idle tick read 367 us vs native 215 us in this run, but the same native code read 273 us in the A/B run of
  0d7a85f an hour later (Federation 321 us there): JVM-to-JVM variance of tens of percent, as before. The benchmark
  now counts `idleReconciliations` and `idleDissolutions` over the idle window; both are 0, so sharing adds no
  per-tick work beyond one changed-inputs check in `tickAll`.
