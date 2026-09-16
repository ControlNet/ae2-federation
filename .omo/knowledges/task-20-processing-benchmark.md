# Task 20 Processing Benchmark Knowledge

## 2026-09-17 profile-derived lifecycle repair

- `processing-small-v3` declares `executionTimeoutSeconds=300` and `shutdownGraceSeconds=10`; the manifest registry must match the profile bytes.
- The runner derives both values from the selected profile and persists the declaration. The consumer compares report and child evidence to that declaration rather than enforcing a universal 300-second ceiling.
- Execution deadlines are operational containment, not performance gates. Strict positive integral parsing and a seven-day safety bound reject malformed declarations while allowing future 45-minute and two-hour profiles without verifier changes.
- A focused executable contract accepted `2700/10` and `7200/10` without waiting and rejected one-second-short mismatches. Timing remains `environment-sensitive-secondary`.

## Qualified runtime

- Java 21.0.12, Gradle 9.2.1, NeoForge 21.1.250, and AE2 19.2.17.
- Canonical profile `processing-small-v3` uses seed `20019006`, 256 physical/logical Patterns, 15 Lanes,
  270 live Provider entries, 16 Grids, one real crafting CPU, and equal-volume large/small variants.
- Accepted evidence is `.omo/evidence/task-20/attempt-20260916T051227490Z/result.json`.
- Exact negative-QA evidence is `.omo/evidence/task-20-selftest/attempt-20260916T051620201Z/result.json`.

## Runtime boundaries

- Native and Federation scenes run serially with identical inputs and outputs. The workload executes every Pattern slot
  through authentic planner/CPU/provider work and records one runtime participation receipt per slot and scene.
- A production Endpoint source needs an established identity handoff before its isolated Provider Grid can settle. The
  benchmark uses one authentic native push for that setup and resets all counters after topology and CPU preparation.
- Adding an unseeded crafting CPU to a settled source Grid produces `AMBIGUOUS_MERGE`. Seed the CPU's `proxy` managed node
  with the source's confirmed network identity before connecting it.
- Rejected targets are saturated before the pre-call snapshot. This excludes fixture prefill from mutation accounting and
  proves the four rejected calls change the target by zero.

## Evidence contract

- The exact properties allowlist rejects missing, extra, malformed, and duplicate fields.
- Frozen baseline identity binds profile bytes, budget bytes, dependency lock/version tuple, and a canonical capture
  identity. Repository state remains on each result as source revision plus dirty-worktree identity, outside the baseline.
- Correctness gates reconcile input, native remainder, retained responsibility, delivered output, final return inventory,
  equal native/Federation resources, and zero cross-run carryover. Timing remains secondary and environment-sensitive.
- The fully rebound adversarial consumer rejects schema, participation-receipt, topology, schedule, five-cohort,
  accounting, timing, baseline, budget, and report-projection mutations.

## 2026-09-16 accepted v2 finalization

- Three canonical captures matched all 648 normalized non-timing/non-hash semantic fields. Native topology digest
  `536d9a1bd2547b60f0a1e92244eb47bc1b726fd4eac2c5cf34c73560f0b90a07`, Federation topology digest
  `acaca6b8bba27424f5155b824420ad8a74f3b035605c1fada2d7e8c049b027a3`, and schedule digest
  `078a48a2db8f69fff4ad2a2061e33cf2f6bad3e51fca09991dd40d9411ed4597` were stable.
- Alternate seed `20019007` preserved scale and conservation while changing both runtime topology digests and the schedule
  digest. Canonical persisted consumption remains pinned to seed `20019006`.
- Each native/Federation scene emitted exactly 256 unique slot receipts covering `0..255`. Five disjoint cohorts cover
  all 15 Lanes and 15 Endpoint identities; their independent fairness, lock, rejection, return-pressure, and bounded-buffer
  equations pass.
- Canonical aggregate evidence records `assertions=40`, `operations=514`, `inserted=15360`, `extracted=15360`, equal
  native/Federation `acceptedInput=3840`, `deliveredPrimary=960`, `deliveredByproduct=480`, and fairness spread zero.
- Fresh Task 17, Task 18, and Task 19 producers, persisted consumers, and adversarial self-tests passed after the repair.
  All 50 changed/new Java files are at most 250 physical lines and all changed/new Java diagnostics are clean.

