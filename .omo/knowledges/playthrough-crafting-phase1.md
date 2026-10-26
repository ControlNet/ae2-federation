# Phase 1 crafting playthrough harness

- `QuestCoverageCompatGameTests.Probe` and its quest records are package-visible so execution reuses Phase 0's
  quest dependencies, producer index and `QuestCoverageResources` provided set.
  `QuestCoverageAnalysis.productionProofs` exposes the same breadth-first reachability with its first finite producer
  for each output. Phase 0 still uses its key set; Phase 1 uses the producer witnesses to avoid conversion-cycle searches.
- The opt-in `playthrough-crafting` group / `playthroughCrafting` method is excluded from `all` and uses `@RunsAlone`.
  Filter production server runs with `JAVA_TOOL_OPTIONS` properties under `ae2federation.playthrough`.
- A real ME Chest cell on the requester needs higher storage priority than materials/factory cells. Equal priorities
  let completed jobs store output in another cell; that is a fixture problem, not evidence of failed Federation returns.
  The first three-target smoke reproduced this; priority 1000 made all three pass in both layouts.
- `PlaythroughSource` mounts an extraction-only `MEStorage` through a test-owned managed node and `IStorageProvider`.
  Modulated extractions are counted by complete `AEKey`; simulated reads are not counted. The source serves the
  complete provided set, including provided quest targets. AE2 ignores the requested output when planning; an
  explicit `patternTimes` check also requires a pattern that produces the target.
- Every target uses fresh factories. Recipe batch surplus in cells is discarded with the fixture and cannot supply
  the next target. The expected holding includes the net batch surplus calculated from the plan; surplus alone
  must not classify a target as a crafting failure.
- Tag alternatives should prefer provided leaves before recursively exploring crafting variants. Trying every deep
  alternative before a known supplied input can exhaust a bounded search on this pack's conversion recipes.
- NAST `minecraft:glass` has a crafting producer (`allthecompressed:decompress/glass_1x`) and cooking. Its
  compression/decompression route needs a seed, so merely testing "has any crafting producer" incorrectly blocks
  all downstream quartz glass. Frontier resolution tries finite crafting routes, rejects seed-dependent conversion
  proofs and supplies that machine-produced seed explicitly. The target itself is never a supplied seed.
- The Phase 0 regression after sharing producer proofs retained 422 crafting and 633 crafting+AE2+cooking targets,
  out of 1238 distinct targets (`PASS nast 2/2` with the 16-target playthrough).
- The full evidence and validation commands belong in `build/playthrough/phase1-handoff.md`; the tracked guide is
  `docs/compatibility/modpack-playthrough.md`.
- AE2's `CraftingCalculation.runCraftAttempt` calls `craftingInventory.ignore(this.output)` before requesting the
  crafting tree. A target cannot bootstrap its own copying recipe from unlimited copies of that same output.
  `ae2:quantum_entangled_singularity` is an observed example: Phase 1 records it as `not-bootstrappable`,
  outside the four executed-result classes.
- `skipTargets` resumes the ordered eligible crafting-task list. Add it to `completedTargetCount` for the next
  chunk. The 1400-second guard leaves time for NAST's 1800-second process limit; preserve reports before rerunning
  because the compatibility runner rebuilds its run directory.
- Before review, AE2-chapter evidence covered all 120 eligible crafting quest-task pairs and 62 non-crafting skips: 91 both-pass,
  29 both-fail, zero baseline-only or federated-only failures. Shared failures were 15 exact-count batch surpluses,
  12 tick-budget failures, one CPU-capacity refusal and one missing finite crafting chain. Runs were split at
  task offsets 0, 79 and 113; the guard finishes both layouts before stopping.
- All 17 targets that are themselves Phase 0 provided items were rechecked with the complete provided source set.
  Every plan scheduled target crafting and none extracted its existing target stock. The other 103 source inventories
  were unchanged. The pre-review strict-mode run retained five both-pass and eleven both-fail results, with no stand-ins.

## Review corrections: plan output and capacity

AE2 rounds crafting outputs to recipe batches. The Phase 1 expected holding must be the target's outputs across
`ICraftingPlan.patternTimes()` minus that key's inputs consumed by those executions, not the quest count.
The encoded patterns disable substitution, so their concrete primary inputs and multipliers determine consumption.
Drain all planned surplus after checking it. Quartz fibre smoke evidence: requested 1, plan and both layouts 3.

Plan byte requirements are available before CPU selection. Native crafting storage blocks form a solid rectangular
CPU with co-processors; wait for the new CPU's storage and co-processor counts before submitting the existing plan.
The NAST `appflux:core_256m` plan needs 571625 bytes and 63971 pattern executions, exceeding one native 256k block.
Replicate the nine-pattern provider groups, keeping the controller spine at most seven blocks long (28 providers).
Each assembler has five acceleration cards. Parallelism is the number of copies of each recipe's provider group,
not the total assembler count, because a recipe is installed in only one provider per copy.

