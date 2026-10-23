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
