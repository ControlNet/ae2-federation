# Performance fixes and native AE2 comparison (2026-09-30 .. 2026-10-01)

Follows `performance-audit-2026-09-30.md`. Fix commits on `dev`: 3c0f1a6 (energy demand), 312e741 (observation
sweep), 0465544 (domain recompute and binding reconcile once per tick), 0e0b9db (storage VIEW-all, overflow retry),
fb92e21 (Domain menu throttles), 843bfba (client map preview and lookups), 948d7ce (per-operation validation
allocations, native baselines), then the node-index round (Grid-level activity, AE2 machine index, node revision).

## Benchmark method
- `tools/perf_benchmark.py`: `--tests`, `--repeat`, `--label`, `--compare <label>` (current first, baseline second),
  `--tree <worktree>`, `--remote <user@host> --key <identity>`, `--remote-root`.
  Results in `.omo/evidence/perf/<label>.json` with per-run `samples`.
- Remote runs must be sequential. Give each worktree its own `--remote-root` (copy `~/ae2f/perf/.gradle` into it):
  rsync never deletes, so switching an older tree into a root that held a newer one leaves stale sources.
- Compare versions by interleaving them (A, B, A, B ...), not by running A three times then B three times: the remote
  host drifts by ~10% over an hour, which once looked like a fix-2 regression of `closedTick`.
- Old versions: `git worktree add --detach`, then check out only `common/src/main` of the older commit, so every
  version runs the same benchmark harness.
- `perfnativeenergy` / `perfnativestorage` are the native AE2 baselines (Quartz Fiber star; Storage Bus on an ME
  Interface) with the same scene sizes and metric names as `perfenergymesh` / `perfstorageprojection`.
  `networkExtractInsert`, `networkAvailableStacks` and `consumerSimulateExtract` go through the consumer Grid's own
  `NetworkStorage` in both, so they compare directly.
- `-P federationPerfOpsScale=N` multiplies op counts and budgets (profiling); `-P federationPerfPadding=2000` makes the
  O(Grid nodes) paths visible.
- Open-menu window needs a warm-up: without it the open-only code (domain projection) is measured before the JIT
  compiles it. That was the whole "fix 2 openTick +12%" (fix 1 had compiled the projector during the closed phase,
  because every `recordAccepted` projected). With a 100-tick warm-up fix 1 1317/1384 us vs fix 2 1271/1350 us.
- Idle windows start `IDLE_SETTLE_TICKS` (100) after the per-op bursts: the ticks right after a burst are slower
  (an old/new "energy idle regression" of 477 vs 400 us vanished with a 1000-tick window, 211 vs 232 us).
  `-P federationPerfWindowTicks=N` lengthens every window.
- The remote host differs by up to ~10% between runs of the same tree even when interleaved: a 7-round
  domain-menu `closedTick` A/B read +8% for the node-index round, but local JFR runs of both trees gave 332 vs
  331 us. Confirm a small tick-level difference locally before chasing it.
- Mock players tick their menus only in entity-ticking chunks; the benchmark broadcasts them itself via the
  TEST-ONLY `MenuBroadcasts` (bimodal results otherwise).

## JFR pitfalls
- `JAVA_TOOL_OPTIONS="-XX:FlightRecorderOptions:stackdepth=256 -XX:StartFlightRecording=dumponexit=true,filename=
  .../rec-%p.jfr,settings=<fine.jfc>"`. Without `dumponexit=true` the last chunk (the benchmark) is lost; without
  `stackdepth` the energy mesh's recursive stacks lose their bottom frames. Every Gradle JVM records too: pick the file
  with the most `Server thread` samples, not the largest.
- JFR samples short hot loops sparsely (energy extract: ~20 samples per second of loop); scale ops up or reason from
  code. Filter samples by a frame that is near the leaf (e.g. the benchmark lambda) and exclude benchmark frames to
  see tick-only cost. Scripts used: `jfrall.py` (subtree), `jfrexcl.py` (must/exclude), `jfrcallers.py` (callers).

