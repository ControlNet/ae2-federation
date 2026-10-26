# NAST quest coverage probe

The optional production compat group `quest-coverage` runs `questCoverage` in the pack server. It is intentionally
absent from the default `nast` test list. Run it from the repository root:

```bash
python3 tools/compat_run.py --accept-eula --tests questCoverage nast
```

The probe reads `config/ftbquests/quests` in the server run directory and writes `quest-coverage.json` and
`quest-coverage.md` beside the path in `ae2federation.compat.report`. No FTB dependency is needed.

NAST 2.2.1 quest files omit commas, include typed integer arrays, and contain empty compound keys in Draconic
Evolution item components. Vanilla `TagParser` needs separator normalization and reversible escaping of empty keys.
Its `lang/en_us.snbt` is empty; `zh_cn.snbt` contains the titles, including chapter group titles. The probe records
that fallback. Actual filter/tag task items must be identified by FTB filter namespaces or tag syntax, not the word
"filter" in ordinary mod item IDs. The inspected pack has no FTB filter/tag items. Missing-item markers also occur
in the source and are distinct from valid plain item targets.

RecipeManager can contain recipe types without registry keys. The probe records their self-reported IDs or explicit
implementation-class labels separately. Crafting classification uses `instanceof CraftingRecipe`, not the type ID.
Empty outputs are unreadable, and empty generic ingredient lists do not prove a machine recipe has no inputs.
Reachability is bounded to 64 steps; synchronous expansion prevents cycles from proving themselves reachable.
No-recipe targets are listed separately and are not counted as produced, although base ingredients are unlimited.

This is an item-ID planning estimate. It does not validate component variants, amounts, fluids, chemicals, catalysts,
power, machine conditions, quest prerequisites or AE2 execution. Unreadable outputs can hide producers, so base-item
classification is provisional. Greedy ranking stops at zero marginal gain; combinations can still matter.

Final Phase 0 production result: `PASS nast 1/1 (148 s)`. The pack has 29 chapters, 1,200 quests, 1,451 item tasks
and 1,238 distinct plain targets. Strict production stays 33/36/85; provided production is 422/460/633 for crafting,
then AE2, then cooking. The AE2 chapter reaches 87/131 at the third level. Eight requested processor/machinery/eye/
paste checks pass; Mekanism basic control circuit still requires unreadable metallurgic infusing.

Supply rules live in `QuestCoverageResources.java`: configured item tags, natural-block loot, entity loot and closed
SCCs. A natural block has no item or no readable producer for its item. Final counts: 1,175 natural blocks, 893 drop
items and 26 target matches. Supplements retain only old ore/crystal drops absent from natural loot (60 ore items,
22 crystal items). All rules supply 2,304 items including 166 targets. Provided and produced headings overlap; only
a recipe proof counts as production. Natural is a heuristic: hidden outputs and uncraftable technical blocks can
inflate supplies. Loot is enumerated from reloadable registries without sampling conditions or simulating global
modifiers. There are 159 missing tables and one unknown entry (`bonsaitrees4:drop_inventory`).

The four presses are supplied by `data_energistics:data_mysterious_cube` loot. AE2's own mysterious cube has two
producer recipes and is not natural. Mystical Agriculture certus crops also fail the exact natural predicate:
`MysticalSeedsItem` extends `ItemNameBlockItem`, and certus seeds have an infusion producer. Do not assume all crops
have no item. Essence remains outside natural supplies; the cube now supplies the press stock instead.

The SCC entry predicate requires every ingredient slot to have an outside alternative. Press-copy does not enter,
even with iron outside a singleton press component. All four presses actually belong to an open 825-item maximal
SCC; other members have external producers. Closed SCCs remain 114 groups / 599 items. Do not replace this with a
self-loop test or infer entry from a single outside ingredient.

Blockers count each target once on its cheapest chain, minimizing unreachable item occurrences then steps. The AE2
chapter's top five are Applied Flux 1k core (10), infused alloy (10), energy tablet (10), 4k core (9), 16k core (8).
These lead to metallurgic infusing, not missing press stock. Per-chapter rankings are intersections with chapter
target sets. Greedy additions reach 830 targets but can miss combinations with zero single-class gain.

Adapter priorities use explicit serialized output fields, never names or namespace guesses. The counterfactual
enables all readable classes and credits opaque outputs, then withholds one type. Unreadable inputs are never
production proofs. There are 2,846 unreadable outputs / 85 types and 2,476 unreadable ingredient lists / 54 types;
2,188 output recipes remain unattributed. Leaders: metallurgic infusing 227, reaction 78, smithing 37, enriching 31, integrated
working station 27, crystal assembler 23, Advanced AE reaction 16. Advanced AE's reaction type lacks a registry key;
its self-reported ID is recorded. `jdte:infusion` implements CraftingRecipe.

The final handoff, reports, real-graph verification script and recursive item witnesses are under ignored
`build/playthrough/`. No dependencies, default NAST test changes or factory implementation were added.


Independent review fixes: optional quest metadata is empty when absent; absent root/chapters are reported explicitly
rather than conflated with zero targets. Item IDs must exist in the registry and cannot be air. Smelting loot uses
already-inspected readable recipe records. Nested loot tables use an active recursion path, not global visitation.
Cheapest-chain costs use exact BigInteger values. JSON object keys and opaque evidence are ordered. `all` expands
only eligible groups, keeping quest coverage explicitly selectable by group or method but opt-in.

Adapter comparisons use a separate producer universe including every opaque output hint, without the no-producer
supply rule; other independent supplies remain. Normal coverage is unchanged. This raises reaction impact 5→78,
centrifuge 1→12, fire crafting 1→3, slicing 0→2 and soul binding 0→1. Actual hinted pellet outputs explain downstream
impact previously hidden by the base assumption. Every type's exclusive impact was independently replayed.

Both explicit selections passed (group 154 s, method 148 s). Headline values, supplies and blocker costs are unchanged.
KubeJS swaps numeric suffixes on three creative-capacitor recipe IDs between starts; full reports compare equal after
normalizing only those live aliases. Preserve actual IDs rather than promising byte identity across changing inputs.
Ignored regression fixtures cover metadata, exact costs, adapter supplies, group eligibility and canonical JSON.