## 2026-09-16 scale-repair findings

- The accepted `processing-small-v1` artifacts above are superseded by the pending 16-Grid/256-logical-Pattern repair and must not be reused as Task 20 completion evidence.
- The repaired replay reached native AE2 planning, CPU dispatch, one accepted push, one busy rejection, machine completion, and one progressing return drain. Final Provider return inventory, send queue, and retained responsibility were all zero, while the reconstructed requester received no completion callback.
- Requester UUID persistence alone does not prove nexus reattachment. Reload validation must observe one `jobStateChange`, `observedDone=true`, exact delivered output, and an absent completed active link.
- Federation Endpoint return ownership is one mutable `EndpointReturnOwner`; a 256-Lane benchmark must correlate the accepted Lane logic with `itemReturnContext().owner().logic()` to detect cross-Lane replacement.
- `GeneratedProcessingFactoryScene.ready()` is invoked on every GameTest tick. Once a scene has started it must not
  re-run setup readiness: the reload phase intentionally destroys the old requester node, and setup readiness otherwise
  blocks the next tick before `finishRequesterReload()` can reconstruct it.
- Fixture-owned creative energy cells must be removed during teardown. For benchmark scenes, use the existing isolated
  power-service path for both native and Federation Providers; physically connecting an independently settled energy-cell
  Grid to a newly identified Provider Grid can produce `AMBIGUOUS_MERGE`.
- With setup-only readiness and isolated native power, the focused `processingbenchmarksmall` GameTest passes both serial
  scenes, including the authentic requester save/destroy/reload continuation. Do not regenerate baseline or budgets until
  the remaining v2 topology, workload, and verifier contracts pass deterministically.
- The approved Pattern/Lane cardinality is 256 physical and logical Patterns over 15 Lanes: slots 0-254 map round-robin
  to one Lane and slot 255 maps to all 15. AE2 consequently publishes 270 live Provider entries (255 + 15) without 256
  concurrent Lanes. The focused GameTest passes with these counts derived from registered `IPatternDetails` instances.
- A temporary non-baseline v2 capture at `/tmp/opencode/task20-v2-observed.properties` showed identical native/Federation
  values: 16 counted Grids, 256 Patterns, 15 Lanes, 270 routes/Provider entries, one accepted 64-unit dispatch, 64 primary
  plus 32 byproduct delivered, one planner call, one submitted job, one reload, two busy CPU ticks, and zero final retained,
  return-inventory, and retry-scheduler state. This capture is diagnostic only; do not promote it before T-S04/T-S06 and
  participating target-Grid work are green.
- The T-S04 quantity ladder isolated two independent scale boundaries. A 3,840-diamond request using a 256-output shared
  Pattern remained in AE2 planning even when the catalog exposed only that Pattern; a 256-diamond request completed
  planning immediately. The scene's `target=121920` diagnostic is aggregate cobblestone in all 15 Endpoint target Grids,
  not the quantity passed to `beginCraftingCalculation`, which receives `plannedOutputUnits` directly.
- A 256-output dispatch cannot be returned as one item-capability `ItemStack`; the Endpoint capability accepted stack-sized
  chunks across multiple native return slots. After that transfer succeeded, the direct ME Chest requester destination
  accepted only 64 units. Switching to a Grid aggregate allowed the crafting link to finish, but a result arriving after
  requester reload observed zero accepted units because the captured aggregate was stale. Persisting benchmark counters and
  rebinding the aggregate did not repair that post-reload accounting. Those experimental edits were reverted.
- The restored 64-unit workload again completes native and Federation GameTest scenes. The canonical task currently stops
  only at the intentionally stale evidence consumer: actual `assertions=40, operations=4, inserted=128, extracted=128`
  versus expected `assertions=24, operations=14`. Do not update that consumer until T-S04 and T-S06 are implemented.
- T-S04's 256/16 quantities are processing input volume, not the item count of one encoded Pattern output. Modeling one
  large call as 256 cobblestone -> 64 diamonds + 32 gold completes planning and execution in both scenes without transfer
  shims; the diagnostic evidence reaches the stale consumer with `inserted=512, extracted=512`. Therefore large mode must
  schedule 15 separate 256-input jobs (one per Lane) for 3,840 total input units, while small mode schedules 240 separate
  16-input jobs. Do not request one 3,840-item output or encode a 256-count item output.