## Findings that shaped the fixes
- Task 21 contract: every storage operation rechecks Policy activation, permissions, filters and source readiness,
  so the authority is never cached; the fixes only remove repeated work inside the check (a new `PolicyService` and
  saved-data lookup per call, `Set.copyOf` of both membership sets, streams, a duplicated readiness scan,
  validating before the native `isPreferredStorageFor`).
- `IGridNode.isActive()` = powered && booted && channels; powered and booted are Grid properties, each an AE2
  service lookup. `NodeActivity.activeOn` reads booted once per Grid. Always call `isPowered()` before `getGrid()`:
  AE2 `GridNode.getGrid()` throws for a destroyed node (the perf storage benchmark caught this on close).
- Energy reconcile ran every tick (first demand) and scanned every node of every consumer and provider Grid.
  `selectSource` now reads AE2's machine index (`IGrid.getMachineNodes(ownerClass)`, keyed by the node owner's exact
  runtime class, recorded in `DirectionalEnergySource.bind(owner, node)`); backend discovery caches the Grid's
  storage-carrying nodes per `NetworkIdentityService.nodeRevision()` (bumped on every addNode/removeNode) and still
  re-reads each listed node's state.
- The testmod always registers an observation receipt listener, which forced a `FlowState` (SHA-256 id) per
  operation in benchmarks only; `Listener.wantsFlows()` lets the meter skip it when no window keeps it.
- `EnergyBindingService.nanoAe` throws for amounts with more than 9 decimals (pinned by `EnergyExactAccountingTest`);
  it runs after the native extraction, so a fractional AE2 amount such as 0.1 + 0.2 would throw out of an energy
  operation. Reported, not changed (behaviour decision). Whole amounts now skip BigDecimal.

## Per-operation breakdown (local, `-P federationPerfOpsScale=20`)
- Energy MODULATE 1153 ns vs native 330 ns. TEST-ONLY part metrics: binding validation (`isCurrent`) 244 ns,
  provider-side native extract 85 ns, the Federation source's SIMULATE extract 420 ns, meter record 124 ns. The
  Federation path runs two AE2 energy services (consumer and provider) where Quartz Fibers run one overlay.
- Storage extract+insert: the authorization recheck is ~58% of the projection's samples, the meter ~21%, the
  native delegate ~6%. The recheck is ~25 hash lookups (mount, generation, compilation, domain, provenance cache,
  policy record, identity settlement, Grid services, domain membership); the Task 21 contract forbids caching
  its result, so the remaining work is removing lookups that repeat within one check.
