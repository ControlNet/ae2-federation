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

## Fix for 3 (2026-10-25)
JFR filtered to stacks through `FederationStatistics.sample` (~6 samples per sampled second): half in
`EnergyPoolReading.measure`, a quarter in `pooledUsage`. Every Bridge puts its own `FederationEnergyConnection` on the
main Grid, so the main energy service had 1000 overlay links, each listing the main Grid's 1000 peers (1M adds per
reading), and `pooledUsage` summed the 1001-member pool again for each of the 1001 networks.
- `measure` lists a Grid's Federation peers once (every Federation link on a Grid reaches that Grid's peers) and sums
  the members' average and idle usage into the reading (`drain`, `idle`); `pooledUsage` uses those sums.
- GameTest `energypoolreadingmanybridges`: a small `BusFarmScene` (3 subnets, one Bridge each) checks the reading from
  each of the four Grids against per-Grid sums. `BusFarmScene.deleteRules()` cleans up for shared batch levels.
Steady 1000 x 10 max tick (median of 3, `buses-10k-fix3` vs `buses-10k-fix12`): 100 -> 46 ms (runs 44-58 vs 91-107);
median/mean unchanged. `setupMaxTick` at 1000 subnets swings 1.1-2.4 s run to run both before and after (the Bridge
linking tick, point 4). AE2's own overlay build (`connectedEnergyServices`) still walks 1000 x 1000 once per pool
re-form; not measured separately.

## Fix for 4, linking half (2026-10-25)
A Bridge that links (status with a membership candidate and confirmed ids) registers its two Grids with the storage,
crafting and energy services at once (`registerConnectedGrids`) and leaves the reconciliation to
`FederationBindingRefresh`, which cables and routers already use (one reconcile per level when the server tick's
HIGH Post listener flushes). A Bridge that stops linking after it linked still reconciles at once (`request` +
`flush`): `storagepriority` pins that removing one of two redundant Bridges keeps the held projection working in the
same tick, and a deferred reconcile fails it closed until the flush. A Bridge that was never linked only requests.
Pitfalls hit on the way:
- Deferring the Grid registration too (a member supplier read at flush time) failed three subscription tests: register
  at once, defer only the reconcile.
- `StorageSubscriptionService.updateTargets` invalidates every target consumer on every reconcile, so each extra
  reconcile counts one consumer delivery even when nothing changed (pre-existing). `DirectSubscriptionFixture.ready()`
  and `ChainStorageFixture.bridgesReady()` now also wait for `!FederationBindingRefresh.pending(level)`, so their
  baselines start after Federation has reconciled the Bridge.
- A GameTest that fails inside its final assertion and is retried by `succeedWhen` reports the LAST message, which can
  be an earlier "Waiting for ..." (storagepriority removed its own Bridge, then waited on it forever).
1000 x 10 Federation, median of 3 (`buses-10k-fix4` vs `buses-10k-fix3`): setup max tick 2.4 s (1.1-2.4) -> 1.05 s
(1.03-1.18), setup 6.1 -> 3.4 s; teardown unchanged at 7.5 s (removals stay synchronous); one rule on 26 -> 33 ms,
off 42 -> 46 ms (10-subnet numbers also rose 3-13% in that run, not separated from host load). The remaining 1 s
setup tick is the scene placing 2000 AE2 parts plus AE2 `CableBusBlockEntity.onReady` (one-network and AE2-subnet
layouts: 1.5-1.9 s in `buses-10k-full`).
Open: deferring removals too would make teardown/unload one reconcile, at the cost of one tick fail-closed on a
redundant route (what cables already accept) and `storagepriority` asserting after one tick.


