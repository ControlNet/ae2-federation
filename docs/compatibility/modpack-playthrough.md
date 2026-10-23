# Modpack playthrough

The long-term test will produce quest items through the pack's real recipes with unlimited raw materials and AE2
networks joined by AE2 Federation. A single AE2 network will run the same targets as a control for Federation bugs.

Phase 0 builds no factory. The optional `quest-coverage` group reads FTB Quests SNBT and the running server's recipe
manager after KubeJS changes, then measures reachability with crafting, AE2 inscriber/charger, and vanilla cooking.

```bash
python3 tools/compat_run.py --accept-eula --tests questCoverage nast
# Equivalent explicit group selection:
python3 tools/compat_run.py --accept-eula --tests quest-coverage nast
```

Expected result: `PASS nast 1/1`. Writing both reports determines success; coverage does not. Reports are
`neoforge-1.21.1/build/compat/runs/nast/quest-coverage.json` and `quest-coverage.md`, beside `compat-report.json`.
Read `server-console.log` there if the run fails. The group is not in the profile's default tests or the runner's `all` expansion.

The Markdown headline uses the **provided** view. It supplies items in the configured mineral, wood and crop tags,
possible natural-block and entity loot, and closed recipe cycles that require an initial stock. A natural block has
no item or no known recipe producing its block item. Its loot is supplied, including crops and world-generated
containers. Ore/budding/cluster loot is retained only for drops absent from natural-block loot, with those exceptions
listed. Reports include natural-block counts and leading contributing mods. Rules live in
`QuestCoverageResources.java`; both reports give each rule's item and target counts, missing/empty tag names, loot
problems, and provided targets. Loot tables come from the server's reloadable registries. Entity loot includes loaded
`entities/` tables and loaded default tables of registered entity types. Enumeration includes conditional item/tag
entries, nested tables, and standard smelting/enchanting outputs without random sampling; custom entries are gaps.
A closed cycle is a strongly connected component for which every readable producer still needs a member of the
component: no recipe can satisfy every ingredient slot from outside it.

Tag-matched quest targets stay eligible for production, including alloys and dusts. We choose the **both headings**
policy: an item can be listed as provided and as producible, but a production count always requires a recipe proof.
Do not add those overlapping counts. The **strict** view preserves the original rule that only items with no readable
producer are base. Schema version 2 stores both under `views`; the top-level summary uses provided resources.

Blocker analysis covers targets neither provided nor produced at crafting + AE2 + cooking. It selects the cheapest
chain by unreachable item occurrences, then recipe steps, using exact integer costs and deterministic ties. Unreadable ingredients are
optimistic opaque leaves for this ranking, never production proofs. Item blockers and recipe-class demand count each
target once on its selected chain. Class demand can reveal combinations that the separate greedy ranking misses;
it does not assert that a class is necessary across every alternative recipe. The Markdown also lists the five most
frequent blockers for each chapter, counting distinct chapter targets on those chains.

The adapter ranking is an optimistic counterfactual: enable all readable recipe classes and credit known or explicitly
serialized unreadable outputs as producible. Rank a type by targets lost when that type alone is withheld, with the
other adapters still assumed available. All comparisons use a separate producer universe that includes explicit
opaque-output hints. Those items lose the no-producer assumption there; independently justified tag, loot and cycle
supplies remain. Normal coverage retains its original base policy. Also report its standalone gain, direct
sole-producer evidence, and downstream blocker impact. This handles compression cycles without pretending to read unknown inputs. Output hints never enter
normal reachability; recipe IDs and mod ownership are not output evidence. Unattributed outputs are counted separately,
since exact producer exclusivity cannot be established without adapters. These estimates are not execution proofs.

The JSON retains dependencies, task SNBT and recipe alternatives. Tasks and rewards are separate; filter/tag tasks
and missing-item markers, invalid/unregistered IDs and air are excluded from plain targets. English titles are
preferred, with labeled Chinese/ID fallbacks. Missing language/group files are empty metadata. A missing quest root or chapter directory is explicitly
reported as unavailable, distinct from an available book with zero targets. JSON keys and opaque evidence are
ordered for reproducible comparisons. FTB commas and empty compound keys are normalized for vanilla `TagParser`;
empty keys are then restored.