- The large variant now runs 15 separate planner/CPU jobs against the one real CPU. Before each calculation, shared Pattern
  slot 255 is remapped through the production `MappedPatternProvider` API to exactly one successive Lane; after all jobs,
  its all-15-Lane mapping is restored before topology counting. Diagnostic evidence at
  `.omo/evidence/task-20-large-15-lanes/attempt-20260916T004725444Z/benchmark-native.properties` records identical native
  and Federation values: `acceptedInput=3840`, `acceptedPushes=15`, `plannerCalls=15`, `submittedJobs=15`,
  `machineCompletions=15`, `dispatchedLanes=15`, and `fairnessSpread=0`.
- Multi-job scene verification must total byproducts and planner/submission counts across `measurementSamples`; it must also
  retain the UUID of the first submitted job because that is the requester link persisted and reloaded. Deliberate GameTest
  wait branches now return explicitly, and native comparison asserts that its scene kind is actually `NATIVE`.
- The current schema has only aggregate scene metrics, so it cannot honestly prove large/small equal-volume parity. Add
  distinct large-variant and small-variant observations/results before implementing the 240 x 16 run and the
  `Task 20 T-S04 equal-volume mismatch` verifier. Do not merge both runs into one aggregate accepted-input counter.
- The small execution primitive was subsequently validated independently by temporarily selecting 240 samples of
  16 cobblestone -> 4 diamonds + 2 gold and cycling `jobIndex % 15`. Evidence at
  `.omo/evidence/task-20-small-240-lanes/attempt-20260916T005102800Z/benchmark-native.properties` reached only stale
  accounting with `operations=482`, `inserted=7680`, and `extracted=7680`, proving both native and Federation scenes
  completed 240 authentic planner/CPU jobs. The canonical profile was restored to the large variant afterward; the next
  implementation must execute and preserve both result sets in one v2 evidence artifact.
- Final v2 execution runs four serial scenes in one GameTest: Federation/native large (15 x 256 input) and
  Federation/native small (240 x 16 input). Both variants accept exactly 3,840 input units per scene, dispatch all 15
  Lanes with fairness spread zero, and use one real AE2 CPU with one native planner/submission per sample.
- T-S06 evidence comes from native owner state: CPU busy ticks, `LOCK_UNTIL_RESULT` Lane observations, rejected target
  attempts, no-progress return attempts, peak/final retained buffers, active return owners, and explicit proof that no
  retry scheduler exists. Do not restore the removed `retrySchedulerSize` label; it was active-owner state, not a queue.
- Canonical source-bound evidence is
  `.omo/evidence/task-20-v2-final-rebound/attempt-20260916T011433814Z/result.json`. The exact Task 20 negative QA is
  `.omo/evidence/task-20-selftest-v2/attempt-20260916T011723319Z/result.json`; the Task 19 regression is
  `.omo/evidence/task-19-task20-regression/attempt-20260916T012425313Z/result.json`.
- Three pre-baseline v2 captures matched across all 134 non-timing/non-hash properties. After that deterministic gate,
  the v2 baseline and budgets were explicitly rebound. Canonical benchmark verification, expanded adversarial mutations,
  focused contracts, Task 19 regressions, strict build, sources JAR, shared-JAR isolation, pure-LOC, diagnostics, and
  runtime cleanup all pass.
- The original closure repair used `source-authority.json` and a normalized `benchmarkSourceSha256`; the revised plan
  supersedes that design. Blocking verification now uses repository-state identity and no manual transitive source list,
  filename heuristic, or source LOC ceiling. The baseline uses non-self-referential `captureIdentitySha256`.
- T-S06 now persists the exact seed-ordered typed scenarios `busy`, `result-locked`, `rejecting`,
  `return-congested`, and `eligible`. Each cohort owns three unique Endpoint/Lane identities; the canonical eligible
  cohort records three completions, fairness spread zero, and event-window starvation bound one.
- Final fresh evidence was verified before required ad hoc-evidence cleanup: canonical attempt
  `20260916T083143828Z`, alternate-seed attempt `20260916T083333821Z`, and exact negative-QA attempt
  `20260916T083406861Z`. The alternate seed preserves scale/conservation while changing all four native/Federation
  topology/schedule digests. The Task 20 self-test rejects 55 fully rebound probes, including a serialized real
  alternate-seed GameTest producer mutation; fresh Task 17, 18, and 19 producer/consumer/self-test matrices also pass.