## One domain, 1000 networks (`perfdomainthousandnetworks`, 2026-10-25)
Scene `test/perf/DomainFarmScene` (TEST-ONLY), the owner's topology: 63 Router bodies 3x3x1 in a row along X, each
pair joined by a 3x3 bundle three long (`BundledCables.form`, unlimited capacity). From the middle of each Router's
north/south face a plain cable two long leads to a 1x1x2 Switch body; its 8 free side faces carry 8 networks (one
coloured glass cable each, 8 colours), exactly one plain cable's capacity. 125 subdomains = 1000 networks; network 0 is
main (creative cell + `BulkItemStorage`), the other 999 get storage + ME power rules in one `editAll`. Neighbouring
subdomains stay a block apart. Steps measured with a per-tick running max (`FullTickClock.lastTickNanos`): setup, idle
(100 settle + 100 window), one rule off/on, break/restore the farthest subdomain's plain cable, teardown.
```
python3 tools/perf_benchmark.py --tests perfdomainthousandnetworks --repeat 1 --label domain-1k-first \
  -P federationPerfNodeDataProbe=false
```
~1 minute. First numbers (`domain-1k-first`): setup max tick 4.7-9.1 s, all-rules edit 2.0 s, idle median 3.7 ms,
one rule off/on 2.3 s each, cable break tick 2.7 s, restore 2.2 s, teardown 1.05 s.

JFR (attach to the JVM with `-Dfml.modFolders`, not the first java with "gametest" in its args):
- `Storage/Crafting/EnergyFederationDomainObserver.relationships()` build every ordered member pair of every domain:
  999,000 pairs here, each with two `confirmedNetworkId` lookups, then each service's `reconcileAll` reads
  `PolicyService.configured` per pair. Every rule edit runs it three times synchronously; every topology change runs it
  through `FederationBindingRefresh`. All consumers skip pairs without a configured rule (UI status only asks for
  enabled rules), so enumerating configured rules of the capability whose two networks share a domain is equivalent.
- `FederationCableBlockEntity.syncCapacity -> CableCapacity.demand` is cached per evidence revision and domain, but
  Router/cable ticks publish evidence between cable ticks during setup and breaks, so the whole domain is recounted
  several times a tick; `FederationDomainRegistry.flush` recompute + `judgeCapacity` show up under it (lazy flush is
  charged to the first caller).

### Fix: pair networks by configured rules (2026-10-25)
`PolicyService.configuredRules(capability)` (store walk, O(rules)); the three observers' `relationships()` iterate
those rules, look both networks up in `ObservedGrids.confirmed()` and keep the pair when
`registry.shareFederationDomain` holds. Equivalent output for every consumer (they skip unconfigured pairs).
Named `configuredRules`, not a `configured` overload: a GameTest calls `configured(context.get(..))`, which became
ambiguous. `StorageMountContractTest` pinned the old member loop; `PolicyContractTest` now pins the rule loop.

`domain-1k-rules` (median of 3): all rules 2.0 s -> 95 ms; one rule off/on 2.3 s -> 31/24 ms; cable break tick
2.7 s -> 75 ms; restore 2.2 s -> 57 ms; idle median 3.2 ms; teardown 1.1 s (unchanged). Setup max tick still ~5.9 s
with 5.4 s of registry recompute (`setupRecomputeTime`): item 2 (capacity recount / recompute during setup) is next.
`buses-1k-rules` (Bridge layout, 1 run): unchanged (setup 1.17 s, rule 42/27 ms, teardown 7.7 s, bus rate 100%).

### Fix: one domain recompute per tick, counts shared with the judgement (2026-10-25)
Cause: the registry flushes lazily on any read. Router/Switch/cable block entities publish evidence in their own
ticks, and every cable's `syncCapacity`/`syncPower` read the registry in between, so each publish was followed by a
whole-component recompute + `judgeCapacity` count, then `CableCapacity` counted the domain a second time (its cache
was per global evidence revision). ~1900 recomputes during setup.
- `FederationDomainRegistry.pending()` (seeds, capacity nodes/domains, or a changed limit with domains present);
  cables skip both syncs while it holds and retry next tick (their check timers are not advanced).
- `FederationDomainRegistryAccess.settle(server)` at the end of the HIGH Post `onServerTickBindings` flushes once, so
  a cable is never left waiting on a registry nobody reads.
- `judgeCapacity` always counts (limit or not) and keeps `capacityCounts` per domain; `capacityDemand(domain)` returns
  it; `CableCapacity` is now a lookup with no cache of its own (its `clear()` and the call in `closeServer` removed).
`domain-1k-settle` (median of 3): setup max tick 5.9 s -> 330 ms, setup recomputes ~1930 -> 6-11, setup 13.4 -> 1.8 s;
rule edit / cable break / restore unchanged (22-28 ms, 75 ms, 55 ms); teardown 1.0 s. Bridge layout unchanged
(`buses-1k-settle`: setup 1.06 s, rule 38/27 ms, teardown 6.8 s). Cable count display may lag one tick behind a change.

