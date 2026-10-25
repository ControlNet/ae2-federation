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

## Systems table before lazy Switch face nodes (2026-10-25, `final-systems-2026-10-25`, median of 3) - superseded below
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

## Lazy Switch face nodes (06074081)
Cause of the 187%: every Switch face created its AE2 boundary node up front, and a node is a Grid of its own until it
connects. AE2's `TickHandler` ticks every Grid each level tick (about 2 ms per 1000 idle Grids), so the 1000x10 domain
had 6006 Grids (1000 Switches x 5 empty faces + 1001 networks) against 1001 for Bridges. `RouterFacePort` now creates
the node only while an AE2 node host (`AECapabilities.IN_WORLD_GRID_NODE_HOST`) is beside the face and no Federation
port faces it (Routers/Switches offer a port on every face; Provider/Endpoint only on their front, where their AE2 node
is not exposed), and destroys it when that ends. A `BlockCapabilityCache` on the neighbour's node host covers chunk
loads/unloads; block updates still revalidate at once. Saved node data is kept until the node is created.
NeoForge gotcha: a `BlockCapabilityCache` calls its listener only once per invalidation until `getCapability()` is
read again, and an unloaded position reads as null without loading it, so the sync reads both caches every time.
Tests: `switchfacenodefollowsneighbour`, `switchfacenodesurviveschunkreload` (Switch and cable on two sides of a
nether chunk border, real unload/load plus a one-tick block entity reload). New benchmark metric `ae2Grids`.

## Systems table after lazy face nodes (2026-10-25, 06074081, `lazy-face-nodes-2026-10-25`, median of 3) - superseded below
Same workloads and scenes as above; every bus ran at AE2's fastest rate (99.6-100.1%). The 10x1000 AE2 one network
median is noisy (runs 9.7/6.6/7.0 ms), so for 10x1000 read the mean column; 1000x10 runs agree within ~1 ms.
| workload | system | AE2 Grids | setup max tick | setup total | median | mean | max | median vs AE2 one network | mean vs AE2 one network | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 | AE2 one network | 1 | 1.46 s | 4.7 s | 7.0 ms | 9.2 ms | 17.9 ms | 100% | 100% | - | - | - | - | - | 532 ms |
| 10x1000 | AE2 subnets | 11 | 606 ms | 3.0 s | 8.7 ms | 9.5 ms | 16.1 ms | 124% | 103% | - | - | - | - | - | 309 ms |
| 10x1000 | Federation Bridges | 11 | 593 ms | 3.1 s | 10.0 ms | 10.4 ms | 21.8 ms | 143% | 113% | 59.7 ms | 7.5 ms | 1.6 ms | - | - | 295 ms |
| 10x1000 | Federation one domain | 11 | 631 ms | 2.9 s | 9.9 ms | 10.5 ms | 22.9 ms | 141% | 114% | 58.0 ms | 9.7 ms | 1.8 ms | 28.6 ms | 33.0 ms | 278 ms |
| 1000x10 | AE2 one network | 1 | 672 ms | 6.7 s | 14.7 ms | 14.8 ms | 16.9 ms | 100% | 100% | - | - | - | - | - | 522 ms |
| 1000x10 | AE2 subnets | 1001 | 1.45 s | 3.4 s | 16.8 ms | 17.5 ms | 34.4 ms | 114% | 118% | - | - | - | - | - | 393 ms |
| 1000x10 | Federation Bridges | 1001 | 1.17 s | 3.4 s | 18.7 ms | 19.1 ms | 48.0 ms | 127% | 129% | 168 ms | 38.5 ms | 26.5 ms | - | - | 6.77 s |
| 1000x10 | Federation one domain | 1001 | 341 ms | 3.0 s | 16.8 ms | 18.2 ms | 42.0 ms | 114% | 123% | 139 ms | 35.2 ms | 22.9 ms | 91.2 ms | 80.6 ms | 1.67 s |
1000x10 one domain is now level with AE2 subnets on the median (16.8 ms both) and 4% above them on the mean; it was
27.9 ms (187%) with 6006 Grids.