- T-S06 scenarios now control runtime behavior rather than labels alone: only the rejecting scenario receives the
  targeted full-input rejection window, while return-congested defers the real native return-owner wake for one tick.
  The resulting Federation scene records 270 return attempts and 255 retries versus native 225/210, and tracker
  finalization rejects any cohort missing its scenario-owned busy, result-lock, rejection, congestion, or eligibility
  evidence.

## 2026-09-16 revised harness repair

- Explicit evidence has no fixed 900-second expiry. Ordered, non-future timestamps remain structural requirements, while
  acceptance depends on source revision, modified/untracked regular-file bytes, dependency lock, product JAR, and artifact
  hashes. `.omo/**`, build outputs, and task runtime directories are documented repository-identity exclusions.
- Default producer flow verifies `attempt-${runId}` directly through `selectCurrentAttempt`; it does not scan prior
  attempts. The focused old-evidence contract validates a two-hour-old envelope because its identities still match.
- Process execution records `executionTimedOut`, `shutdownTimedOut`, `mainExit`, `survivingDescendants`, execution deadline,
  and shutdown grace independently. Linux launches use a dedicated process group so a child remains attributable even
  when a zero-exit shell disappears before `ProcessHandle.descendants()` can observe it.
- Focused characterization plus six contracts cover modified tracked bytes, untracked bytes, `.omo/**` exclusion,
  baseline self-reference prevention, current-attempt selection, execution timeout, delayed clean shutdown, and zero-exit
  surviving descendants. The real three-case `federationVerify` path persisted both expected timeout outcomes.

## 2026-09-16 bounded review repair

- The revised plan keeps Task 20 timing as `environment-sensitive-secondary` evidence only. Timing medians, p95/p99,
  retained-heap gates, and 110% regression thresholds belong to Task 37; no such gates remain in the Task 20 baseline or
  budgets.
- The full old-evidence contract now copies a real Task 20 benchmark attempt, moves its timestamps back 24 hours, rebinds
  only run/path identity, and sends it through the complete current verifier with source, dirty-worktree, dependency,
  product JAR, profile, budget, and artifact hashes intact.
- Porcelain-v1 `-z` rename/copy parsing treats the first path as the current destination and hashes destination bytes. A
  synthetic `R`/`C` characterization mutates each destination independently and observes a changed dirty identity.
- Schema-v3 reports require `childLifecycles` to match `childExits`. Task 20 child names have workload-derived deadlines,
  shutdown grace is positive and bounded, and timeout flags, main exits, survivors, and expected labels are checked.
- Process launch now requires `/usr/bin/setsid` and `/proc` before starting a child. Unsupported-isolation testing uses a
  missing executable seam and proves the immediate zero-exit/background-child command never launches.
- `harness.reject-stale` now uses current valid identities and independently mutates source revision, dirty-worktree hash,
  dependency lock hash, missing artifact, and artifact content. Timestamp age is not a staleness condition.
- Resource evidence uses semantic fields: `startingInput=4096`, `offeredInput=3840`, `acceptedInput=3840`,
  `finalInputInventory=256`, `nativeRemainder=0`, and `finalRetained=0` for both layouts. The enforced equation is
  `startingInput=acceptedInput+nativeRemainder+finalRetained+finalInputInventory`.
- Final benchmark attempt: `.omo/evidence/task-20/review-repair/final-benchmark/attempt-20260916T131256290Z/result.json`.
  Final five-case attempt: `.omo/evidence/task-20/review-repair/final-five-case/attempt-20260916T131733954Z/result.json`.
  Final stale-identity attempt: `.omo/evidence/task-20/review-repair/final-stale-case/attempt-20260916T131705053Z/result.json`.
- Final baseline SHA-256 is `4b05441a26984dc3e693c1b31a5a072d7bbc87ca7cfca482329d8ad7e8d2ecc0`; budget
  SHA-256 is `9c862bb0c1e218ecbfe54904f9b9aa33b9e8614515807c50c31e982aabbe7867`; profile SHA-256 is
  `224e7ce6201b54116622d341eeef51fde2f5db67ba572410078d8681397e869d`.