## Final comparison (2026-10-25, a26344fd, `final-2026-10-25`, median of 3)
Baselines: `buses-10k-full` (500b6cf2, median of 3) and `domain-1k-first` (46f0c187, one run). The AE2-only layouts
also came out ~15% faster than in `buses-10k-full` (host load), so compare Federation against them in the same run.
| metric | 1000 Bridges before | after | one domain before | after |
|---|---|---|---|---|
| setup max tick | 151.7 s | 1.03 s | 6.8 s | 0.31 s |
| setup total | 158.8 s | 3.3 s | 16.8 s | 1.7 s |
| all rules, one edit | 310 ms | 153 ms | 2.0 s | 106 ms |
| one rule off / on | 200 / 171 ms | 40 / 27 ms | 2.35 / 2.21 s | 28 / 22 ms |
| median / max tick | 19.5 / 99 ms | 17.3 / 48 ms | 3.7 / 16 ms | 3.2 / 19 ms |
| teardown | 57.1 s | 6.7 s | 1.05 s | 1.08 s |
| cable break / restore tick | - | - | 2.7 / 2.3 s | 70 / 52 ms |
Same run, AE2 at 1000 subnets: direct setup max tick 1.44 s, median 15.2 ms; AE2 subnets (Storage Bus + Quartz
Fiber) 1.45 s, median 16.0 ms, teardown 0.39 s. Federation bus rate 100.1% everywhere. Left: Bridge teardown 6.7 s
(removals reconcile synchronously; deferring them on chunk unload is the open question).

### Full table, every configuration (before = `buses-10k-full` / `domain-1k-first`, after = `final-2026-10-25`)
AE2 rows have no Federation code in their path: their before/after difference is host noise.
| config | run | setup max tick | setup total | median | mean | max | bus rate | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 AE2 one network | before | 1.29 s | 5.3 s | 11.5 ms | 11.7 ms | 29.8 ms | 100.1% | - | - | - | - | - | 640 ms |
| 10x1000 AE2 one network | after | 695 ms | 4.0 s | 9.4 ms | 9.7 ms | 16.5 ms | 99.9% | - | - | - | - | - | 603 ms |
| 10x1000 AE2 subnets | before | 437 ms | 3.2 s | 11.8 ms | 12.8 ms | 41.1 ms | 99.8% | - | - | - | - | - | 301 ms |
| 10x1000 AE2 subnets | after | 317 ms | 2.9 s | 9.1 ms | 9.7 ms | 16.8 ms | 99.9% | - | - | - | - | - | 266 ms |
| 10x1000 Federation Bridge | before | 473 ms | 3.4 s | 11.7 ms | 12.4 ms | 27.9 ms | 99.8% | 65.4 ms | 8.0 ms | 1.9 ms | - | - | 286 ms |
| 10x1000 Federation Bridge | after | 363 ms | 2.9 s | 10.5 ms | 10.1 ms | 21.2 ms | 100.2% | 58.4 ms | 7.7 ms | 2.1 ms | - | - | 286 ms |
| 1000x10 AE2 one network | before | 1.68 s | 7.6 s | 18.9 ms | 18.7 ms | 27.5 ms | 100.1% | - | - | - | - | - | 550 ms |
| 1000x10 AE2 one network | after | 1.44 s | 7.1 s | 15.2 ms | 15.3 ms | 23.5 ms | 100.0% | - | - | - | - | - | 525 ms |
| 1000x10 AE2 subnets | before | 1.56 s | 3.9 s | 19.3 ms | 19.9 ms | 31.5 ms | 99.9% | - | - | - | - | - | 420 ms |
| 1000x10 AE2 subnets | after | 1.45 s | 3.4 s | 16.0 ms | 16.4 ms | 28.4 ms | 99.9% | - | - | - | - | - | 386 ms |
| 1000x10 Federation Bridge | before | 151.70 s | 158.8 s | 19.5 ms | 22.0 ms | 99.4 ms | 100.1% | 310 ms | 200 ms | 171 ms | - | - | 57.09 s |
| 1000x10 Federation Bridge | after | 1.03 s | 3.3 s | 17.3 ms | 18.4 ms | 47.7 ms | 100.1% | 152 ms | 39.7 ms | 27.0 ms | - | - | 6.73 s |
| 1000 networks, one domain | before | 6.79 s | 16.8 s | 3.7 ms | 4.2 ms | 15.7 ms | - | 2.01 s | 2.35 s | 2.21 s | 2.71 s | 2.26 s | 1.05 s |
| 1000 networks, one domain | after | 313 ms | 1.7 s | 3.2 ms | 3.5 ms | 18.7 ms | - | 106 ms | 28.0 ms | 21.7 ms | 70.4 ms | 51.6 ms | 1.08 s |