## No domain size limit (3bd89462)
The 4096-node / 24576-port recompute budget is gone (with `BUDGET_EXHAUSTED`): AE2 has no node limit on a Grid either
and re-runs its channel pathing over the whole Grid once on the next tick after a change; the registry recomputes at
most once per tick. New benchmark `perfbusesthousandsubnetslargedomain`: the 1000x10 workload with the Routers lifted
52 blocks (`DomainFarmScene` `lift`), 10 046 Federation blocks.

## Systems table with ticking cables (2026-10-25, 3bd89462, `unlimited-domain-2026-10-25`, median of 3) - superseded below
Every bus ran at AE2's fastest rate (99.8-100.2%). Runs agree within ~1 ms except where noted in the samples.
| workload | system | Federation blocks | AE2 Grids | setup max tick | setup total | median | mean | max | median vs AE2 one network | mean vs AE2 one network | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 | AE2 one network | - | 1 | 568 ms | 4.0 s | 9.2 ms | 9.4 ms | 12.1 ms | 100% | 100% | - | - | - | - | - | 572 ms |
| 10x1000 | AE2 subnets | - | 11 | 639 ms | 2.9 s | 9.9 ms | 9.9 ms | 19.2 ms | 108% | 105% | - | - | - | - | - | 267 ms |
| 10x1000 | Federation Bridges | - | 11 | 586 ms | 3.0 s | 10.5 ms | 10.5 ms | 34.2 ms | 114% | 112% | 63.8 ms | 8.1 ms | 2.0 ms | - | - | 298 ms |
| 10x1000 | Federation one domain | 320 | 11 | 575 ms | 2.9 s | 9.6 ms | 10.1 ms | 20.5 ms | 104% | 107% | 37.0 ms | 8.9 ms | 1.7 ms | 25.1 ms | 35.7 ms | 269 ms |
| 1000x10 | AE2 one network | - | 1 | 637 ms | 6.8 s | 13.7 ms | 14.5 ms | 21.3 ms | 100% | 100% | - | - | - | - | - | 515 ms |
| 1000x10 | AE2 subnets | - | 1001 | 1.51 s | 3.5 s | 15.7 ms | 16.0 ms | 21.4 ms | 115% | 110% | - | - | - | - | - | 388 ms |
| 1000x10 | Federation Bridges | - | 1001 | 1.09 s | 3.4 s | 17.6 ms | 18.3 ms | 46.7 ms | 128% | 126% | 177 ms | 39.3 ms | 26.8 ms | - | - | 6.72 s |
| 1000x10 | Federation one domain | 3494 | 1001 | 402 ms | 2.9 s | 16.4 ms | 17.3 ms | 43.6 ms | 120% | 119% | 149 ms | 37.3 ms | 23.0 ms | 97.7 ms | 93.7 ms | 1.71 s |
| 1000x10 | Federation one domain, 10k blocks | 10046 | 1001 | 335 ms | 3.7 s | 22.8 ms | 24.2 ms | 50.4 ms | 166% | 167% | 143 ms | 36.4 ms | 24.1 ms | 133 ms | 136 ms | 2.88 s |
The 10k-block domain forms (it was dropped whole before) and a cable break/restore costs ~35 ms more than at 3494
blocks. Its steady tick is ~6.4 ms higher: JFR (`prof/large.jfr`) puts 23% of server samples in
`FederationCableBlockEntity.serverTick` (a stream over six ports, `syncCapacity`'s bundle lookup, `syncPower`) and ~9%
in vanilla's per-block-entity `shouldTickBlocksAt`, i.e. the cost of 10 000 ticking cable block entities. Counting
showed ports are NOT re-dirtied in the steady state (resolves after setup are a few dozen per tick); JFR's attribution
of `Level.isLoaded` to `CableFacePort.resolve` was an inlining artefact. AE2 cable buses do not tick at all.

## Systems table with ticking Switches (2026-10-25, 6d810eef, `safety-nets-2026-10-25`, median of 3) - superseded below
Tickless Federation Cables (9938fdcd) plus the missed-event safety nets (6d810eef: Switch face re-check on
`getGridNode` and a sweep every 100 ticks per Switch). Every bus ran at AE2's fastest rate (99.9-100.1%). This run
was noisier than the previous one: AE2 one network itself read 15.1 ms on 1000x10 (13.7 before).
| workload | system | Federation blocks | AE2 Grids | setup max tick | setup total | median | mean | max | median vs AE2 one network | mean vs AE2 one network | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 | AE2 one network | - | 1 | 584 ms | 4.2 s | 9.6 ms | 10.1 ms | 14.3 ms | 100% | 100% | - | - | - | - | - | 628 ms |
| 10x1000 | AE2 subnets | - | 11 | 563 ms | 2.8 s | 9.7 ms | 9.9 ms | 15.2 ms | 101% | 98% | - | - | - | - | - | 277 ms |
| 10x1000 | Federation Bridges | - | 11 | 606 ms | 3.1 s | 10.7 ms | 11.2 ms | 22.9 ms | 111% | 111% | 61.8 ms | 8.1 ms | 1.8 ms | - | - | 290 ms |
| 10x1000 | Federation one domain | 320 | 11 | 583 ms | 3.0 s | 10.5 ms | 11.4 ms | 24.6 ms | 109% | 113% | 55.8 ms | 9.1 ms | 1.6 ms | 30.1 ms | 27.2 ms | 270 ms |
| 1000x10 | AE2 one network | - | 1 | 566 ms | 6.7 s | 15.1 ms | 15.5 ms | 21.4 ms | 100% | 100% | - | - | - | - | - | 546 ms |
| 1000x10 | AE2 subnets | - | 1001 | 1.49 s | 3.6 s | 16.4 ms | 16.6 ms | 24.4 ms | 109% | 107% | - | - | - | - | - | 413 ms |
| 1000x10 | Federation Bridges | - | 1001 | 1.07 s | 3.6 s | 18.0 ms | 18.7 ms | 45.3 ms | 119% | 121% | 160 ms | 40.3 ms | 27.4 ms | - | - | 6.84 s |
| 1000x10 | Federation one domain | 3494 | 1001 | 482 ms | 3.1 s | 17.6 ms | 19.7 ms | 73.6 ms | 117% | 127% | 193 ms | 37.6 ms | 23.5 ms | 101 ms | 88.0 ms | 1.74 s |
| 1000x10 | Federation one domain, 10k blocks | 10046 | 1001 | 384 ms | 3.6 s | 17.8 ms | 19.6 ms | 57.0 ms | 118% | 126% | 149 ms | 37.3 ms | 23.5 ms | 135 ms | 124 ms | 2.92 s |

The 10k-block domain now costs what the 3494-block one does (166% -> 118%): its steady tick no longer grows with the
number of cables. Sweep A/B on 1000x10 one domain (`sweep-on-2026-10-25` vs `sweep-off-2026-10-25`, sweep disabled
locally and not committed): median/mean/max 16.2/17.3/41.7 ms on, 16.5/18.0/55.0 ms off, i.e. no measurable cost;
the higher max ticks in the table above are machine noise. Still open: Bridge teardown 6.8 s.

Mekanism Cardboard Box compat tests (`cardboardboxbesideswitch`, `cardboardboxfederationcable`, group
`mekanism`): boxing is a plain right-click with the box (sneaking passes the click on and places the box item),
unboxing is a sneak right-click with an empty hand; boxing sets the box with flags 35 (neighbour updates), unboxing uses
`setBlockAndUpdate` then `loadWithComponents`. Use NeoForge's `FakePlayerFactory`: GameTestHelper's mock server
player is in the player list, so Mekanism's login payloads (`mekanism:player_data`) throw on its mock connection.
Run: `python3 tools/compat_run.py --accept-eula --tests cardboardboxbesideswitch,cardboardboxfederationcable mekanism`.

## Systems table with per-tick statistics work (2026-10-25, 35a49369, `tickless-routers-full-2026-10-25`, median of 3) - superseded below
Profile of 6d810eef (1000x10 one domain, steady window, ~650 server samples) put Federation's own overhead at:
Switch/Router ticker 5.5% (a stream over six ports + the verify sweep), statistics 4.5% (`pollJobs` walked every
member Grid's CPUs each tick, plus the once-a-second drain/sample), per-operation metering
(`AuthorizedStorageProjection.observe`) 3.8%, per-operation authorization (`permits`, already O(1): EnumMap get
behind the AuthorityEpoch fast path, cost is cold pointer chasing over 1000 mounts) 3.4%, `owedWithin` 1.2%.
Fixed: a0b069f6 (statistics poll only member networks with a CPU, AE2 `GridCraftingCpuChange` adds a network at
once) and 35a49369 (Routers/Switches tickless on `RouterTicks`). Metering and authorization untouched (owner: later).
Every bus ran at AE2's fastest rate (99.5-100.2%).
| workload | system | Federation blocks | AE2 Grids | setup max tick | setup total | median | mean | max | median vs AE2 one network | mean vs AE2 one network | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 | AE2 one network | - | 1 | 1.39 s | 4.5 s | 7.8 ms | 9.9 ms | 26.7 ms | 100% | 100% | - | - | - | - | - | 541 ms |
| 10x1000 | AE2 subnets | - | 11 | 578 ms | 3.0 s | 8.6 ms | 9.9 ms | 14.4 ms | 110% | 100% | - | - | - | - | - | 290 ms |
| 10x1000 | Federation Bridges | - | 11 | 554 ms | 2.8 s | 10.2 ms | 10.6 ms | 26.0 ms | 131% | 107% | 55.1 ms | 8.9 ms | 2.1 ms | - | - | 285 ms |
| 10x1000 | Federation one domain | 320 | 11 | 573 ms | 2.8 s | 9.8 ms | 10.2 ms | 24.8 ms | 126% | 103% | 56.9 ms | 11.3 ms | 1.6 ms | 27.3 ms | 25.4 ms | 264 ms |
| 1000x10 | AE2 one network | - | 1 | 690 ms | 6.5 s | 14.2 ms | 15.2 ms | 22.0 ms | 100% | 100% | - | - | - | - | - | 531 ms |
| 1000x10 | AE2 subnets | - | 1001 | 1.44 s | 3.6 s | 15.4 ms | 16.2 ms | 27.1 ms | 108% | 107% | - | - | - | - | - | 366 ms |
| 1000x10 | Federation Bridges | - | 1001 | 1.17 s | 3.5 s | 16.5 ms | 18.3 ms | 47.7 ms | 116% | 120% | 152 ms | 38.5 ms | 26.0 ms | - | - | 6.60 s |
| 1000x10 | Federation one domain | 3494 | 1001 | 335 ms | 3.0 s | 14.0 ms | 14.9 ms | 55.7 ms | 99% | 98% | 148 ms | 37.3 ms | 23.5 ms | 97.1 ms | 82.4 ms | 1.71 s |
| 1000x10 | Federation one domain, 10k blocks | 10046 | 1001 | 418 ms | 3.5 s | 15.0 ms | 15.9 ms | 41.4 ms | 106% | 105% | 193 ms | 35.7 ms | 23.2 ms | 140 ms | 160 ms | 2.90 s |

The 10x1000 medians were noisy this run (AE2 one network 6.6/7.8/9.7 ms over the three runs); its means (9.9, 9.9,
10.6, 10.2 ms) are the steadier comparison there. On 1000x10 the one domain now runs at AE2 one network's tick
(99% median, 98% mean), below AE2 subnets (108%/107%); the 10k-block domain at 106%/105%. Max ticks stay higher
(41-56 ms vs 22-27): not yet profiled (suspect the once-a-second statistics sample). Still open: Bridge teardown 6.6 s.

## Max ticks and the 10x1000 gap (2026-10-25)
Timers around each Federation step of the server tick (temporary, not committed) put every Federation max tick above
AE2's on the once-a-second statistics write: 23-61 ms in one tick at 1000x10 (drain of the route meters 10-33 ms,
gauges of 1000 networks 8-36 ms, `liveClaims()` copying every Grid's lineages ~5 ms, the 10k-type main network's
stock reading up to 16 ms on its turn). GC pauses (5-10 ms, ~12 ms for a young + mixed pair, every ~1.5 s) and ticks
slow only in the level phase were the same in AE2 one network, and account for its own 22-27 ms max. At 10x1000 a
JFR profile (excluding the TEST-ONLY `BulkItemStorage` both systems pay) put Federation at ~10% of server time:
per-operation metering 5.7% (mostly `PairMeter.add`, a `HashMap.computeIfAbsent` with a `long[]` per key), `permits`
1.4%, the CPU receipt hook's `owedWithin` 1.4% (a synchronized lookup on every insert into a Grid's CPUs), statistics
and binding ticks 2.7%.

