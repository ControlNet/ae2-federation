# Bus farm benchmark: one AE2 network vs AE2 subnets vs Federation (2026-10-24, dev)

User question: a main network storing 10 000 item types, shared to (a) 10 subnets with 1000 import/export buses each,
(b) 1000 subnets with 10 buses each. Compared against one AE2 network with the same types and buses, and against AE2's
own subnet linking (Quartz Fiber for energy, Storage Bus on an ME Interface for items).

## How to run
```
python3 tools/perf_benchmark.py --tests perfbusestensubnetsdirect perfbusestensubnetsnative \
  perfbusestensubnetsfederation perfbusesthousandsubnetsdirect perfbusesthousandsubnetsnative \
  perfbusesthousandsubnetsfederation --repeat 3 --label buses-10k-full -P federationPerfNodeDataProbe=false
```
About 25 minutes; the 1000-subnet Federation run alone takes ~4.5 minutes (setup and teardown, see below).

## Scene (`test/perf/BusFarmScene`, TEST-ONLY)
- Main network: one yellow cable plane, one creative energy cell, `BulkItemStorage` (TEST-ONLY) with 10 000 keys:
  registered items, then paper with distinct `custom_data`.
- Each subnet: a block of columns; each column stacks 5 chests between cable layers, an Export Bus (filtered to one
  key) under each chest and an Import Bus above it, so every pair cycles one item; one all-cable riser per block.
  No storage and no energy of its own. Neighbouring blocks alternate red/blue cable.
- DIRECT: all cable yellow, one Grid. NATIVE: Storage Bus on the riser facing an ME Interface on the main plane,
  Quartz Fiber from the main plane to the column beside the riser. FEDERATION: Bridge on the main plane over the
  riser; storage + ME power (SUPPLY) rules for every subnet in one `editAll`.
- Every Grid is forced to AE2's infinite channel mode (`PathingService.setForcedChannelMode`, as AE2's TestPlots);
  `AEConfig.setChannelModel` throws in a GameTest server (no config object to save). Force ALL grids in each
  readiness pass: a subnet left waiting idles its buses to the slowest rate.
- Built outside the test structure on force-loaded chunks, 2000 placements per tick; the world is deleted after each
  benchmark server, so rules are not turned off at teardown.

## Measurement pitfalls found
- `MinecraftServer.getTickTimesNanos()` (used by `PerfMeasure.TickWindow`, every existing `idleTick`) is recorded
  BEFORE `ServerTickEvent.Post`. AE2's Grid end-of-tick work (`StorageService.onServerEndTick`, which triggers
  Federation's `nativeMountsChanged` -> `reconcileAll`) and Federation's own tick work (`NeoForgeEntrypoint.onServerTick`
  -> statistics) run in Post and are missed. A 150 s tick showed as 2 s. `FullTickClock` times Pre (HIGHEST) to Post
  (LOWEST); the bus benchmarks use it. Bus work itself runs in level ticks and was counted either way.
- AE2 bus rate: FASTER -2 ticks, SLOWER +1, IDLE -> max (Import 40, Export 60), min 5. A scene whose buses idled during
  setup needs ~1000 ticks to reach full rate; warm-up is adaptive (99% of buses/5 ops per tick over 100 ticks, cap
  3000). `busRate` is reported; all layouts reached ~100%.
- The testmod's `NativeNodeDataProbeService` logs every node add/save (36k lines for this scene);
  `-P federationPerfNodeDataProbe=false` skips registering it.
- Host was shared (ffmpeg ~17 cores, load ~21/60). `maxTick` varies 25-180 ms run to run for the same test; compare
  medians, and max only where it is consistent across all three runs.