## Final systems table (2026-10-25, `final-systems-2026-10-25`, median of 3) - the reference result
`perfdomainthousandnetworks` (no buses) was replaced: the one-domain system now runs the same workload as the others
(`DomainFarmScene` builds BusFarm's subnet blocks; a Switch on every riser top, side-by-side risers merge into a 1x1x8
body = one 8-network subdomain; 10x1000 blocks are 11 wide, so one network per Switch; Routers 3x3x1 at pitch 3
with 2-long 3x3 bundles, two above the Switches, a plain cable from each side face over the farm and down; 4 rows per
side). Tests `perfbuses{ten,thousand}subnetsdomain`. All `setupMaxTick` values are now a running max over every setup
tick (`FullTickClock.lastTickNanos`), not the last-100-tick window. Workload per row: main network with 10 000 types;
10x1000 or 1000x10 buses, all running at AE2's fastest rate in every system (99.7-100.2%), so the tick-time column
(median tick / AE2 one network's median tick, same workload) compares cost at equal work. Mean-based: 100/100/106/109%
(10x1000), 100/112/121/198% (1000x10).
| workload | system | setup max tick | setup total | median | mean | max | tick time vs AE2 one network (median) | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 | AE2 one network | 681 ms | 4.4 s | 9.3 ms | 9.7 ms | 15.2 ms | 100% | - | - | - | - | - | 558 ms |
| 10x1000 | AE2 subnets | 595 ms | 3.1 s | 9.6 ms | 9.7 ms | 20.5 ms | 103% | - | - | - | - | - | 258 ms |
| 10x1000 | Federation Bridges | 578 ms | 3.0 s | 10.0 ms | 10.3 ms | 26.7 ms | 108% | 63.7 ms | 7.4 ms | 1.8 ms | - | - | 275 ms |
| 10x1000 | Federation one domain | 555 ms | 2.9 s | 10.2 ms | 10.6 ms | 21.3 ms | 110% | 55.5 ms | 9.3 ms | 1.8 ms | 27.9 ms | 35.3 ms | 279 ms |
| 1000x10 | AE2 one network | 738 ms | 6.7 s | 14.9 ms | 14.9 ms | 19.4 ms | 100% | - | - | - | - | - | 510 ms |
| 1000x10 | AE2 subnets | 1.46 s | 3.6 s | 16.0 ms | 16.7 ms | 26.2 ms | 107% | - | - | - | - | - | 400 ms |
| 1000x10 | Federation Bridges | 1.18 s | 3.4 s | 17.2 ms | 18.0 ms | 46.9 ms | 115% | 154 ms | 38.6 ms | 26.1 ms | - | - | 6.53 s |
| 1000x10 | Federation one domain | 339 ms | 4.0 s | 27.9 ms | 29.5 ms | 61.2 ms | 187% | 161 ms | 36.5 ms | 23.9 ms | 110 ms | 104 ms | 1.87 s |
Open: 1000x10 one domain runs at 27.9 ms median vs 17.2 ms Bridges (profile: mostly AE2 bus/tick-queue time, Federation
block entity ticks + statistics + energy reform ~6-10% of samples; not yet explained). Bridge teardown 6.5 s.
Product limit found: `FederationDomainRecomputeBudget.standard()` = 4096 node visits; a domain larger than that is
dropped whole (`BUDGET_EXHAUSTED`, no networks link, no in-game message). The first layout (Routers 9 above the
Switches) had ~4800 Federation blocks and never formed; the compact one has 3494.