Fixes (owner approved M1-M4, G1, G2):
- The finished second is written over the next 10 ticks (`FederationStatistics.PASS_TICKS`): meters double-buffer
  (`moved`/`finished`), so what moves meanwhile counts for the next second; the second reads as finished only once
  written. Series names are made once per network and route. `TopKeys.addAll` writes a meter's map with one point
  lookup per range and reuses a slot's map when it moves on (same results as per-key `add` in the same order).
  `NetworkIdentityRegistry.liveGrids()` lists Grids without copying lineages.
- `PairMeter` uses fastutil `Object2LongOpenHashMap.addTo`. Its iteration order differs from `HashMap`'s, so when a
  ranking point trims (over 2x its 16-key cap) the kept keys can differ from before; the ranking was already
  approximate there.
- `CraftingReturnLedger` counts the ledgers owing anything; `owedWithin` returns 0 without the lock or lookups while
  none does.
After: statistics cost 0.22-0.35 ms per tick on average at 1000x10 (was ~1.15 ms, all in one tick per second);
metering 3.7% of server time at 10x1000; `owedWithin` no longer in the profile.

## Systems table after statistics metering (2026-10-25, `statistics-metering-2026-10-25`, median of 3) - superseded below
Another project's compat server ran on the machine during part of this run, so the max ticks of single runs are
noisy (listed below the table).