Reachability is limited to 64 recipe steps. Quantities, components, fluids, chemicals, power, catalysts, machine
conditions and quest prerequisites are not verified. Empty generic ingredient lists are not interpreted as free
recipes. Hidden outputs make no-producer bases provisional. This is planning evidence, not proof of AE2 execution.

Natural-block detection uses readable recipe outputs, so hidden producers can overestimate supplies. The JSON records
each cyclic component and its entering recipes for auditing; a press-copy recipe does not enter a component merely
because it consumes iron, since every slot must have an outside alternative.

## Phase 1: serial crafting factories

The opt-in `playthrough-crafting` group (`playthroughCrafting`, excluded from `all`) reuses Phase 0's quests,
producer proofs and provided resources. Each quest task is ordered in dependency order, first in plain AE2, then
in a Switch-connected materials/factory/requester federation. The requester imports materials storage and factory
crafting/storage; materials powers the other networks through ME power rules. Both layouts use identical patterns,
256k storage cells, nine patterns per provider, and Molecular Assemblers with five acceleration cards each.
Replicated copies of each provider/assembler group allow independent crafts to run concurrently within a serial order.
After planning, a native rectangular CPU is built with enough 256k crafting storage blocks for `plan.bytes()`
and at least 32 co-processors; execution waits for that CPU to form.
Each target gets fresh layouts, so failed jobs and recipe surplus cannot supply the next target.

```bash
# Three-target smoke:
JAVA_TOOL_OPTIONS='-Dae2federation.playthrough.maxTargets=3' \
  python3 tools/compat_run.py --accept-eula --tests playthroughCrafting nast
# Full AE2 chapter, including its addon tasks:
JAVA_TOOL_OPTIONS='-Dae2federation.playthrough.chapter=ae2 -Dae2federation.playthrough.maxTargets=0' \
  python3 tools/compat_run.py --accept-eula --tests playthroughCrafting nast
# Crafting chains without machine stand-ins:
JAVA_TOOL_OPTIONS='-Dae2federation.playthrough.mode=crafting-only' \
  python3 tools/compat_run.py --accept-eula --tests playthrough-crafting nast
```

Properties use the `ae2federation.playthrough.` prefix:

| Property | Default | Meaning |
|---|---|---|
| `chapter` | `ae2` | Chapter ID or title substring; `ae2` names `应用能源2`, empty selects all chapters. |
| `maxTargets` | `16` | Eligible crafting tasks after filtering; `0` removes the count limit. |
| `skipTargets` | `0` | Skip this many eligible tasks in dependency order, before applying `maxTargets`. |
| `items` | empty | Optional comma-separated item IDs, applied before task offsets and limits. |
| `mode` | `crafting-frontier` | Or `crafting-only`, using the same provided-resource policy. |
| `targetTicks` | `1200` | Planning/publication budget and base execution budget in ticks. |
| `ticksPerCraft` | `20` | Execution budget factor: `targetTicks + ticksPerCraft * ceil(totalCrafts / assemblerCopies)`. |
| `sprint` | `true` | Vanilla tick sprint while this isolated playthrough runs; restored on completion, deadline or exception. Other compat groups keep normal ticking. |
| `wallSeconds` | `1400` | Active-stage wall-clock deadline, 1–1400 seconds; the runner also reserves 60 seconds before its process deadline. |
| `assemblerCopies` | `28` | Maximum identical provider/assembler copies (1–28), reduced to fit 28 providers total. Actual copies are the conservative per-recipe parallelism in the budget formula. |

Execution is serial and isolated with `@RunsAlone`. Construction has 400 ticks; an unbuilt layout fails the harness.
The wall-clock deadline is checked during every active stage, independently of tick budgets and TPS. On expiry,
the current pair is checkpointed as `incompleteTarget`, cleaned up and deferred, with neither layout assigned a
result class. JSON records `nextEligibleOffset` and `nextTask`; resume that offset, preferably with a smaller chunk
so the interrupted target starts early. Construction/teardown and report writes are synchronous; an indefinitely
blocked server thread cannot be interrupted by an in-thread deadline. The runner's process deadline is the final bound.

