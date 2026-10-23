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
