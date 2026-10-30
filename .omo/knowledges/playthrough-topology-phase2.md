# Phase 2 topology harness

- `PlaythroughTopologyGameTests` calls the existing Phase 1 serial driver. Seeded factories share recipes,
  source extraction accounting, exact full-cell balance, signatures, return-ledger/transit snapshots and delayed
  teardown verification. Generator version 2 persists the material/factory/requester role permutation.
- Filter quest items before parsing SNBT: unrelated component-bearing quest tasks can be unreadable by vanilla
  TagParser. The three default cross-chapter controls are blank pattern, oak drawer and crafting table.
- With dev's projection startup-sweep fix, the existing 111-case matrix passes using power/CPU visibility plus
  Phase 1's 25 idle ticks. The former additional 60-tick grace is unnecessary. Do not rerun the cake regression
  as part of the continuation; its original-scene confirmation belongs to the orchestrator.
- Native Pattern Provider bypasses external-inventory delivery for adjacent ICraftingMachine and requires an
  external-inventory-capable processing pattern for an Endpoint. Use the original crafting pattern in a real
  Molecular Assembler's configuration slot 10, and its equivalent processing inputs/outputs in the provider.
  Each recipe needs its own programmed assembler. Assembler slot 10 is configuration, not leftover material.
- Federation Pattern Provider's slot-owning facade publishes no patterns. The executing Endpoint lane exposes
  available patterns, busy/send state and return inventory; inspect that lane for signatures and buffers.
  Both Endpoint paths passed real blank-pattern controls; split Federation factories passed 36 cases.
- A Bridge part on native cable blocks direct native connectivity. Removing only the part exposes the cable
  and can merge the grids, invalidating a link-outage fixture. Remove/restore the cable host and Bridge together.
  The corrected Bridge control, removal/restoration and cancellation all passed.
- RouterFacePort exposes no native boundary node for Routers. Federation Pattern Provider front ports do publish
  native domain evidence, so they can attach members to one Router without any Switches. The `router` join
  exercises this actual supported route; Router-to-native-cable direct attachment remains unavailable.
- Quantum uses production paired cubes and OtherDimensionSite's nether region. Only the east factory is moved;
  source and requester stay overworld. Site tickets must be released without accessing remote block entities
  during unload, since getBlockEntity can reload their chunks. Keep pre-release block-entity references and
  require their isRemoved flags plus absent loaded factory chunks before reloading.
- Checking every block entity in the whole nether site is stricter than checking factory chunks. An exploratory
  unload timed out with hasChunk=false but Quantum Cube/cable entities still unremoved; the isolated retry
  actually unloaded and completed (cont-chunk3, recorded flaky). Preserve this evidence rather than treating
  the first attempt as coverage. Async unload deadlines use wall time because tick sprint can outrun disk saves.
- Cancellation uses a single-craft target after its first accepted push. This makes the original full plan's
  balance the exact expected output even after cancellation, without inventing execution counts for unstarted
  recursive crafts. CPU release and every return/transit entry still have to settle.
- Restart reuses tools/compat_transit_restart.py and ae2federation.compat.transitRestartPhase, with persistent
  RestartChunkTickets. Prepare disconnects the Router after the observed first push and saves the actual plan
  balance, extraction counts, patterns, positions and baseline. Recover uses a different JVM and the original
  world, restores the Router and calls the same factory final checks without resubmitting a job. A native job
  may be cancelled by shutdown; retained outputs and zero debt are the required clean alternative.
- Build serially before parallel --no-build servers. Concurrent Gradle builds can race over compat class files.
  A read-only project-cache lock in this environment was bypassed with --project-cache-dir /tmp/ae2f-playthrough-gradle;
  --no-daemon -Dorg.gradle.jvmargs=-Xmx1G --max-workers=2 avoided a disappearing existing daemon.
- Capacity fixtures require branch-default demand limit 8 and use real Endpoints to reach demand 8/9.
  Normal demand crafts; over-limit refuses with no extraction or stock. The ledger's watched job list may
  temporarily retain DONE/RUNNING metadata after debt is zero; the shared delayed teardown verifies retirement.