## Results (median of 3, whole ticks, ms)
| scene | layout | median | mean | max | setup | one rule edit | teardown |
|---|---|---|---|---|---|---|---|
| 10 x 1000 | one network | 11.5 | 11.7 | 30 | 5.3 s | - | 0.6 s |
| 10 x 1000 | AE2 subnets | 11.8 | 12.8 | 41 | 3.2 s | - | 0.3 s |
| 10 x 1000 | Federation | 13.2 | 14.2 | 25-100 (noisy) | 3.5 s | 2-10 ms | 0.3 s |
| 1000 x 10 | one network | 18.9 | 18.7 | 28 | 7.6 s | - | 0.6 s |
| 1000 x 10 | AE2 subnets | 19.3 | 19.9 | 32 | 3.9 s | - | 0.4 s |
| 1000 x 10 | Federation | 19.5 | 22.0 | 98-116 | 159 s | 170-205 ms | 57 s |
All layouts moved ~2000 bus operations per tick (10 000 buses at AE2's 5-tick rate). `authorityEpochs` and
`dependencyRefreshes` stayed 0 in every steady window.

## Federation scaling problems found (1000 Bridge domains, 2000 rules)
1. Setup: one ~150 s tick. Slow-tick stacks: `StorageService.onServerEndTick` -> `StorageMountService
   .nativeMountsChanged` -> `reconcileAll` -> `StorageDependencyIndex.refresh` -> `StorageFederationDomainObserver
   .references` -> `FederationDomainRegistry.federationdomainsFor`, which returns `Set.copyOf` of the main network's
   1000 domains for each of the 1000 relationships; `reconcileAll` runs for every changed storage service.
2. Each rule edit: `PolicyService.edit` runs full `reconcileAll` of storage, crafting and energy: ~200 ms at 1000
   subnets (~2-10 ms at 10).
3. Steady state: one ~100 ms tick every ~2 s from `FederationStatistics.sample` -> `EnergyPoolReading.measure` ->
   `FederationEnergyConnection.listedEnergyServices` -> `EnergySharingService.listedPeers`: every network's reading
   lists the main pool's 1000 peers.
4. Teardown (removing 1000 Bridges with everything else in one tick): 57 s.
Not covered: 1000 networks in ONE Switch/cable domain (`relationships()` pairs every member with every other).

## Fixes for 1 and 2 (2026-10-25)
JFR on the 1000-subnet run (`jcmd <game pid> JFR.start settings=profile dumponexit=true filename=...`, aggregate
server-thread `jdk.ExecutionSample` stacks; `jfr print --json` is 5.8 GB for 4 minutes, stream the text output instead):
13433 of 15072 server samples in storage `reconcileAll`, 10154 of them via `nativeMountsChanged`, 11071 in
`federationdomainsFor`'s `Set.copyOf`, 1386 in `PolicyActivation.classify` -> `shareFederationDomain`.
- `FederationDomainRegistry.sharedFederationDomainReferences` gives the storage and crafting observers the common
  domains without copying, walking the smaller membership.
- `shareFederationDomain` used `Collections.disjoint(first, second)`, which walks its SECOND argument whenever the first
  is a set, whatever the sizes: (subnet, main) walked main's 1000 ids. The `share*` checks now walk the smaller set.
- `nativeMountsChanged` only marks the service; `reconcileNativeMountChanges` runs once per tick from a LOWEST
  `ServerTickEvent.Post` listener, after AE2's NORMAL Grid end ticks (Federation's own HIGH listener runs before them,
  so draining there would be a tick late). `tick` also drains a leftover mark.
Rule edits still reconcile everything (storage, crafting, energy), now O(networks) each.

Median of 3 (`--label buses-10k-fix12 --compare buses-10k-full`, Federation layouts only):

| Federation | 1000 x 10 before | 1000 x 10 after | 10 x 1000 before | 10 x 1000 after |
|---|---|---|---|---|
| setup max tick | 152 s | 1.3 s | 473 ms | 326 ms |
| setup | 159 s | 6.0 s | 3.4 s | 3.1 s |
| editAll of 2000 / 20 rules | 310 ms | 114 ms | 65 ms | 64 ms |
| one rule off / on | 200 / 171 ms | 41 / 25 ms | 8 / 2 ms | 8 / 2 ms |
| steady median / mean tick | 19.5 / 22.0 ms | 17.0 / 18.8 ms | 11.7 / 12.4 ms | 9.8 / 10.3 ms |
| teardown | 57 s | 7.5 s | 0.29 s | 0.29 s |
The steady-tick drop was not separated from host load (the earlier runs shared the host with ffmpeg); the ~100 ms statistics tick
every ~2 s (point 3) is unchanged.
After the fix the remaining Federation samples are mostly teardown and setup Bridge `setStatus` -> three
`reconcileIfPresent`/`observeConnectedGrids` per Bridge (one full reconcile per Bridge change, point 4) and the
statistics sample (point 3).