| workload | system | Federation blocks | AE2 Grids | setup max tick | setup total | median | mean | max | median vs AE2 one network | mean vs AE2 one network | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 | AE2 one network | - | 1 | 736 ms | 4.3 s | 9.0 ms | 9.5 ms | 13.0 ms | 100% | 100% | - | - | - | - | - | 593 ms |
| 10x1000 | AE2 subnets | - | 11 | 599 ms | 2.9 s | 9.4 ms | 9.6 ms | 16.3 ms | 104% | 101% | - | - | - | - | - | 268 ms |
| 10x1000 | Federation Bridges | - | 11 | 589 ms | 3.1 s | 10.6 ms | 10.3 ms | 16.2 ms | 118% | 108% | 58.3 ms | 7.5 ms | 1.6 ms | - | - | 312 ms |
| 10x1000 | Federation one domain | 320 | 11 | 563 ms | 3.0 s | 9.1 ms | 9.6 ms | 20.2 ms | 101% | 101% | 59.4 ms | 8.4 ms | 1.5 ms | 27.1 ms | 26.3 ms | 270 ms |
| 1000x10 | AE2 one network | - | 1 | 812 ms | 7.1 s | 13.7 ms | 14.8 ms | 24.8 ms | 100% | 100% | - | - | - | - | - | 510 ms |
| 1000x10 | AE2 subnets | - | 1001 | 1.45 s | 3.4 s | 15.8 ms | 16.2 ms | 32.0 ms | 115% | 109% | - | - | - | - | - | 374 ms |
| 1000x10 | Federation Bridges | - | 1001 | 1.07 s | 3.3 s | 18.1 ms | 18.6 ms | 39.6 ms | 132% | 126% | 154 ms | 44.3 ms | 30.7 ms | - | - | 6.64 s |
| 1000x10 | Federation one domain | 3494 | 1001 | 278 ms | 3.0 s | 14.8 ms | 15.1 ms | 24.5 ms | 108% | 102% | 128 ms | 38 ms | 24.7 ms | 93.7 ms | 93.7 ms | 1.64 s |
| 1000x10 | Federation one domain, 10k blocks | 10046 | 1001 | 387 ms | 3.4 s | 13.4 ms | 13.9 ms | 38.8 ms | 98% | 94% | 142 ms | 34.2 ms | 22.6 ms | 131 ms | 117 ms | 2.80 s |

