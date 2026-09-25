# Task 37 scale benchmark: INCOMPLETE

## Small Federation after the identity and source-index fixes (2026-09-26)

Local host (60 logical CPUs, shared, not quiesced), `taskset -c 2-9`, Java 21, the selected
`federationBenchmark -Pprofile=small-federation` with `-Dae2federation.federationStageDiagnostics=true`, one run per
revision. Every revision had the replay-fixture correction applied (configure each machine once; before, the second
256-job cycle threw because no earlier run had completed a first cycle). Single runs under different background load are
descriptive, not medians or a speedup claim.

| Revision | Warmup (300 s) | Sample (600 s) | Notes |
| --- | --- | --- | --- |
| `4becaf3` baseline | failed at 24 jobs (~311 s, ~4,670 ticks) | none | server-thread CPU ≈ wall time; ~66 ms per tick |
| `d89241d` storage source index only | failed at 24 jobs | none | the source-index change alone does not move the failure |
| `c371346` identity cache only | 3,388 jobs | 6,520 jobs | passes |
| `31e8533` all changes | 2,747 jobs / 547,697 ticks | 5,655 jobs / 90,480 units / 1,128,262 ticks | passes; persisted result consumed by `federationVerifyEvidence` |

The stage diagnostics attribute the baseline's time to `TARGET_INPUT_CONTEXT` and `MACHINE_CALLBACK` with server-thread
CPU equal to wall time. With the identity settlement cache the same stages cost about 0.6 ms of server-thread CPU per
tick. The 256-job warmup failure was therefore caused by per-read identity settlement (each read copied all node
lineages and compared them with every other Grid's lineages; see `identity.stable-query-cost`), not by the storage
discovery path; that path's own cost is measured separately in `storage.source-index-scale`. The difference between the
identity-only and all-changes sample counts comes from single runs under different load and is not attributed.
Direct and native-subnet layouts were not re-run, so there is still no same-source three-layout comparison, late/ultra
or soak. Task 37 remains incomplete.


Started 2026-09-23 UTC. This is a chronological execution-status report, not a scale qualification. For the latest source-matched small snapshot, direct and native-subnet each have three complete 300-second warmup / 600-second sample repetitions. Federation failed the 256-job warmup after 27 jobs and has no accepted window or sample. Resource parity, late/ultra timing, full three-layout medians and Task 38 soak are absent. Earlier sections below describe historical checkpoints superseded by the [latest timed evidence](../../.omo/evidence/task-37-small-timed-first/verification.md). No performance budget was changed.

## Implementation progress

`tests/benchmarks/scale/{small,late,ultra}.json` now declare the fixed tier targets and full 300-second warmup, 600-second sample, three repetitions and three requested layouts; `budgets.json` declares correctness requirements without a timing median. These are intended inputs, not measured topology or scale support. A failing-first JUnit contract went red because the files were absent, then passed when they were added. The existing processing and mixed benchmark contracts passed before these changes.

The Gradle benchmark entrypoint now splits a comma-separated list, rejects duplicates, empty members and unknown IDs before any native launch, and runs each registered ID serially through the existing source-bound schema-v3 producer. A tier cannot be registered honestly until its real layout GameTests and evidence consumer exist; hence this change does not produce a Task 37 result.

## Attempts

From the repository root:

```bash
./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=small,late,ultra -PevidenceDir=.omo/evidence/task-37 --dependency-verification=strict --no-configuration-cache
./gradlew :neoforge-1.21.1:federationVerify -Pcases=perf.resource-integrity,perf.reject-incomparable-speedup,perf.reject-budget-regression -PevidenceDir=.omo/evidence/task-37-checks --dependency-verification=strict --no-configuration-cache
./gradlew :neoforge-1.21.1:check :neoforge-1.21.1:build --dependency-verification=strict --no-configuration-cache
```

The initial benchmark exited nonzero at `gradle/federation-qa.gradle:6516` with `Unknown benchmark profile: small,late,ultra`. After comma-list prevalidation was implemented, it rejected `Unknown benchmark profiles: [small, late, ultra]` before launch. The verify task exited nonzero at line 6514 with `Unknown cases: [perf.resource-integrity, perf.reject-incomparable-speedup, perf.reject-budget-regression]`. Neither exact Task 37 command created an attempt directory, run ID, native log, scene output, raw sample, or Task 37 schema-v3 result to consume against the current source. The strict build passed; it does not qualify performance.

The registered profiles are `harness-native-smoke`, `processing-small`, `mixed-small`, and `ui-small`. Task 20 freezes only processing correctness/resource budgets. Task 30's small mixed native scene does not cover three scale layouts. The new scale declaration assertions are not native scene or resource-conservation assertions; those acceptance obligations remain open.

An actual native `processing-small` development run under `.omo/evidence/task-37-development/attempt-20260923T082743302Z` launched and logged `All 1 required tests passed :)`. Its `benchmark-native.properties` contains 40 assertions and real per-layout processing accounts, but the outer producer rejected it at `Task 20 benchmark capture identity is stale`; the committed baseline's dependency-lock hash is `74bdb2dc...` while the current strict verification metadata hash is `2677d763...`. No `result.json` exists for this attempt, so it is not accepted evidence and cannot qualify Task 37. The discrepancy was not fixed by silently rebinding Task 20's frozen baseline. A duplicate-list negative probe rejected `processing-small,processing-small` before launch.

The existing `harness-native-smoke` profile passed a real GameTest under `.omo/evidence/task-37-dispatch-smoke/attempt-20260923T083231326Z/result.json`; its schema-v3 current-source persisted consumer passed immediately afterward. This proves the modified task still launches and validates a registered single profile. It is neither a scale scene nor proof of multi-profile native execution. Subsequent source edits can invalidate its current-worktree identity, as intended.

Fixture inspection found a substantive missing path: the Task 20 generated comparison has a real native planner/CPU but only hard-coded 1/16-Grid scenes; the existing native-subnet fixture directly pushes a Pattern without a planner/CPU; the mixed machine fixture handles real item capabilities but is not a three-layout scale generator. Registering any of these unchanged as `small`, `late`, or `ultra` would misrepresent its native work. Native scale layouts, manifest registrations and the `perf.*` semantic consumer are not implemented in this attempt.

| Tier | Target Grids | Target logical Patterns | Order units | Big grid | Native subnet | Federation |
| --- | ---: | ---: | ---: | --- | --- | --- |
| Small | 16 | 256 | 1,000-10,000 | 3/3 descriptive timed windows | 3/3 descriptive timed windows | warmup failed, 0/3 |
| Late | 128 | 4,000 | 100,000-1,000,000 | not measured | not measured | not measured |
| Ultra (exploratory) | 512 | 16,000 | 1,000,000-10,000,000 | not measured | not measured | not measured |

Three tiers, three layouts, three repetitions, and 5-minute warmup plus 10-minute sample require at least 405 minutes (6 hours 45 minutes) of serial measurement windows, excluding setup. The shared `run-gametest/world` lock prohibits concurrent native children. This host showed 60 logical CPUs, about 95 GiB available RAM, and load averages 5.94/4.93/3.62. It is shared and unpinned, not a quiesced baseline machine. These are host observations, not benchmark samples. No timing baseline, >10% timing trigger, supported scale, or measured regression fix can be claimed.

Future qualification must use real AE2 planner, CPU, provider, storage, machine, return, and physical inventory paths and match CPU/energy/loaded chunks/replay/machine capacity where expressible. Unexpressible native topology is non-comparable, never a speedup. Each layout needs source-bound schema-v3 identity, unique run ID and child, three complete windows, raw tick/wall-clock samples and per-resource ownership equations. Freeze medians only after repeated runs on matching pinned hardware and observed load; never expand budgets silently.

## Negative probes and cleanup

- Stale-state and dirty-worktree: existing schema-v3 consumers protect registered results, but there is no Task 37 result to mutate or consume; Task 37 remains untested.
- Hung/long child, interrupted benchmark, flaky rerun, and misleading-success-output: no Task 37 child launched; existing harness process containment is not evidence for these new profiles.
- Malformed input: both exact unknown selections were rejected before launch. No broader malformed-profile assertion ran.
- Prompt injection: not applicable to static local profile JSON and GameTest output; neither is an instruction stream interpreted as agent commands.
- Cleanup: both Gradle failures ran `federationCleanupGameTest`. A subsequent process search found no GameTest process; no Task 37 attempt artifacts were created and no owned native child needed termination.

Task 37 remains incomplete because its native scale scenes and semantic perf cases have not been implemented or measured. The length of the required run is not, by itself, an environment blocker. Task 38 must not infer a supported measured scale from this report.

## Native topology fixture continuation

A selected NeoForge GameTest loads each tier's JSON declaration and constructs powered AE2 ME Chests with 1K cells. Earlier small, late and ultra topology tests reported 16, 128 and 512 distinct active `IGrid` instances under `.omo/evidence/task-37-topology-small-bounded/`, `.omo/evidence/task-37-topology-late-bounded/` and `.omo/evidence/task-37-topology-ultra-final/`. The ultra result depended on task-added `ServerLevel.setChunkForced` calls outside the bounded GameTest structure and is **safety-rejected**, not valid scale evidence. The previous one-shot iron insert/extract assertions were also removed: they did not exercise a Processing factory.

Two earlier late-tier fixture attempts failed and are not qualification evidence. Vertical spacing of two merged adjacent powered pairs into 64 Grids (`.omo/evidence/task-37-topology-late/`). Extending placements outside the test footprint left nodes inactive (`.omo/evidence/task-37-topology-late-spaced/`). The repaired fixture retains spacing of three vertically and limits every placement to the original x/z 1..15 volume (5 vertical layers x 8 x positions x 8 z positions = 320 slots). The bounded small test passed (`.omo/evidence/task-37-safety-small/`). The selected ultra test failed before fixture placement with `Scale tier exceeds bounded physical Grid fixture capacity` (`.omo/evidence/task-37-safety-ultra/`, nonzero Gradle exit). It is explicitly unsupported by this fixture; no forced chunks or ticket-release success assertion remain.

Three selected small planner/catalog experiments failed at `Waiting for small native Grids` before reaching native catalog or planner assertions: `.omo/evidence/task-37-small-planner-attempt/`, `.omo/evidence/task-37-small-planner-staged/`, and `.omo/evidence/task-37-small-planner-forced/`. The attempted planner fixture was rolled back; neither staging providers nor forcing chunks demonstrated readiness. Current GameTests check topology only, not factory or benchmark scenes. They do not instantiate the declared logical Patterns, crafting CPUs, planner, providers, machines, returns, three comparable layouts, complete per-key accounts, measurement windows, or repetitions. Task 37 remains incomplete. Both new dev-run logs and `runtime-cleanup.txt` receipts are retained, with no benchmark result claimed.

## Later small route development (2026-09-23)

The manual-only `scalesmallfederationroutedevelopment` now follows two authentic one-job native Processing probes and a separately settled target ME Chest, Endpoint and Export Bus. Within the x/z 1..15 footprint, red and blue native cables at `(4,2,1)` and `(5,2,1)` attach a physical east-facing Bridge without merging source and target native Grids. The production Bridge publishes a common Federation Domain; a directional Processing Policy enables EXECUTE and SUPPLY; the Endpoint owns a Claim for the exact Provider runtime identity. After the source's earlier explicit storage edge is replaced by physical north adjacency, the Bridge is refreshed against the settled boundaries. The production target resolver reports `ACTIVE`, one native push is accepted, and the target physical cell contains one cobblestone. The fixture also asserts 16 distinct active Grids and one settled target identity claim. The exact selected command passed one required GameTest and Gradle exited zero:

```bash
./gradlew :neoforge-1.21.1:runGameTestServer -PfederationGameTestSelection=benchmark -PfederationGameTestId=scalesmallfederationroutedevelopment -PfederationNativeEvidenceFile=$PWD/.omo/evidence/task-37-physical-route-east/benchmark-native.properties --no-configuration-cache --dependency-verification=strict
```

The causal phase receipts are in `.omo/evidence/task-37-physical-route-east/scale-small-route.log`. The earlier rejected push at `.omo/evidence/task-37-physical-route-attempt/` reported `DOMAIN_DISCONNECTED` because source edge rewiring invalidated the Bridge's published membership; the final fixture refreshes the physical Bridge before pushing. The identity-only selector also passed again. The original `scalesmallprocessingdevelopment` still intentionally fails at `Small Federation Processing layout remains unqualified`. A successful single input push is not a third planner/CPU/Endpoint/return job, nor a replay of the declared scale profile. The earlier topology-only characterization above is historical and superseded only for this narrow development scene; no Task 37 profile, timing window, conservation matrix or budget is qualified.

## One-job three-layout development (2026-09-23)

The prior deliberately failing `scalesmallprocessingdevelopment` checkpoint above is now superseded. The selected test passes one real planner/CPU job each for native-big-grid, native-subnet and Federation, retaining 16 distinct active Grids. The Federation job does not use the route selector's direct `provider.push`: native CPU submission invokes the Provider runtime, whose final physical-route receipt reports `resolution=ACTIVE pushAccepted=false`. One cobblestone was observed in the separate target physical cell before the AE2 Export Bus fed the registered machine. The machine returned one diamond via the Endpoint's south-sided production item capability into the exact owning native Provider return inventory; AE2 injected it into the source physical cell and completed the requester callback. Each layout's receipt reports one distinct native job ID, zero remaining source input, one machine output, one callback and one physical source output; the Federation target cell also finishes empty.

```bash
./gradlew :neoforge-1.21.1:runGameTestServer -PfederationGameTestSelection=benchmark -PfederationGameTestId=scalesmallprocessingdevelopment -PfederationNativeEvidenceFile=$PWD/.omo/evidence/task-37-third-job-final-receipts/benchmark-native.properties --no-configuration-cache --dependency-verification=strict
```

This selected run passed one required GameTest and Gradle exited zero. The three layout receipts are persisted in `.omo/evidence/task-37-third-job-final-receipts/scale-small-processing.log`; route phases are in the adjacent `scale-small-route.log`. The failing-first selector and both passing route/identity regression selectors are recorded in `.omo/knowledges/task-37-scale-blocker.md`. This is only a single-job correctness probe, not a 256-Pattern/1,000-10,000-unit small replay or a measured benchmark. Late and ultra, matched resource budgets, timed windows/repetitions, raw samples, medians and `perf.*` verifiers remain unqualified. Task 37 remains incomplete.

## Small replay resource-integrity preflight (2026-09-24)

`tools/task37_resource_preflight.py` reads the three selected *process* transcripts, not sibling append-only logs. It binds the declared `small.json` Pattern/order/layout inputs by hash but never treats that declaration as a measurement. It verifies the unique GameTest/Gradle success markers, source-host topology receipt, each layout's catalog/layout and completion receipts, 256 ordered host-major `(inputKey, outputKey)` pairs, 256 distinct native UUIDs and physical owners, 16 typed transitions and callbacks per job, source CPU/return idle, target input where applicable, and each paired physical Drive row through the five-cell 4,096-unit readback. It hashes both the selected transcripts and the ordered typed catalog/input replay. The output ledger reports the actual source CPU/channel and Drive checks, and discloses direct one-Grid machine adjacency versus 16 Interface or Bridge/Endpoint target Grids; topology is not falsely equalized. `small.json`'s `gridCount` is a tier declaration, not an assertion that the three intrinsically different layouts have equal physical Grid counts.

The optional `--observations` JSON has `schemaVersion: 1` and a `layouts` object keyed by the three layout names. Each entry must bind `receiptSha256` to its process transcript and match an `AE2F_SCALE_RESOURCE_SNAPSHOT layout=<layout> measurementPhase=postReplay` record **inside that same transcript**. Required positive integer observations are `sourceCpu`, `sourceDriveCells`, `sourceGridUsedChannels`, `cpuBytes`, `driveCellBytes`, `totalItemStorageBytes` (source plus targets), `machineCount`, `machineInputSlotsPerHost`, `machineUnitsPerTick`, `energySupplyAePerTick`, `energyDebitAe`, `peakEnergyDemandAePerTick`, `worldLoadedChunks`, and `worldLoadedChunksPeak`; `worldChunkTickets` and `worldChunkTicketsPeak` may be zero. `energyStorageKind` must be `finite`. These are required *live measured observations*, not values copied from the tier JSON, fixture constants, creative powered booleans or selected pod chunk-key sets. Values must match the persisted post-replay snapshot. Controllable capacities, energy supply, and whole-world chunks/tickets must be equal across layouts; finite energy debit and peak demand must be measured but are reported as route-dependent outcomes, not force-equal inputs. Source CPU/Drive/channel measurements also reconcile with the replay ledger. Extra target cells and power sources remain disclosed topology, while their capacity/supply contribute to the equal total provisioning comparison. An absent snapshot rejects even a complete handwritten observations JSON. A producer for those live snapshots has not been implemented, so the current transcripts cannot pass this gate. `COMPARABLE` would only be a resource-input decision, not a speedup or timing qualification.

Run the current-evidence preflight from the repository root:

```sh
python3 tools/task37_resource_preflight.py --native-big-grid .omo/evidence/task-37-large-direct-256-development/green-attempt.log --native-subnet /tmp/opencode/task37-large-subnet-256-green.log --federation /tmp/opencode/task37-large-federation-256-green-complete.log
python3 -m unittest tests/test_task37_resource_preflight.py
```

The first exits 1 with `NON_COMPARABLE` and `speedupEligible=false`: the ordered work is equal, but finite energy debit/supply, machine/CPU/storage capacity, whole-world loaded chunks/tickets and peak demand have not been measured. The second tests mismatched CPU/capacity/input and missing energy/chunk evidence using explicitly test-only in-memory values, not replacement source artifacts. Existing `small,late,ultra` profiles and `perf.*` cases remain unregistered; the generic benchmark harness is unchanged. See `.omo/evidence/task-37-resource-integrity-preflight/verification.md` for process hashes, command exits and further limits. Task 37/38/40 and F1-F4 remain `[~]`.

## Subsequent partial perf verification wiring (2026-09-24)

The preceding paragraph describes the earlier preflight checkpoint. `perf.resource-integrity` and `perf.reject-incomparable-speedup` are **now registered** with a dedicated persisted-input backend; `perf.reject-budget-regression` and timed `small,late,ultra` are still absent. The two new cases require explicit three selected process-log paths and SHA-256 values (`-Ptask37NativeBigGridLog`/`Sha256`, `-Ptask37NativeSubnetLog`/`Sha256`, `-Ptask37FederationLog`/`Sha256`). Optional independently measured observations require both `-Ptask37Observations` and `-Ptask37ObservationsSha256`. Selected inputs are copied into the source/attempt-bound report only after hash verification, then the existing Python preflight checks all three physical replays. A missing resource snapshot cannot be replaced by authored JSON, a positive elapsed time or powered flags. The negative case additionally runs the explicitly test-only, snapshot- and receipt-bound mismatched-capacity fixture and requires its rejection.

The exact current three-log verification command, hashes, child exits and report are recorded in `.omo/evidence/task-37-perf-verify-wiring/verification.md`. Its real decision is **`NON_COMPARABLE`**, outer exit 1 and persisted `BLOCKED` report, because no finite energy, whole-world chunk/ticket or capacity measurements were emitted by those runs. The existing persisted consumer rejects the blocked report; for a future comparable result it reruns the parser over the bound attempt-local inputs and requires an identical semantic verdict. A comparable resource-input verdict alone would **not** be a timing or speedup claim. Tasks 37/38/40/F1–F4 remain `[~]`.
