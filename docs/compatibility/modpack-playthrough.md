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
Each subnet bus sits on the side of its provider line away from the hub, clear of the member chest and power cell;
building asserts that every subnet is its own grid. A Federation Provider is ready once its Endpoint lane holds
exactly the expected pattern; a local provider once it holds it in Local mode.

The original six joins are sampled by default. Pin `join=quantum` for actual paired Quantum Cubes and a nether
factory through `OtherDimensionSite`; it requires projection, at least three networks, and an east factory.
Pin `join=bridge` for a native requester backbone with separate Bridge-linked members; extra CPUs are placed
as on the other joins. Pin `join=router` for one Router with no Switches: Federation Pattern Provider front ports attach the native
members. A Router cannot attach native grids directly; these Providers publish the native boundary.
P2P's powered carrier is transport infrastructure outside the member count.

`perturbations` defaults to `none,cable,router,STORAGE,CRAFTING,ME_POWER`. An owed ledger entry while the CPU is
busy proves the first accepted push. Restore after 40 ticks, wait for power and CPU visibility, then use Phase 1's
25 idle ticks to cover the 20-tick return sweep. Every completed case checks full inventories, extractions,
signatures, buffers, CPU release and all ledger/transit entries. `cable` cuts the factory arm's first block
(`center` plus the factory role's face) and `router` the hub; each restores exactly the block it removed, native
cable included (the hub is native cable on the Bridge join). Preconditions are checked before each case is built,
after the target's baseline: a case that cannot run on its layout or plan is a `skipped-precondition` row with its
reason, not a harness error. Pin the additional disturbances explicitly:

- `bridge`: on the Bridge join, remove/restore its cable host and Bridge together. Removing only the part exposes
  native cable and can merge the two grids. `bridge-part` tests that merge/split operation separately, with the
  same required zero-debt, output-retention outcome.
- `chunk`: on Quantum, release the factory site's tickets once the requester CPU holds no unpushed input, as the
  existing unload scenes do; require actual block-entity removal while the CPU remains busy, then reload after 40
  unloaded ticks. Until every input is pushed, AE2's push attempts read the factory blocks on the main thread and
  reload the chunk each tick. Sprint pauses for the unload window (`pauseSprintForUnload=false` keeps it), since
  vanilla drains its unload queue only in spare tick time or above 2000 entries. An unaccelerated assembler
  (`ae2federation.playthrough.assemblerCopies=1`) keeps the last craft in flight; `orderAmount=4096` remains the
  measured command. A timed-out unload, or a job that ended first, is `perturbation-not-executed`: outside the
  outcome summaries and not coverage. Read-only ticket/holder diagnostics are recorded at unload or timeout.
  `unloadSeconds` defaults to 120 (allowed range 1–300).
- `cancel`: use a single-craft target such as blank pattern. Cancel after the first push; require its accepted
  output at the requester, an idle CPU and zero debt/transit with the same full material balance.
- `restart`: use the command above. The existing runner drives `transitRestartPhase=prepare/recover` in two
  actual JVMs sharing a world. Preparation disconnects the Router after a push and preserves the original job
  with `RestartChunkTickets`; recovery restores the Router and validates the saved plan's exact balance without
  submitting a replacement job. Before the Router returns, the requester's CPU blocks must hold a busy job with
  the saved crafting ID (read from the CPU, since an unpowered requester lists no CPUs); otherwise the row is
  `job-lost-on-reload`, however cleanly it settles. The row then goes through the same baseline comparison as the
  others, and post-restart extractions are counted. Recovery releases the restart tickets also when it fails.
  This focused case uses router-switch projection and a single-craft target.

The fixed capacity fixtures use three members, multi-hop joins and west power. Demand 8 must craft; demand 9
must refuse projection with no extraction, stock, job or debt. Seeds congruent to 5/6 modulo 7 select those
fixtures; `capacity=normal` overrides that for ordinary layouts. `capacityCases=false` disables automatic boundary
additions. Capacity fixtures ignore ordinary pins and require this branch's default limit of 8.

Reports `playthrough-topology.json` and `.md` contain dimensions, case evidence and dimension/perturbation
summaries. `perturbation-not-executed` and `skipped-precondition` rows appear only under `notRunByPerturbation`.
Harness PASS means the report completed; inspect classifications and `completed` separately.
Failed and not-executed cases get an isolated same-seed confirmation, retaining both attempts; after a
not-executed first attempt, the confirmation is classified on its own (`first-attempt-not-executed`). Ordinary cases use fresh fixtures
in one process; restart cases use a second fresh world and another process pair. `--chunks N --chunk-size S`
partitions seeds. Build once, then use `--no-build`; run at most four NAST servers at once (8 GB each).
Actual coverage and unresolved findings are recorded in `build/playthrough/phase2-handoff.md`.

## Phase 3a: machine adapters

`playthroughCrafting` now enables `inscriber`, `charger`, `furnace`, `smoker` and `blast-furnace` by default.
`ae2federation.playthrough.machines` accepts a comma list of those IDs; `none` restores Phase 1's stand-ins.
Selection uses exact recipe types, so enabling a smoker does not also enable Phase 0's other `cooking` types.
An adapter's `recipeType()` is the registered type ID; Phase 0 selection and discovery both match it.
Adapters can discover outputs hidden from Phase 0 and replace unreadable generic ingredients with concrete proofs.
Discovery registers a hidden producer only when it passes the same acceptance checks under the resource view that
registering it produces, so rejected recipes never change the provided set.
Unsupported recipes remain stand-ins with reasons in `machineRejections`. Processing patterns retain concrete
amounts and must round-trip through AE2. Machine-backed stand-ins are removed from the unlimited source; Phase 0 provided resources remain available.

Each recipe has its own machine and input subnet. Inscriber presses are fixed equipment from Phase 0's provided
set; their exact stacks must remain unchanged. Other inputs, including cooking fuel, come from processing patterns.
Sticks fuel a furnace/smoker/blast-furnace recipe through a side Storage Bus filtered to sticks; the top bus is filtered to the recipe ingredient.
The stick count uses `getBurnTime` (NeoForge's fuel data map), halved for smokers and blast furnaces as their
`getBurnDuration` does. The furnace's own recipe lookup must select the recipe for the chosen ingredient, and a
stick ingredient is rejected so the two bus filters stay disjoint.
Quartz Fiber shares the layout's one creative AE energy source with native subnets. Hoppers return products to the
provider. No test tick transfers ingredients or products. Up to four copies of each machine run concurrently,
limited to 24 machines per layout (`machineCopies`, range 1–24). Unsupported cooking durations are reported.
A proof that needs more than 24 machine recipes or 12 extra requester cells fails that target as `pattern-encoding`.
The shelf stands at z=25–29, beyond every topology layout; any overlap with layout blocks fails while building.

Each target's budget is the base `targetTicks`, plus `ticksPerCraft` per assembler craft divided by assembler
copies, plus three times the sum over machine recipes of runs divided by that recipe's copies times its duration (or its
hopper time, 8 ticks per output item, if longer). A hung job therefore fails as `job-complete`.
The crafting, topology and remainder GameTests share the sprint and timeout ceiling `MAX_TICKS` (5 million ticks); the wall-clock
deadline still bounds each run. Prefer 20-target chunks for machine-heavy chapters.

`machinePath=projection` uses native Pattern Providers on the factory network and processing-pattern projection.
`machinePath=endpoint` puts Federation Providers on that network, mapped to individual machine Endpoints.
Both use a fresh plain AE2 processing control. Reports retain the four-way class per path, all Phase 1 checks,
and machine/catalyst, collector, interface and provider buffer checks. Each layout records the plan's runs of every
machine pattern (`cleanup.machineRuns`); machine providers also appear in `installedPatternsByNetwork`. A
target's `machineSteps` list inputs, outputs, runs per layout and `observedIn`, the passing layouts with runs above
zero. Only those steps count as real (`realMachineSteps`); a planned step with zero runs is not machine coverage.
`playthrough-machines.json` and `.md` are checkpointed beside the crafting reports. The merge tool rejects
observations that do not follow from recorded runs, differing recipe selections, and plain AE2 control runs that
differ between paths; it reports per-path adapter coverage (`adapterCoverage`) from observed steps only.

```bash
# Build once, then run the paths sequentially; never exceed two NAST servers in this worktree.
./gradlew :neoforge-1.21.1:jar :neoforge-1.21.1:compatTestJar
# Do not name the loop variable `path`: zsh ties it to $PATH.
for mp in projection endpoint; do
  python3 tools/compat_run.py --accept-eula --tests playthroughCrafting nast \
    --no-build --run-suffix "machines-$mp" --chunks 7 --chunk-size 20 -j 2 \
    --jvm-property ae2federation.playthrough.chapter=ae2 \
    --jvm-property "ae2federation.playthrough.machinePath=$mp" \
    --summary "build/playthrough/$mp-summary.json"
done
python3 tools/compat_playthrough_machines.py --output build/playthrough \
  neoforge-1.21.1/build/compat/runs/nast-machines-{projection,endpoint}-chunk{1,2,3,4,5,6,7}
```

Add an adapter by implementing `PlaythroughMachineAdapter` in one new class and adding one constructor entry to
`PlaythroughMachineAdapters.REGISTERED`. Use `read` for exact `GenericStack` inputs/outputs, `place` for real block
setup and `leftovers` for idle/empty and catalyst checks. The interface Javadoc states the site geometry: output
leaves the machine's DOWN face, and `inputFace` may be any other face. `Site` tracks placement and exposes the return port;
non-item adapters can replace the hopper with real pipes and declare justified raw keys via `providedInputs`.
Fluid outputs receive an AE2 fluid cell; chemical adapters declare their pack cell IDs through `storageCells`.
Those physical requester cells participate in the same full inventory balance. Non-item adapters still need their own runtime validation; this phase exercises items only. No main-mod code is changed.

### Phase 3b: AE2 addon machines

The default machine list also includes `crystal-assembler`, `reaction-chamber`, `overload-processing`,
`lightning-assembly` and `lightning-simulation`. These machines are AE grid nodes. Each joins its input subnet through the cable above it and runs on that subnet's
AE energy. The Interface exposes the whole subnet storage, so a pattern's fluid, FE and lightning inputs land in subnet
storage as well. Water and lava are provided world fluids (`PlaythroughAddonRecipes.WORLD_FLUIDS`).

`crystal-assembler` (`extendedae:crystal_assembler`): items and an optional fluid. The Storage Bus fills the
assembler's slots and tank. The adapter replays the assembler's own search (the first recipe in `RecipeManager` order whose
samples one run's contents satisfy) and rejects a recipe the machine would not pick. Four Acceleration Cards: 4 ticks.

`reaction-chamber` (`advanced_ae:reaction`, the type name Phase 0 records): items and an optional fluid input, checked
with Advanced AE's own `ReactionChamberRecipes.findRecipe`. Recipes with a fluid product (`quantum_infusion`, a KubeJS
time fluid) are rejected, and so are those that need `quantum_infusion_source` as input. Four cards: 4 ticks.

`overload-processing`, `lightning-assembly`, `lightning-simulation` (AE2 Lightning Tech): each recipe also needs FE and
HV or EHV lightning. The pattern carries the recipe's exact `totalEnergy` as Applied Flux FE and its exact
`lightningCost`. Two ME Chests beside the subnet hold them, one with an FE cell and one with a
`lightning_storage_component_v` (65536). The machine draws both from grid storage, as on a player's network. The bus is
filtered to the item and fluid inputs. FE is layout power. Lightning comes from the Lightning Collector, which stores
strikes in ME storage, or from the Tesla Coil. The mod's `findFirstProcessable` must select the recipe. Rejected:
lightning costs above 65536, fluid products, fluids other than water and lava (`superfluid_helium`,
`cryotheum_solution`), and derived recipes that the machine resolves to another recipe. Leftovers also require an empty
FE buffer, no locked recipe and empty subnet cells. `lightning_transform` strikes dropped items in the world and has
no machine.

Each item's proof first tries the producer that `QuestCoverageAnalysis.productionProofs` reaches at the
shallowest depth. With every adapter enabled, the 8-target regression therefore makes AE2 processors in one Overload
Processing Factory step and charged certus in the Reaction Chamber (`advanced_ae:certuscharger`), not in the Inscriber and Charger.

Smithing and stonecutting (`smithing`, `stonecutting`) are AE2 pattern kinds rather than shelf machines: an adapter
whose `assembler()` is true supplies its own `encode` (AE2's smithing-table or stonecutting pattern, decoded and
checked by `Recipe.verify`), and the factory's Molecular Assemblers run it beside the crafting patterns. Such
recipes never get a shelf site, input subnet or `place`/`leftovers` call (`Proof.shelf()` lists the rest), but they
still appear in `machineSteps` and count as real when a passing layout plans runs of them. Only transform recipes
are adapted; AE2 consumes the template, base and addition, as the vanilla smithing table does. Trim recipes (no fixed
output), custom smithing classes that AE2 cannot encode (JustDireThings abilities, paxels) and recipes declaring
remainders are rejected.

The Crystal Aggregator (`crystal-aggregator`, `ae2cs:crystal_aggregator_recipe`) joins the input subnet as an
ME-powered machine with four acceleration cards; its duration is the recipe's AE cost at 3,200 AE per tick plus one.
Its three slots take any item, so the provider runs in blocking mode (`Site.blocking()`). Blocking also sets the
input Storage Bus's Storage Filter to None: the provider sees the machine only through that bus, and the default
extractable-only filter hides inputs the machine will not give back. Recipes whose output is also
an input, or whose ingredients resolve to one item, are rejected: a hopper below could grow the output from an
input it simulated as extractable.

The Data Reassembler (`data-reassembler`, `data_energistics:data_reassembler`) also joins the input subnet, with four
acceleration cards (40 ticks less each). Its slots accumulate batches, so the machine's own resolver must pick the
recipe for one run and for full slots. Fluid recipes, the data flow resource and output modifiers have no
pack-justified supply here and are rejected.

Powah energizing (`energizing`) builds an Energizing Orb with one Nitro Energizing Rod linked to it, fed through the
rod's mounting face by a Creative Energy Cell; the provider runs in blocking mode because the orb takes one item per
slot and only while its output slot is empty. The duration is 20 warm-up ticks plus the scaled energy at 200,000 FE
per tick.

Mystical Agriculture's Seed Reprocessor (`reprocessor`) takes the seed from the top and sticks from the north; each
run's pattern carries the sticks that cover its 4,000 FE (two sticks), and `leftovers` checks that no fuel or energy
remains.

Machines that return many items per run can finish their job before the hopper drains them, so a finished target
waits up to `drainTicks()` (40 plus 8 ticks per output item of the largest run) until every machine is empty.

### Phase 3b: Mekanism adapters

These adapters are registered by default with the Phase 3a ones. The test mod does not build against Mekanism, so
they read Mekanism's recipe classes and configure its machines by reflection (`MekanismSetup`). Each machine
has its own filled Creative Energy Cube on its west side, ejecting into the machine's west energy input. Its side
configuration takes items on top and gives the product at the bottom, where the site hopper collects it.
A machine is idle when every internal slot and chemical tank is empty and no operation is in progress.
Both adapters reject a recipe when another recipe of the same type accepts the same concrete input, because the
machine runs the first recipe its input cache matches.

`metallurgic-infuser` (`mekanism:metallurgic_infusing`) supplies the infuse type as items. The candidates come
from the loaded `mekanism:chemical_conversion` recipes: items that exactly one conversion accepts, one at a time,
producing a chemical the recipe accepts, in an amount that divides the recipe's chemical amount. The pattern holds
that amount divided by the conversion amount, for example two redstone dust for the basic control circuit's 20
redstone. A second Storage Bus on the east side, filtered to that item, feeds the infusion slot (side type EXTRA).
The top bus is filtered to the main ingredient. Recipes with a per-tick chemical cost, no dividing conversion item,
or an ingredient equal to the infuse item are rejected.

`enrichment-chamber` (`mekanism:enriching`) and `crusher` (`mekanism:crushing`) share one item-to-item adapter. It
reads `getInput()` with its count and `getOutput(input)`. The Energized Smelter is not adapted, because it runs the
vanilla smelting recipes that the furnace adapter already covers. `mekanism:reaction` is not adapted. Every one of
its recipes needs a chemical input (oxygen, hydrogen, ethene or plutonium) that only other chemical machines produce.

Evidence (both machine paths, plain AE2 control on each). The two Mekanism chapters (`chapter=通用机械` also
matches `通用机械附属`) give 195 tasks, after leaving out
`compactmekanismmachines:compressed_wind_generator_x532480`. That target plans 7.1 million crafts and cannot finish
within the wall clock. The result is 194 both-pass and one not-bootstrappable per path, validated by
`tools/compat_playthrough_machines.py`. The infuser ran in 157 tasks (10 recipes), the Enrichment Chamber in 97 (3)
and the Crusher in 1 (the refined obsidian dust quest). No planned machine step went unobserved. The infusing-dependent
targets in other chapters (111 tasks) include the Applied Flux cores up to 64m and the AE2 Generators flux and
singularity generators. They give 96 both-pass per path. The 256m tiers and the 64m singularity generator hit the
1400-second wall clock in both layouts. The creative and infinite cells fail planning in both layouts, also without
these adapters.

## Phase 4: quest progression

`playthroughProgression` walks a quest chapter in dependency order with two bases that last the whole run: a plain
AE2 network and a federated layout (`PlaythroughTopology.base(seed)`: four networks, Router + Switch, projection,
seed 0 by default). Both sit in one GameTest, at test-relative x = 48 and x = 96, and take each order in turn
against the stock their earlier orders left. Each item task is ordered at its quest count. A task with
`consume_items` is then taken out of the requester's cells; other products stay in storage and later plans reuse
them. Phase 0 provided resources stay unlimited.

```bash
./gradlew :neoforge-1.21.1:jar :neoforge-1.21.1:compatTestJar --dependency-verification=strict --console=plain -q
python3 tools/compat_playthrough_progression.py --accept-eula --no-build --run-suffix ae2 --chapter 031C3CFAFBBF4A6D
python3 tools/compat_playthrough_progression.py --accept-eula --no-build --run-suffix ae2 --resume
# Repeat a run from one of its checkpoints in a new directory, for example to confirm a finding:
python3 tools/compat_playthrough_progression.py --accept-eula --no-build --run-suffix ae2-confirm \
  --from-checkpoint neoforge-1.21.1/build/compat/runs/nast-progression-ae2-checkpoints/segment-2 --segments 1
```

`--chapter` takes Phase 1's chapter filter; a chapter ID selects exactly one chapter (`ae2` also matches
`ae2存储元件`). Reports go to `build/playthrough/progression-<suffix>/`: `playthrough-progression.json` and `.md` per
target and order (class, machines available, stock reused, machines and equipment obtained for it), a summary per
chapter, the machine timeline (`machineTimeline`, `waitedBy`), blocked machine recipes and owned equipment. Each
segment's report and console log, and `segments.json`, sit beside them.