Max tick per run (ms):
- 10x1000 AE2 one network: 13.0/15.7/11.3
- 10x1000 AE2 subnets: 16.3/12.9/18.6
- 10x1000 Federation Bridges: 24.3/16.2/16.0
- 10x1000 Federation one domain: 19.4/23.2/20.2
- 1000x10 AE2 one network: 15.9/24.8/33.9
- 1000x10 AE2 subnets: 21.4/44.5/32.0
- 1000x10 Federation Bridges: 48.9/23.4/39.6
- 1000x10 Federation one domain: 20.6/25.1/24.5
- 1000x10 Federation one domain, 10k blocks: 47.1/19.0/38.8

At 10x1000 the one domain now matches AE2 one network (101%/101%); at 1000x10 the one domain is at 108%/102% (its
three runs 14.8/13.7/14.9 ms against AE2's 13.7/14.7/13.3) and the 10k-block domain at 98%/94%. The one domain's max
tick matches AE2's at 1000x10 (24.5 vs 24.8 ms). At 10x1000 it stays higher (19-23 ms vs 11-16 ms); the likely
cause, not yet confirmed there, is the statistics reading of the 10k-type main network's stock: one network in one
tick once every 10 seconds, 13-16 ms when timed at 1000x10, not split by the pass. Still open: Bridge teardown 6.6 s.


## Stock reading, Bridge teardown and permits (2026-10-25)
- The 10x1000 domain's higher max tick was the statistics stock reading of the 10k-type main network (one network,
  one tick, every 10 s). Timed at 1000x10 before the change: discover 0.9, enumerate 3.6, removeZeros 3.3, read 8.3 ms.
  `read()` now sums by `AEKeyType` (an identity map, the last type cached across consecutive keys), names each type
  once at the end, and skips zero amounts instead of a `removeZeros()` pass; `stock(network)` (the dashboard's own
  view) still removes zeros. Read went to 3.9 ms and removeZeros to 0; splitting the read across ticks (A2) was not
  needed.