Keep the four executed-result classes separate from `not-bootstrappable`. Reuse Phase 0's production proofs with
the target absent from supplied resources, then add only the target seed: if that enables a producer where the
unseeded view has none, do not run that task as a shared crafting failure. Report the seed-dependent producer ID.


## Reviewed chapter evidence

The reviewed frontier chapter completed in two disjoint runs (first 96 tasks, then offset 96): 119 both-pass,
zero both-fail, zero baseline-only, zero federated-only, and one `not-bootstrappable` task
(`ae2:quantum_entangled_singularity`). All 120 original task IDs are present; 62 own non-crafting tasks remain
separate. Of the original 29 shared harness-limit failures, 28 now execute successfully and one is excluded as
self-seeded. Runs passed in 1448 and 1257 seconds, inside the 1800-second profile limit.

The 256m AppFlux component uses three native 256k storage blocks (786432 bytes). Its plan requires 571625 bytes,
63971 crafts and 14 assembler copies; its execution budget is 92600 ticks. The independent smoke passed in
1010 seconds, with 9142 baseline ticks and 9178 federated ticks for that target. Full chapter execution
independently confirmed both layouts. Reports retain each layout's measured ticks rather than substituting smoke
measurements. The final merger checks task identity, every invariant, used/extracted items, planned net output,
CPU capacity, budget formula and matching plan metrics between layouts.

## Complete inventory and return-state review

`PlaythroughAccounting` computes full final cell contents as plan used items + pattern outputs + ingredient
remainders - pattern inputs, with execution counts and exact component-bearing keys. The requester has priority
1000, so the complete balance belongs there; both other physical cells must be empty. Compare per-cell inventories
between layouts as well. Quartz fibre legitimately leaves two black stained glass in addition to its three outputs.

The public `CraftingReturnLedger.save` snapshot exposes every debt/held/transit key, watched job and unknown-job
marker. Filter by every fixture network identity observed during execution; never limit the keys to recipe outputs.
Capture residuals before test teardown. Compat-side reflection discards only fixture-owned debts/transit, then the
normal projection sweep retires settled jobs and its crafting-link cache. That sweep runs every 20 ticks: verify
cleanup after 25 ticks, and allow 25 idle ticks before final order invariants. Ten ticks caused premature cleanup
verification failures. No production source changes are necessary.

Recipe signatures preserve exact output and remainder key/count maps. Compare repeated assembly and decoding,
installed provider details, fresh decoding at completion, and both layouts. Observed disagreement is
`nondeterministic`, not one of the four differential execution outcomes. Sampling cannot prove arbitrary mod code
is deterministic in all future states.

Construction only validates and creates Java helpers; `initialize` mutates the level after the driver owns the
factory reference. Record positions before placement, attempt all cleanup operations, and preserve exceptions.
Checkpoints are sibling-temp + fsync + atomic rename, with JSON authoritative and matching Markdown checkpoint IDs.
They record next eligible offset/task and incomplete-pair evidence. Active-stage wall-clock deadlines defer without
classifying a partial pair. `compat_run.py` passes an absolute process deadline, reserving 60 seconds for cleanup.

Vanilla tick sprint is owned only by the isolated playthrough run and is restored on normal completion, deadline
and caught exception. The runner supports isolated suffixes, per-process JVM properties and parallel bounded chunks;
`tools/compat_playthrough.py` merges and validates complete coverage. Synthetic report-corruption tests are explicitly
separate from real gameplay evidence.

The real `playthroughRemainders` cake regression exposed a likely Federation return defect: identical signatures,
source counts, idle CPUs and empty machine buffers, but three empty buckets remain in the factory cell with owed=3
and held=3 while the cake and seven surplus eggs reach the requester. This persisted after a full return sweep and
in the normal-tick single-target confirmation. The NAST recipe chain crafts buckets before milk buckets, so buckets
are also installed pattern outputs; do not claim this particular case alone proves an old whitelist bypass. It
independently exercises full remainder inventory and debt accounting. Keep it separate from AE2 chapter counts.


The full six-finding review rerun used three concurrent 40-task chunks: 138, 158 and 289 seconds, all harness PASS.
All 120 chapter task identities and classes were unchanged: 119 both-pass, one not-bootstrappable, zero both-fail,
zero one-sided and zero nondeterministic. The separate cake regression remained baseline-only in final normal-tick
and sprint confirmations, with identical inventories and signatures; the 25-tick settlement did not resolve it.
Final small-set speed parity: 34.519 seconds normal versus 7.909 sprint for the test portion, 118 versus 98 seconds
including startup. A 22-second active-stage deadline preserved the incomplete federated pair at offset 1, and the
continuation completed the remaining tasks. The focused remainder test has a separate opt-in group to prevent
chapter reports being overwritten when selecting the chapter group. Both groups are excluded from `all`.