- Removed repeats: the second policy-record lookup in activation (`activation(configured, ...)`), the level
  registry lookup inside activation (callers pass theirs), `provenance.isCurrent(domain)` after
  `discover(grid) == domain` (discover only returns the origin's current domain); a storage flow's resource id is
  built only when the flow is.
- Tried and reverted: the meter's event-id dedup (HashSet + ArrayDeque, limit 128) as a fastutil
  `ObjectLinkedOpenHashSet` or `ObjectOpenHashSet`: a standalone loop gave 47 ns vs 89 ns per record (open-addressing
  removal shifts keys), and `partRecordFlow` went 128 -> 171 ns.

## Lookup round (local, opsScale 20, 2 interleaved rounds x 3 repeats)
- Benchmark fix (TEST-ONLY): both energy scenes resolved the consumer Grid through `grids()` (five block-entity
  lookups and a list) inside the measured lambda. Resolved once, native energy is 84 ns MODULATE / 70 ns SIMULATE,
  not 330 ns: Quartz Fibers draw from the provider Grid's `GridEnergyStorage` buffer, a few field updates.
- Uncontended `synchronized` static lookups (`EnergyBindingService.find`, `FederationDomainRegistryAccess.get`,
  `LevelObservabilityService.get`) plus their WeakHashMap probes cost ~15% of an energy demand on JDK 21 (no biased
  locking). Each now keeps its last result in a volatile field, checked by level identity, replaced under the lock
  and cleared in `closeLevel`; `PolicyService.get` does the same. Energy MODULATE 1125 -> 960 ns, SIMULATE 475 ->
  395 ns, storage extract+insert 1741 -> 1635 ns.
- Binding validation keeps every check but reads less: a withdrawn flag replaces `bindings.get(key) != binding`
  (every removal withdraws, every put follows a removal), the binding holds its Grids' identity services instead of
  two `getService` calls per activation, the route guard scans a 16-slot array before falling back to an identity
  set, and `shareFederationDomain` compares single-domain memberships with `equals` (the TreeSet compared the id's
  characters).

- Accepted-flow bookkeeping: the meter window, the flowed-scope set and the rule's pair-flow window keep the last
  scope/key object and its entry, checked by identity (one binding repeats the same objects) and cleared wherever
  the map drops entries (`retain`, sweep, prune, `pairFlow`). `recordAccepted` 128 -> 110 ns.
- `StorageDependencyIndex.sourceCurrent` no longer scans the source nodes before `discover`: a domain's source
  nodes are exactly the capture's active nodes, all in its stamp with `active=true`, so `discover(grid) == domain`
  re-checks their readiness in the same operation. On failure the first operation now rebuilds the domain instead of
  the next reconcile; the result (0 accepted, mount removed) is unchanged (`providerInactiveAccepted=0`).
- Local totals after these rounds (opsScale 20): energy MODULATE 1125 -> ~825 ns, SIMULATE 475 -> ~330 ns vs
  native 84 / 70 ns; storage extract+insert 1741 -> ~1565 ns vs native ~445 ns; simulate ~590 vs ~155 ns.
  Idle ticks stay within their run-to-run spread (260-400 us for every version).

## Revision round (local, opsScale 20, 2 interleaved rounds x 3 repeats)
Each authority check keeps its inputs' revisions from the last full check it passed and repeats only what those
revisions do not cover. Every compare is exact identity or an exact revision: nothing expires by time.
- Revisions relied on: `FederationDomainRegistry.topologyRevision` (advances with every domain install/removal, so
  `isCurrent(reference)` and `shareFederationDomain` hold while it is unchanged), the policy store's high
  watermark (advances on every rule edit and deletion), `IdentityClaimIndex.revision` (new; advances on every
  claim add/remove/release), and the storage index's compilation/domain/direct-relationship maps (immutable,
  replaced whole on refresh, so identity is a revision).
- Identity settlement: `NetworkIdentityGridService.settlement()` re-settles only when the registry or the claim
  revision changed (it rebuilt an IdentityHashMap per call before). A settlement is an immutable record, so callers
  that matched a settlement object skip `PolicyActivation.matches` while `settlement()` returns the same object.
- Energy: a binding remembers (registry, topology, policy service, watermark) of its last passed full check; while
  all four are unchanged only the two Grids' settlements are read again. `backendCurrent` answers the last backend
  from a one-entry memo of the tick's map. MODULATE 866 -> 708 ns, SIMULATE 340 -> 190 ns,
  `partBindingCurrent` 160 -> 65 ns (v11 -> v12).
- Storage: each mount owns a `StorageDependencyIndex.CurrentCheck` that remembers the passed check's relationship,
  domain, maps, registry/topology and policies/watermark, the direct relationships' identity services, and the
  compilation it looked its relationship up in. `sourceCurrent` skips `domains.get(origin)` while the domain map is
  the same, and `NativeSourceDomainRegistry.discover(grid, probe)` answers from the probe while the registry's
  `mutations` counter (bumped before every change to `current`/`cache`) and the Grid's settlement object are
  unchanged: only the stamp is matched, which still re-checks every source node's activity. The stamp holds the
  Grid's energy and pathing services (fixed for a Grid) instead of two `getService` per match, and reads each node's
  Grid through `NodeActivity.gridOf` (null for a destroyed node, whose `getGrid()` throws; the old second
  `node.getGrid()` could throw there). `PolicyKey.hashCode` uses the capability's ordinal (the enum identity hash
  read the constant's header on every lookup); `EffectiveStorageAuthority` keeps an EnumMap.
  extract+insert 1372 -> 829 ns, simulate 500 -> 253 ns (v12 -> v13); native ~445 / ~155 ns.
- Mounts: `StorageMountService.mountsRevision` advances with every change to `mounts`/`mountGenerations` (after
  the put, before a removal, on close); a mount that found itself in both maps at a revision skips both lookups
  until it changes.