- Bridge teardown (6.6 s at 1000x10) was one full binding reconcile per removed Bridge: a linked Bridge that stopped
  linking flushed `FederationBindingRefresh` at once. It now only requests, as cables and Routers do, so 1000 Bridges
  reconcile once at the tick's end. Until then a held storage projection fails closed, even on a redundant route
  (`storagepriority` flushes explicitly after removing the first Bridge).
- `EffectiveStorageAuthority.permitsAll` is a bit test of a mask built in the constructor (~1.4% of samples before).
- The 1000x10 max ticks of every system, AE2 subnets included, come from vanilla chunk saving
  (`ChunkMap.processUnloads` saves up to 20 accessible, unsaved chunk holders per tick with spare time, 10 s cooldown
  per chunk; the chests dirty their chunks). Timed: 20 chunks with 4319-4718 block entities = 26-47 ms in the level
  tick before its Post event; AE2 subnets once 118 ms (15256 block entities). Whether a save batch falls in the 100-tick
  window decides the max, so single-run maxima at 1000x10 are noise; the same 32-62 ms range shows at 0af051b6.

## Final systems table (2026-10-25, `stock-teardown-permits-final-2026-10-25`, median of 3) - the reference result

| workload | system | Federation blocks | AE2 Grids | setup max tick | setup total | median | mean | max | median vs AE2 one network | mean vs AE2 one network | all rules | rule off | rule on | break tick | restore tick | teardown |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x1000 | AE2 one network | - | 1 | 1.46 s | 4.8 s | 9.6 ms | 9.9 ms | 21.7 ms | 100% | 100% | - | - | - | - | - | 602 ms |
| 10x1000 | AE2 subnets | - | 11 | 558 ms | 2.8 s | 9.5 ms | 9.5 ms | 11.6 ms | 99% | 96% | - | - | - | - | - | 281 ms |
| 10x1000 | Federation Bridges | - | 11 | 600 ms | 3.1 s | 9.6 ms | 9.2 ms | 17.6 ms | 100% | 93% | 57.0 ms | 7.5 ms | 1.6 ms | - | - | 272 ms |
| 10x1000 | Federation one domain | 320 | 11 | 567 ms | 3.0 s | 9.2 ms | 9.6 ms | 18.1 ms | 96% | 97% | 54.8 ms | 9.3 ms | 1.6 ms | 25.3 ms | 23.5 ms | 252 ms |
| 1000x10 | AE2 one network | - | 1 | 741 ms | 6.7 s | 14.9 ms | 15.1 ms | 20.8 ms | 100% | 100% | - | - | - | - | - | 520 ms |
| 1000x10 | AE2 subnets | - | 1001 | 1.39 s | 3.4 s | 15.7 ms | 16.3 ms | 28.9 ms | 105% | 108% | - | - | - | - | - | 372 ms |
| 1000x10 | Federation Bridges | - | 1001 | 1.24 s | 3.5 s | 16.1 ms | 16.4 ms | 23.4 ms | 108% | 109% | 148 ms | 37.8 ms | 26.1 ms | - | - | 656 ms |
| 1000x10 | Federation one domain | 3494 | 1001 | 336 ms | 2.8 s | 13.8 ms | 14.3 ms | 38.7 ms | 93% | 95% | 166 ms | 36.9 ms | 23.3 ms | 91.7 ms | 82.5 ms | 1.67 s |
| 1000x10 | Federation one domain, 10k blocks | 10046 | 1001 | 336 ms | 3.4 s | 14.2 ms | 14.8 ms | 39.1 ms | 95% | 98% | 174 ms | 35.2 ms | 22.9 ms | 125 ms | 142 ms | 2.92 s |