**Segments.** A run is a chain of server processes over one saved world, not a replay. A process stops at the first
order boundary after `--segment-seconds` (900; at least one order or target per process) and saves both bases
(`BaseState`) beside the world in `playthrough-progression-state.json`. The next process re-attaches to them and first
checks `restartChecks`: the same total stock key by key, idle CPUs, no ledger debt or transit, and the saved
Federation rules still enabled. Before each segment the tool copies the world and state to
`runs/nast-progression-<suffix>-checkpoints/segment-<k>`. If the 1400-second process deadline cuts an order, the
saved world holds a half-finished order. The tool then restores the checkpoint and repeats the segment, stopping
after the orders the cut process finished, or marks the order `deferred` if it alone outlasts a process. Replaying
from the start each time would make a chapter's run time grow with the square of its orders.

**Machines are earned.** A target's proof may use every enabled adapter. Each machine in it that the bases do not
own yet is earned first. Its equipment (`PlaythroughMachineAdapter.equipment`: by default the machine block, its AE2
upgrade cards and the recipe's fixed catalysts such as presses; Energizing adds its rod) is surveyed once from a
scratch site. Then it is taken out of both bases' storage, supplied when Phase 0 provides it, or ordered through the
factory with the machines already owned. An equipment order earns its own sites' parts first. Owned equipment
leaves ME storage and never returns. An equipment item that cannot be made blocks the recipes that need it until the
bases own another machine, so those products stay stand-ins. A target with no proof only because of blocked recipes
is `machine-unobtainable`. The default `equipment` keeps adapters written without it compiling and working.

**Checks after every order, in each base:** the Phase 1 invariants, with the stock balance replacing the source
check. Afterwards the cells hold the stock before, plus the plan's outputs, remainders and inputs, minus what it used
from stock. Stock reused is what the plan used minus what the source supplied, between 0 and the stock before.
Across bases, source extractions and total stock must match key by key. Logged exceptions (ERROR or with a
throwable) fail the order. Results use Phase 1's four classes, plus `not-bootstrappable`, `nondeterministic`,
`machine-unobtainable`, `unobtainable` (equipment), `deferred` and `craft-limit`. If the bases are left unsettled
or with different stock, the run stops with `stopReason`, since later orders would no longer compare like for like.

Properties use prefix `ae2federation.playthrough.progression.`: `seed` (layout, 0), `bankCells` (requester cells in
one ME Drive, 8, at most 10), `maxTargets`, `stopAfterSeconds`, `maxCrafts` (60000; a larger plan is recorded as
`craft-limit` and not started; `appflux:core_256m` plans 56,115 crafts and takes about 21 minutes in both bases),
`exclude` (comma-separated items, listed under `excludedTargets`), and `disturbances` (any of `cable`, `router`,
`STORAGE`, `CRAFTING`, `ME_POWER`; off by default) with `disturbanceEvery` (4) and `disturbanceSeed`. A disturbance
runs on the federated base while its job pushes across networks. One that finds no such push is recorded in
`perturbationExecuted`, not failed. Phase 1's `chapter`, `items`, `targetTicks` and `sprint`, and Phase 3's
`machineCopies` (1 here), also apply.

Results on NAST 2.2.1 (projection, seed 0):

| Chapter | Quest tasks | Both pass | Machine-unobtainable | Both fail | Other | Equipment orders | Processes |
|---|---:|---:|---:|---:|---:|---|---:|
| 应用能源2 | 130 | 130 | 0 | 0 | 0 | 21 pass, 5 unobtainable | 5 |
| AE2闪电科技 | 86 | 37 | 43 | 5 | 1 not-bootstrappable | 14 pass, 7 unobtainable | 2 |

No target was baseline-only or federated-only. In AE2 the bases earned the Seed Reprocessor, Blast Furnace,
Reaction Chamber, Inscriber, Furnace, Crystal Aggregator and Crystal Assembler. 96 of 151 passing orders reused
stock (733 items). Lightning Tech crafted and consumed 10,000 `minecraft:cooked_cod`. Its Lightning Simulation Room
comes only from `lightning_transform`, a world lightning strike with no adapter. The Assembly Chamber is made in the
Simulation Room and the Overload Processing Factory in the Chamber, so none of the three can be earned; 43 tasks,
`ae2lt:overload_tnt` among them, need one of them. The five both-fail tasks fail identically in both bases: two
targets with item components, `ae2:creative_energy_cell` (its inputs need component-bearing
`extendedae_plus:basic_core`), and `ae2lt:tianshu_overload_main_core` (two tasks, no finite proof). A 25-target
AE2 run with a disturbance on every order executed all 34 disturbances (cable 9, router 8, CRAFTING 8, ME_POWER 6,
STORAGE 3), all both-pass.