Checkpoints use sibling temporary files, fsync and atomic replacement. JSON is authoritative; JSON and Markdown
carry matching `checkpointId` values. A mismatched Markdown ID means the rendering is stale, not that the JSON
continuation offset should be guessed. Preserve reports before reusing a run directory.

Run the current 120-task AE2 chapter as three parallel chunks (each process orders its own targets serially):

```bash
python3 tools/compat_run.py --accept-eula --tests playthroughCrafting nast \
  --run-suffix phase1 --chunks 3 --chunk-size 40 -j 3 \
  --jvm-property ae2federation.playthrough.chapter=ae2 \
  --summary build/playthrough/chunk-summary.json
python3 tools/compat_playthrough.py --output build/playthrough \
  neoforge-1.21.1/build/compat/runs/nast-phase1-chunk1 \
  neoforge-1.21.1/build/compat/runs/nast-phase1-chunk2 \
  neoforge-1.21.1/build/compat/runs/nast-phase1-chunk3
```

For N chunks choose `--chunks N --chunk-size ceil(120/N) -j N`; the last chunk can be short.
`--run-suffix` isolates directories, and repeatable `--jvm-property KEY=VALUE` passes properties only to those servers.
Without these options the runner's existing CLI behaviour is unchanged. The merger rejects missing/duplicate tasks,
settings mismatches, incomplete runs and inconsistent passing evidence. Keep single-target confirmation runs separate.

Frontier mode tries crafting routes first and explicitly supplies required non-crafting intermediates as **stand-ins**.
A conversion cycle cannot create its seed: NAST's glass decompression still needs cooking. Only items with an
observed non-crafting producer qualify. Each target lists its stand-ins and producer classes;
`PlaythroughRecipes.MACHINE_BACKED_CLASSES` follows the Phase 3 adapter registry; set `machines=none` to replay this frontier unchanged. The source serves the complete provided set plus those stand-ins.
AE2 excludes the requested output during planning; the harness also checks that the plan actually schedules a
pattern producing it. Concrete ingredient grids must pass the loaded recipe's
`matches` and AE2 pattern decoding; unsupported chains receive encoding reasons.

`playthrough-crafting.json` and `.md` are checkpointed beside `ae2federation.compat.report`. Harness success means
both reports were written; target failures remain results. Chapter/overall summaries count quest tasks, including
repeat items, in four classes: `both-pass`, `baseline-only` (a likely Federation bug), `both-fail`, `federated-only`.
Targets whose own recipe is not crafting are listed separately for Phase 3. `not-bootstrappable` is a separate
non-execution category, outside the four layout result classes: the shared Phase 0 resolver cannot prove a finite
crafting route with the target removed from supplied resources, but adding only a seed of the target enables a
producer. This detects direct and indirect self-seeding recipes without item-specific exceptions.
`nondeterministic` is also outside the four result classes: repeated assembly, decoding, installed-pattern or
cross-layout signatures differ. Signatures contain exact component-bearing output keys/counts and remainder
keys/counts. Three initial samples and checks in both installed layouts detect observed variation; they cannot
prove that arbitrary mod recipes are deterministic under every possible state.

Each layout records ticks, every post-order invariant, failure reason, per-network target stock, planned used items
and actual source extractions. `expectedInventories` is the complete material balance: plan used items plus all
pattern outputs and remainders, minus all pattern inputs, multiplied by execution counts. The requester has the
highest storage priority; materials/factory cells must be empty and the requester must contain that exact balance.
`inventories` records every key in every physical cell; the pair also compares those inventories directly.
All debt, held-stock and transit entries for any fixture network are read through the ledger's public saved-data
snapshot, including unexpected keys. Teardown records residual entries before removing fixture-owned debt/transit
through compat-side reflection, lets the normal service retire watched jobs, then verifies no references remain
after a 25-tick settling interval. Final order checks also wait 25 idle ticks to cover the complete 20-tick
return sweep. Main-mod code is unchanged.

The report also records `expectedAmount`, `totalCrafts`, `planBytes`, `cpuBytes`,
`assemblerParallelism` and `tickBudget`. Passing requires the plan's net target output in requester storage, an idle CPU, empty
provider/assembler buffers, exact extraction accounting, zero return debt/transit for all fixture keys, and
complete final inventories matching the plan and the other layout. Expected target output is the sum of every target output in `patternTimes()`, less all target inputs
consumed by those patterns, multiplied by their execution counts. Batch surplus is collected and drained with the
requested items. Timeout reasons include plan bytes, crafts, parallelism and budget for diagnosis. Harness and crafting-future
exceptions fail the test; other server log exceptions are not intercepted. Read `server-console.log` for diagnosis.