- Meter: an operation that makes its own event id (every energy demand and storage operation) cannot repeat, so
  `NativeTransportMeter.recordNew` skips the dedup set (which then stays empty) but still ages the window, and
  makes the id only when a flow is built (not while the window is overflowed without a receipt listener).
  Operations with ids from elsewhere (processing lanes) keep the dedup path. `AcceptedStorageOperation` no longer
  carries an id.
- JFR after the storage round: the native NetworkStorage chain is ~41% of a Federation extract, authorization
  ~38%, bookkeeping ~20%.
- v12 -> v15 (mount revision, meter `recordNew`): storage extract+insert 1390 -> 607 ns, simulate 503 -> 224 ns;
  energy MODULATE 721 -> 446 ns. `partRecordFlow` now measures the `recordAccepted(scopes, resource, ...)` overload
  that demands use (52 ns); before v15 it measured the explicit-id path (107 ns), so the two are not comparable.
- What remains is mostly cache misses on other objects (the benchmark cycles 1000 item types): the two Grids'
  identity services, the source Grid's pathing/energy services, the route guard's thread-local.
- `IdentityEpoch` (static, server thread): advances on every claim-index change of any level and every identity
  service node/provisional/duplicate/registry change. While it is unchanged a settled Grid's `settlement()` returns
  the same object, so energy bindings and storage checks that matched at an epoch skip both `settlement()` reads,
  and the discover probe skips the source Grid's.
- Energy route guard: the thread-local `Demand` is found through a volatile last-used field checked against its
  owner thread (a miss falls back to the thread-local), and the visited Grids live in `Demand` itself. A consumer
  source's sorted candidate list is kept in a one-entry memo cleared with the candidates map.
- Storage bookkeeping names a key type's unit once per type (`lastKeyType`) instead of a string search per record.
- The meter ages its window by sequence number: a `recordNew` event only advances the counter, and an id that can
  repeat leaves the dedup set once `eventLimit` later events were admitted (what the old deque of the last
  `eventLimit` events did, without a deque entry per event).
- v15 -> v16 (opsScale 20, 2 rounds): storage extract+insert 608 -> 569 ns, simulate 225 -> 206 ns; energy MODULATE
  474 -> 355 ns, `partBindingCurrent` 54 -> 46 ns.
- Warm-up artifact: at opsScale 20 `extractSimulate` read ~300 ns for v16/v17 against ~210 ns for v15 (9 JVMs
  each), while `partSourceSimulate` dropped. Reverting the route guard or the candidates memo did not move it. At
  opsScale 100 v17 beats v15 everywhere (MODULATE 217 -> 189 ns, SIMULATE 178 -> 146 ns, `partSourceSimulate`
  184 -> 134 ns): the short SIMULATE window ran before C2 finished that path. Judge a single small-window metric
  that moves against its own parts at a larger opsScale before chasing it. AE2 also breaks equal energy-provider
  priorities by identity hash, so provider order differs per JVM and some metrics are bimodal across JVMs.

## Node-index round, remote A/B (3 interleaved rounds, medians)
| metric | before | after |
|---|---|---|
| energy extract MODULATE | 3963 ns | 3156 ns |
| energy extract SIMULATE | 2495 ns | 2278 ns |
| storage extract+insert (network) | 2982 ns | 1933 ns |
| storage list all (network) | 104 us | 91 us |
| idle ticks, open menu | unchanged within noise | |

## Native comparison (remote, 948d7ce, default op counts)
| metric | Federation | native | ratio |
|---|---|---|---|
| energy extract MODULATE | 3963 ns | 980 ns | 4.0 |
| energy extract SIMULATE | 2492 ns | 684 ns | 3.6 |
| energy idle tick | 412 us | 330 us | 1.25 |
| storage simulate extract | 840 ns | 516 ns | 1.6 |
| storage extract+insert | 2192 ns | 1189 ns | 1.8 |
| storage list all (1000 types) | 90 us | 320 us | 0.28 |
| storage idle tick | 480 us | 671 us | 0.72 |
