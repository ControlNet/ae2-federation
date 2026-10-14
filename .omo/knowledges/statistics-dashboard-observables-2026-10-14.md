# Statistics dashboard: what can be measured (2026-10-14)

Source survey for [the statistics idea](../../docs/archive/ideas/statistics-dashboard.md) before the design canvas (row
"统计面板" on the GUI design artifact: options A grid and B focus list, plus one-network, missing-data and no-rules
states, all with sample data). Paths are under `common/src/main/java/space/controlnet/ae2federation/`.

## Exact counts that exist or are one hook away

- Shared storage: `storage/mount/AuthorizedStorageProjection` insert/extract call `observe` on MODULATE with the
  accepted amount. The event `AcceptedStorageOperation(key, amount)` drops the direction (insert vs extract) and
  the `IActionSource`; the mount in `StorageMountService.reconcileEffective` knows the `PolicyKey(consumer,
  provider, STORAGE)`. `LevelObservabilityService.recordPairFlow` keeps a 100-tick `PairFlowWindow` per pair that
  adds items and fluids into one number, so a chart needs a per-key-type split and the direction first.
- Crafting returns: `crafting/projection/CraftingReturnRouter` `Storage.insert` knows executing network, consumer,
  key and amount (`ledger.take` / `ledger.hold`); not metered.
- Pattern pushes across networks: `PatternProjection.pushPattern` → `CraftingProjectionService.owe` knows the pair
  and outputs. AE2 does not say which job pushed, so a push maps to a set of busy consumer CPUs: chart counts, not
  per-job durations. Job ends come from the ledger sweep (`JobEnd` DONE / CANCELLED / TERMINATED), no timestamps.
  `CraftingService.submitJob` is not hooked.
- Processing lanes (Provider → Endpoint): already metered per lane and key type (`ProviderObservationRegistry`).

## Sampled values (AE2 API, per grid)

`NetworkOverview.addGridFacts` already sends each second: stored/max energy, the shared pool, `getAvgPowerInjection`
/ `getAvgPowerUsage` (40-tick exponential averages), cached-inventory type count, CPUs / busy CPUs, used channels.
In a shared ME power pool `getAvgPowerUsage` is what was drained from this grid's own cells, not what it used.
A consumer's cached inventory includes mounted Federation projections, so summing networks double counts
(`FederationManagedStorage` marks ours).

## Cost checked in AE2 19.2.17 sources