Max tick per run (ms):
- 10x1000 AE2 one network: 21.7/11.2/125.8
- 10x1000 AE2 subnets: 11.6/17.9/10.5
- 10x1000 Federation Bridges: 20.0/13.4/17.6
- 10x1000 Federation one domain: 18.1/12.4/18.6
- 1000x10 AE2 one network: 20.8/22.6/18.2
- 1000x10 AE2 subnets: 29.2/25.1/28.9
- 1000x10 Federation Bridges: 23.4/20.7/24.3
- 1000x10 Federation one domain: 38.7/30.8/61.7
- 1000x10 Federation one domain, 10k blocks: 20.8/39.1/53.2

At 10x1000 the one domain's max ticks (12-19 ms) are now within AE2's range. Bridge teardown at 1000x10 is 656 ms
(was 6.64 s). Medians: every Federation system is within 0.5 ms of AE2 subnets or below; at 1000x10 the one domain
runs below AE2 one network (93%/95%). Remaining 1000x10 max spikes are chunk saving (above); a benchmark without saving
during the window (e.g. `server.setAutoSave(false)`/`level.noSave`) would show Federation's own max, not yet adopted.

## Why AE2 one network differs between 10x1000 and 1000x10 (2026-10-25)
The two workloads share bus, chest and operation counts but not geometry, so their absolute ticks are not comparable;
the owner accepted them as two scenes (no layout change). Each 1000x10 subnet block is one bus column plus one all-cable
riser that joins the column's cable layers (it cannot be dropped), laid out as a checkerboard: every chest has AE2
cables on its four horizontal sides; in 10x1000 a chest's neighbours are mostly chests. Every bus operation changes a
chest, and `BlockEntity.setChanged` -> `Level.updateNeighbourForOutputSignal` visits the four horizontal neighbours.
For a cable bus that costs `CableBusBlock.onNeighborChange` -> `cb()` -> `getBlockEntity` (0.12 -> 1.49 ms/tick) and the
redstone-conductor test `isCollisionShapeFullBlock` -> `Block.isShapeFullBlock`'s Guava cache on the dynamic shape
(0.96 -> 2.57 ms/tick). Level-tick timing: vanilla level work ~0.2 ms in both; the gap sits in the level-end phase.
Together ~3 of the ~5.5 ms gap (9.6 vs 14.9 ms); the rest was not attributed. The bus work itself
(`TickManagerService`) is not larger at 1000x10.

Profiling note: `jfr print` cuts stacks to 5 frames unless given `--stack-depth 64`; earlier caller analyses that saw
"truncated" stacks were this. A copy of the JDK's `profile.jfc` with the `jdk.ExecutionSample` period at 1 ms, attached
with `jcmd <pid> JFR.start settings=<file>`, gives ~700 server-thread samples per second.