The focused `playthroughRemainders` regression runs a real `minecraft:cake` recipe through the same driver,
including its bucket returns. Its report is separate from quest chapter counts.

```bash
python3 tools/compat_run.py --accept-eula --tests playthroughRemainders --run-suffix remainders nast
```

## Phase 2: seeded topology matrix (partial)

`playthroughTopology` / `playthrough-topology` reuses the Phase 1 driver, factory, recipes, source, accounting and
atomic checkpoints. Defaults select `ae2:blank_pattern`, `functionalstorage:oak_1` and `minecraft:crafting_table`
from three chapters, each independently confirmed with Phase 1. Five seeds run six disturbances; two additional
fixed fixtures exercise cable demand 8 and 9. The default 1400-second active budget remains inside NAST's 1800-second
process limit. Longer ranges are opt-in and can end with an explicitly incomplete checkpoint.

```bash
python3 tools/compat_run.py --accept-eula --tests playthroughTopology --run-suffix topology nast
# Reproduce just one generated case, including its automatic fresh-fixture confirmation on failure:
python3 tools/compat_run.py --accept-eula --tests playthroughTopology --run-suffix topology-single nast \
  --jvm-property ae2federation.playthrough.items=ae2:blank_pattern \
  --jvm-property ae2federation.playthrough.topology.seedStart=0 \
  --jvm-property ae2federation.playthrough.topology.seedCount=1 \
  --jvm-property ae2federation.playthrough.topology.capacityCases=false \
  --jvm-property ae2federation.playthrough.topology.perturbations=router
```

Properties below have prefix `ae2federation.playthrough.topology.`. `seedStart` defaults to 0 and `seedCount` to 5.
Dimensions are data in `PlaythroughTopology.DIMENSIONS`; pin `networks` (2–6), `join` (`router-switch`, `multi-hop`,
`bundle-2x2`, `bundle-3x3`, `beam`, `p2p`), `power` (`WEST`, `EAST`, `NORTH`), `splitPatterns` or `extraCpus`.
The last two require at least four member networks when pinned true. P2P has a separate powered carrier, excluded
from the member-network count. Two-network layouts physically join materials and requester with native cable.
Split patterns alternate recipe slots between east and south factories; a single-pattern proof instead distributes
its copies. Extra native CPUs are present on additional networks; the job still uses the requester's CPU.

`perturbations` defaults to `none,cable,router,STORAGE,CRAFTING,ME_POWER`. A nonempty return debt while the CPU is
busy proves an accepted push before firing. Blocks/rules are restored after 40 ticks; checks wait for power and CPU
visibility and at least 60 further ticks. Each case expects completion after restoration, with full cells,
extractions, signatures, idle CPUs, empty buffers and zero debt/transit. A disturbance that never fires fails.
Failures receive a fresh factory with the same layout and target; reports retain both attempts and distinguish a
reproduced invariant, a different failure, and a passing confirmation (`flaky`). This is an in-process confirmation,
not a fresh-server flakiness test.

The fixed boundary fixtures use three networks, multi-hop joins and west power, independent of ordinary dimension
pins. Demand 8 must craft normally. Demand 9 must keep the domain stopped and refuse projection despite an installed,
locally available pattern: no job, extraction, cell stock or ledger entry. It records perturbation `none` and does not disturb an unstarted job. Use `capacity=at-limit` or `capacity=over-limit` with
`seedCount=1` to replay a boundary fixture alone; `capacityCases=false` suppresses automatic boundary additions.
Seeds congruent to 5/6 modulo 7 select boundary fixtures, so the same seed replays the same kind. Automatic boundary
additions select the next matching seeds beyond the requested range. The boundary geometry requires the branch's
enforced default capacity of 8. `--chunks N --chunk-size S` partitions topology seed ranges (Phase 1 still partitions
target offsets); each process includes its boundary fixtures. Build once before concurrent `--no-build` invocations.