- Exact executed coverage, unresolved findings, commands and copied evidence belong in
  build/playthrough/phase2-handoff.md. Do not infer gameplay coverage from harness PASS alone.

- The real two-JVM restart passed with the same crafting UUID and owed=held=1 after restart. An unpowered
  requester exposes zero active CPUs until the Router is restored; waiting for CPU visibility before restoration
  deadlocks the harness. Read the requester's CraftingBlockEntity cluster instead: busy and
  craftingLogic.getLastLink() equal to the saved ID proves resumption before the Router returns (else
  job-lost-on-reload). Final balance, CPU release and debt checks passed.
- Continuous tick sprint starves vanilla ChunkMap's unloadQueue: its callbacks run only when haveTime is true
  (unless the queue exceeds 2000). Do not bypass isRemoved/chunk absence or simulate the lifecycle.
- The ordinary Bridge outage removes its cable host and Bridge together. `bridge-part` preserves the earlier
  part-only merge/split operation as an explicit regression, instead of dismissing its observed remaining debt.
- All 30 seeded cancellation cases passed, but the first 30 Java Random seeds sampled only beam, 2x2 bundle and
  router-switch joins. Explicitly pinned supplements cover multi-hop, 3x3 bundle and P2P; random sampling is not
  evidence that every dimension value executed. No-Switch Router passed all seven disturbances using actual
  Federation Pattern Provider front-port membership.

- Unload cause, from DistanceManager/ChunkMap reflection (PlaythroughChunkDiagnostics, 2026-10-23): while the
  4096-craft job still had inputs to push, every tick showed an `unknown:33` ticket on the factory chunk (40953
  of 40954 samples), with zero harness accesses and no nearby tickets. PatternProviderLogic.pushPattern ->
  ICraftingMachine.of -> level.getCapability -> getBlockState is a main-thread getChunk that re-acquires the
  holder from pendingUnloads. Once the job ended, the chunk sat at `L45 pending` with unloadQueue 1399 under sprint
  and never unloaded; a warm retry unloaded because the queue had reached 2000. Pausing sprint alone failed because
  at 20 tps the job keeps pushing for ~34 minutes. Fix (harness): release the site only when the requester CPU's
  craftingLogic inventory holds nothing unpushed, and pause sprint for the unload window by default. Seeds 0 and 1
  then unloaded cold in 0.25-0.3 s with a busy CPU and passed on the first attempt.

- Part-only Bridge merge/split reproduces a return-debt leak with just one assembler: seed 0, networks=3,
  join=bridge, power=NORTH, roles=WEST/EAST/NORTH, target ae2:blank_pattern count 1,
  perturbations=bridge-part, assemblerCopies=1. Both attempts pass exact inventory/CPU/buffer checks but retain
  owed=1, held=0 after the requester receives its one output. This is the smallest tested reproduction;
  production code remains unchanged. See the copied cont-bridge-part-min report and phase2-handoff.md.
- Review fixes (2026-10-23): `cable` cuts center+factory face, restoring the exact block (native cable via
  placeCable); a disturbance that did not happen is `perturbation-not-executed`, outside byDimension/byPerturbation;
  preconditions are checked per case after the baseline (`skipped-precondition`); Endpoint buses sit on the side
  away from the hub (factory=WEST/power=WEST previously merged a subnet into the factory via the power cell).
- The restart aggregation unit test uses synthetic reports, not Minecraft: it verifies that a passing isolated
  confirmation retains the original failure, reports flaky in both summaries and Markdown, and presents the
  confirmation as the final federated result. It must not be counted as gameplay coverage.

## Upstream flake: NAST fails to load in mmcr (2026-10-30)
Run 41066819371 (docs-only commit 152bd7e4) ended `NO-REPORT nast-topology` after 24 s: mmcr 0.1.3 crashed in
parallel mod construction (`ExceptionInInitializerError` from `ModItems`, caused by a `ConcurrentModificationException`
on `modloading-worker-0`, while its KubeJS startup machines registered). Not our code; the next run on the same tree
passed. First such failure in the last 60 playthrough runs; if it recurs, it is mmcr's static registration racing
another mod's construction, a candidate upstream fix.
