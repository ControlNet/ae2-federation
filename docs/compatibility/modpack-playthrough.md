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
`PlaythroughRecipes.MACHINE_BACKED_CLASSES` is empty until Phase 3 supplies real adapters. The source serves the complete provided set plus those stand-ins.
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

## Phase 2: seeded topology matrix

`playthroughTopology` reuses the Phase 1 driver, factory, recipes, source, accounting and checkpoints.
Defaults order `ae2:blank_pattern`, `functionalstorage:oak_1` and `minecraft:crafting_table` across five seeds,
six disturbances and two capacity fixtures. The active wall budget is 1400 seconds inside NAST's 1800-second limit.
The cake regression remains opt-in with `topology.remainders=true`; Endpoint paths exclude remainder recipes.

```bash
./gradlew :neoforge-1.21.1:jar :neoforge-1.21.1:compatTestJar --dependency-verification=strict --console=plain
python3 tools/compat_run.py --no-build --accept-eula --tests playthroughTopology --run-suffix topology nast
python3 tools/compat_run.py --no-build --accept-eula --tests playthroughTopology --run-suffix endpoints \
  --chunks 2 --chunk-size 1 -j 2 \
  --jvm-property ae2federation.playthrough.topology.path=federation-provider \
  --jvm-property ae2federation.playthrough.topology.networks=4 \
  --jvm-property ae2federation.playthrough.topology.capacityCases=false nast
python3 tools/compat_transit_restart.py --no-build --accept-eula --playthrough-topology --seed 0
```

Properties below use prefix `ae2federation.playthrough.topology.`. `seedStart` defaults to 0, `seedCount` to 5.
`PlaythroughTopology.DIMENSIONS` holds pin values: `networks` (2–6), `join` (`router-switch`, `multi-hop`,
`bundle-2x2`, `bundle-3x3`, `beam`, `p2p`, `quantum`, `bridge`, `router`), `power` (WEST/EAST/NORTH), `splitPatterns`,
`extraCpus`, `path` (`projection`, `federation-provider`, `local-endpoint`) and `roles` (six permutations of
material/factory/requester directions, such as `WEST/EAST/NORTH`). The seed permutes these three roles;
the native job CPU belongs to the requester, with optional extra CPUs on other members. Two-network layouts
join west/north by native cable and keep the separate factory east. Split patterns/extra CPUs require four members.
Generator version 2 records the role permutation in each layout; compare seeds within the same generator version.

All normal-capacity joins and crafting paths expect completion with Phase 1 accounting.
Projection remains the default path. Both Endpoint paths use real Molecular Assemblers programmed with the
original crafting patterns and processing patterns with identical inputs/outputs in their providers. The
Endpoint is the provider's adjacent target; its adjacent assembler executes the recipe. Each distinct recipe has
its own assembler, up to four in this fixture. Federation Provider checks inspect the executing Endpoint lane,
including its return inventory. Local mode uses a powered Endpoint subnet. Split recipes occupy separate factories.

The original six joins are sampled by default. Pin `join=quantum` for actual paired Quantum Cubes and a nether
factory through `OtherDimensionSite`; it requires projection, at least three networks, and an east factory.
Pin `join=bridge` for a native requester backbone with separate Bridge-linked members. Pin `join=router` for one Router with no Switches: Federation Pattern Provider front ports attach the native
members. A Router cannot attach native grids directly; these Providers publish the native boundary.
P2P's powered carrier is transport infrastructure outside the member count.

`perturbations` defaults to `none,cable,router,STORAGE,CRAFTING,ME_POWER`. An owed ledger entry while the CPU is
busy proves the first accepted push. Restore after 40 ticks, wait for power and CPU visibility, then use Phase 1's
25 idle ticks to cover the 20-tick return sweep. Every completed case checks full inventories, extractions,
signatures, buffers, CPU release and all ledger/transit entries. Pin the additional disturbances explicitly:

- `bridge`: on the Bridge join, remove/restore its cable host and Bridge together. Removing only the part exposes
  native cable and can merge the two grids. `bridge-part` tests that merge/split operation separately, with the
  same required zero-debt, output-retention outcome.
- `chunk`: on Quantum, release the factory site's tickets, require actual block-entity removal while the CPU
  remains busy, then reload after 40 unloaded ticks. Tick sprint remains enabled by default;
  `pauseSprintForUnload=true` runs the unload/reload window at normal tick speed for diagnosis. `orderAmount=4096` and the existing
  `ae2federation.playthrough.assemblerCopies=1` keep an unaccelerated assembler's job active. A failed unload is
  recorded as a result, not counted as coverage. `unloadSeconds` defaults to 120 (allowed range 1–300).
- `cancel`: use a single-craft target such as blank pattern. Cancel after the first push; require its accepted
  output at the requester, an idle CPU and zero debt/transit with the same full material balance.
- `restart`: use the command above. The existing runner drives `transitRestartPhase=prepare/recover` in two
  actual JVMs sharing a world. Preparation disconnects the Router after a push and preserves the original job
  with `RestartChunkTickets`; recovery restores the Router and validates the saved plan's exact balance without
  submitting a replacement job. Clean native cancellation must retain the same output balance and clear all debt. This focused case uses router-switch projection and a single-craft target.

The fixed capacity fixtures use three members, multi-hop joins and west power. Demand 8 must craft; demand 9
must refuse projection with no extraction, stock, job or debt. Seeds congruent to 5/6 modulo 7 select those
fixtures; `capacity=normal` overrides that for ordinary layouts. `capacityCases=false` disables automatic boundary
additions. Capacity fixtures ignore ordinary pins and require this branch's default limit of 8.

Reports `playthrough-topology.json` and `.md` contain dimensions, case evidence and dimension/perturbation
summaries. Harness PASS means the report completed; inspect classifications and `completed` separately.
Failed cases get an isolated same-seed confirmation, retaining both attempts. Ordinary cases use fresh fixtures
in one process; restart cases use a second fresh world and another process pair. `--chunks N --chunk-size S`
partitions seeds. Build once, then use `--no-build`; run at most four NAST servers at once (8 GB each).
Actual coverage and unresolved findings are recorded in `build/playthrough/phase2-handoff.md`.