`remainders=true` appends the cake regression; it is excluded by default. Reports `playthrough-topology.json` and
`.md` sit beside the compat report, with case evidence and summaries by dimension and perturbation. A harness
`PASS nast-… 1/1` means the report completed, not that every case passed. `completed=false` means incomplete coverage.

This implementation does **not** finish Phase 2: material/requester/CPU role reassignment, a standalone Router
layout, Federation Provider/Endpoint and Local Endpoint crafting paths, cross-dimension Quantum Cubes, Bridge
removal, actual chunk unload/reload and the existing restart protocol are not integrated. JSON lists these gaps;
no placeholder cases count as coverage. The separate existing compat scenes are not evidence for this matrix.

## Phase 3a: machine adapters

`playthroughCrafting` now enables `inscriber`, `charger`, `furnace`, `smoker` and `blast-furnace` by default.
`ae2federation.playthrough.machines` accepts a comma list of those IDs; `none` restores Phase 1's stand-ins.
Selection uses exact recipe types, so enabling a smoker does not also enable Phase 0's other `cooking` types.
Adapters can discover outputs hidden from Phase 0 and replace unreadable generic ingredients with concrete proofs.
Unsupported recipes remain stand-ins with reasons in `machineRejections`. Processing patterns retain concrete
amounts and must round-trip through AE2. Machine-backed stand-ins are removed from the unlimited source; Phase 0 provided resources remain available.

Each recipe has its own machine and input subnet. Inscriber presses are fixed equipment from Phase 0's provided
set; their exact stacks must remain unchanged. Other inputs, including cooking fuel, come from processing patterns.
Two sticks fuel a normal furnace/smoker/blast-furnace recipe through a side Storage Bus filtered to sticks; the top bus is filtered to the recipe ingredient.
Quartz Fiber shares the layout's one creative AE energy source with native subnets. Hoppers return products to the
provider. No test tick transfers ingredients or products. Up to four copies of each machine run concurrently,
limited to 24 machines per layout (`machineCopies`, range 1–24). Unsupported cooking durations are reported.
The GameTest and sprint share a 20-million-tick ceiling; the existing wall-clock deadline still bounds each run. Prefer 20-target chunks for machine-heavy chapters.

`machinePath=projection` uses native Pattern Providers on the factory network and processing-pattern projection.
`machinePath=endpoint` puts Federation Providers on that network, mapped to individual machine Endpoints.
Both use a fresh plain AE2 processing control. Reports retain the four-way class per path, all Phase 1 checks,
and machine/catalyst, collector, interface and provider buffer checks. Inputs and outputs for each real machine
step are listed per target. `playthrough-machines.json` and `.md` are checkpointed beside the crafting reports.

```bash
# Build once, then run the paths sequentially; never exceed two NAST servers in this worktree.
./gradlew :neoforge-1.21.1:jar :neoforge-1.21.1:compatTestJar
for path in projection endpoint; do
  python3 tools/compat_run.py --accept-eula --tests playthroughCrafting nast \
    --no-build --run-suffix "machines-$path" --chunks 7 --chunk-size 20 -j 2 \
    --jvm-property ae2federation.playthrough.chapter=ae2 \
    --jvm-property "ae2federation.playthrough.machinePath=$path" \
    --summary "build/playthrough/$path-summary.json"
done
python3 tools/compat_playthrough_machines.py --output build/playthrough \
  neoforge-1.21.1/build/compat/runs/nast-machines-{projection,endpoint}-chunk{1,2,3,4,5,6,7}
```

Add an adapter by implementing `PlaythroughMachineAdapter` in one new class and adding one constructor entry to
`PlaythroughMachineAdapters.REGISTERED`. Use `read` for exact `GenericStack` inputs/outputs, `place` for real block
setup and `leftovers` for idle/empty and catalyst checks. `Site` tracks placement and exposes the return port;
non-item adapters can replace the hopper with real pipes and declare justified raw keys via `providedInputs`.
Fluid outputs receive an AE2 fluid cell; chemical adapters declare their pack cell IDs through `storageCells`.
Those physical requester cells participate in the same full inventory balance. Non-item adapters still need their own runtime validation; this phase exercises items only. No main-mod code is changed.