- `IStorageService.getCachedInventory()` is not a free read: `StorageService.onServerEndTick` marks the cache stale
  every tick while no watcher is interested, and the getter then re-enumerates all storage, Federation mounts (other
  networks' cells) included. Fine once a second for an open screen, wrong for an always-on server-wide sampler, so
  "types" is not kept as history (dropped from option B on 2026-10-14). `KeyCounter.size()` is O(distinct items).
- `IEnergyService.getStoredPower` refreshes its sum only after 90 ticks (`availableTicksSinceUpdate`); `getMaxStoredPower`,
  `getAvgPowerUsage/Injection`, `IPathingService.getUsedChannels` are field reads; `ICraftingService.getCpus` builds a
  small ImmutableSet. `AEItemKey` caches its hash, so a per-key `HashMap` add per storage operation is cheap.
- Plan: per-mount accumulators on the storage hot path drained once a second; gauges every 20 ticks, networks spread
  over ticks; pool walk once per pool; ranking keeps the top 32 keys per bucket (approximate beyond that); sync only
  while the page is open, only the selected range.

## Not available today

- ME power moved between networks: no event; AE2 `EnergyService.extractAEPower` / `injectPower` loop over
  `getConnectedServices()`, so an exact count needs a mixin there (`service != this`).
- Any history: every window is 100 ticks and in memory; nothing is persisted.
- Per-segment cable N/C in the domain screen (only over-capacity booleans are synced).
- A chart widget: LDLib2 2.2.34 has none; polylines go through `DrawerHelper.drawTexLines` inside a custom
  `UIElement.drawBackgroundAdditional` (as `FederationTopologyView.Links` does), rectangles through
  `FederationTheme.Painter`.

## Where a tab goes

`ui/domain.xml` rail (`tab_overview`, `tab_mapping`) and `client/menu/FederationWorkspace.PAGES`; a third page needs
a `tab_x` / `page_x` pair and a `PAGES` entry. Screen data rides LDLib2 `stringS2C` JSON bindings in
`FederationDomainPolicyMenuHolder` (overview every 20 ticks, flows every 10).

## Decided and built (phase 1, server side)

- Code: `observability/statistics/` (`StatisticsRange`, `Timeline`, `TopKeys`, `StatisticsBook`, `StatisticsNames`,
  `FederationStatistics`) and `persistence/StatisticsSavedData` (overworld SavedData `ae2federation_statistics`).
  `LevelObservabilityService.sweep()` ticks it.
- Four ranges, one tier each: 2 min @1 s, 10 min @5 s, 1 h @1 min, 24 h @10 min (144 points). Wall-clock seconds,
  so a stopped or paused server shows as a gap. The book also records which seconds were observed. A counter's point
  is its sum divided by the observed seconds in it. A point with no observed second is `NaN` (drawn as a gap).
- History is saved with the world, 24 h of it. Doubles are stored as raw long bits. Ranking keys go into one AEKey
  table (`toTagGeneric`), and a key that no longer loads is dropped. Rankings keep their top 16 keys per point.
- Hooks:
  - storage mounts meter both directions in `StorageMountService.reconcileEffective`;
  - pattern pushes count their inputs before the push (`PushListener.prepare(..., KeyCounter inputs)`);
  - `CraftingReturnRouter` and `sweepReturns` meter what comes back;
  - finished and cancelled jobs are counted from `countedJobs` after `sweepReturns`, because the ledger forgets the
    jobs of a settled consumer.
  - Meters are never removed, so a mount may keep a reference to its meter.
- Domain stock: each network's own native sources (`NativeSourceDomainRegistry.discover`, own registry instance),
  read every 10 s per network and spread across seconds by the hash of the network id. The reading is re-recorded
  each second and dropped after 30 s. Never `getCachedInventory`.
- No cross-network ME power metering in v1: that would need a mixin on `EnergyService` extract/inject.

## Measured cost (perfStorageProjection, 1000 bulk item types, 2 networks)

- `statisticsSecond` ≈ 33 µs per wall-clock second on average (≈ 1.7 µs per tick).
- `statisticsSecondReadingStock` ≈ 0.27 ms for a second that reads the 1000-type stock. For comparison,
  `availableStacks` on 1000 types is ≈ 0.12 ms.
- The stock read scales with the number of distinct types. At about 0.27 µs per type, a 20k-type network costs about
  5 ms once every 10 s. If that ever matters, split the read across ticks or lengthen the period.

## Pitfall: GameTest time

A GameTest server runs ticks faster than the wall clock: 1200 ticks took about 1.4 s. A test cannot wait for
`tick()` to finish seconds. Instead it calls `finishSecond(++second)` itself on every retry, and reads the 1 h range
so that all of its seconds fall in one window.

## Phase 2: the page (2026-10-14)

- Code:
  - `client/menu/FederationStatisticsView` draws the page.
  - `observability/statistics/StatisticsPayload` sends the data as one NBT tag per refresh.
  - `FederationDomainPolicySession.statistics(range)` builds it from the domain's members and enabled rules.
  - `FederationDomainPolicyMenuHolder` carries it on a `tagS2C` binding. It sends an empty tag until the page asks.
- Requests go client to server through `DataBindingBuilder.string(getter, setter)`, not `stringC2S`. The latter
  logged "IllegalAccessException: unknown receive sync data while it does not accept sync" in `readInitialData`.
- The page sends `finishedSecond()`, the newest second written into the book, not `nowSecond() - 1`. That second may
  not be finished yet when the menu syncs, and the newest column then showed as a gap.
- The overworld's `LevelObservabilityService` is now created on the first server tick (`NeoForgeEntrypoint`).
  Before, it was created on first use, so seconds before the first storage mount or screen were missing.
- Chart hover uses `isMouseOver(x, y)`. `isHover()` is true only for `ModularUI.getLastHoveredElement()`, and the
  crosshair never appeared under the UI test's hover.
- Payload size and build time are measured in `StatisticsPayloadTest.aBusyEightNetworkDomainStaysSmall`. For 8
  networks with all 56 pairs active:
  - with UUID strings in every row it was 192 KB;
  - with network indices and a shared key table it is 142 KB raw, 51 KB compressed;
  - building it takes about 6.5 ms after warm-up, 3.7 ms of that the rankings (each pair merges 120 points).
  - If that ever matters, keep running totals per range in `TopKeys` rather than merging on read.
- UI scenario pitfalls:
  - `ScenarioOptions.scenarioTimeoutMs` is the whole scenario's budget. `ui.mapping` needs 300 s because of the 75 s
    of showcase traffic. `.timeoutMs` on a step does not extend it.
  - The showcase domain is over the shipped C=8 cable limit, so the fixture sets the limit to 32 while it runs.
