# Nexus Processor recipe feasibility and collision audit

Date: 2026-10-06. Research and design record only; no production recipe, item, texture, or registry was changed.

The requested item rename, new core, recipe replacement, scope, artwork/documentation follow-up, and acceptance
expectations were recorded together in the [archived Nexus Processor idea](../../docs/archive/ideas/nexus-processor.md).
That historical proposal was superseded by the 2026-10-08 chain; see the
[current survival feature](../../docs/features/survival-playability.md) for implemented recipes.
This knowledge file supplies the investigation evidence; it is not a substitute for that change specification.

Follow-up: the user requested explicit planned item IDs. The idea now specifies `ae2federation:nexus_processor`
and `ae2federation:nexus_core`. The user subsequently clarified that old item stacks need not be preserved.
Replace `ae2federation:federation_logic_processor` directly, without a legacy registration, alias, remapping,
or save-migration mechanism. Old-ID stacks may be lost or become unavailable after updating.

## User proposal

- Display name: Nexus Processor / 联结处理器.
- Craft three Redstone Dust and three Ender Dust into **16 Nexus Cores / 联结核心** using shaped crafting.
- Inscriber assembly: one Nexus Core in the top slot, one Ender Dust in the middle, and one AE2 Printed Silicon
  (`ae2:printed_silicon`, 硅板) in the bottom, producing one Nexus Processor.
- The assistant proposed the two-row pattern `RRR` / `EEE`, where R is Redstone Dust and E is Ender Dust.
  The user specified shaped crafting and quantities but has not separately finalized this exact arrangement.
- Do not reintroduce a dedicated printed Nexus circuit, a new press, or a new loot/world-generation gate.
  Existing AE2 Printed Silicon still uses its existing production requirements.
- This supersedes the earlier brainstorming about a dedicated material-to-printed-circuit chain as the current
  recipe proposal. Earlier speculative ingredients and intermediate products are not implementation requirements.

## Conclusion

The proposed three-slot assembly is supported by AE2's existing Inscriber recipe system. The two-row core recipe
had no collision in the statically evaluated ordinary crafting recipes in the inspected artifact set.
No six-ingredient recipe accepting exactly three actual `minecraft:redstone` and three actual `ae2:ender_dust`
was found, even when considering alternative arrangements and shapeless recipes.
This is not an exhaustive runtime or all-CurseForge compatibility guarantee.

## AE2 mechanism verification

Source inspected: AE2 19.2.17, commit `db95d25ccc79f7bd55b504cf71522b57d60bf4f7`.

1. `InscriberRecipe` serializes arbitrary top, middle, and bottom Ingredients. The upper slot is not restricted
   to a press or a native printed circuit. A new core becomes a valid ingredient through its registered recipe.
2. **Use `mode: "press"`.** `InscriberBlockEntity.tickingRequest` consumes one item from each upper/lower slot
   for PRESS and always consumes one middle item. INSCRIBE preserves the upper/lower items, which would make the
   core and silicon reusable and violate the intended recipe.
3. `InscriberRecipes.findRecipe` accepts either orientation of the upper/lower ingredients. Thus core above and
   silicon below is the intended presentation, but the native machine also accepts silicon above and core below.
   There is no reason to introduce custom logic merely to forbid the reversed arrangement.
4. Native insertion validation derives allowed combinations from loaded Inscriber recipes. No additional
   Mixin, new recipe type, press acquisition, or world loot change is required for this assembly.
5. AE2 already supplies Ender Dust (`ae2:ender_dust`) and includes it in `c:dusts/ender_pearl`.
   Its native Inscriber recipe produces one dust from one vanilla Ender Pearl without a press.

Primary sources:
[recipe schema](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/main/java/appeng/recipes/handlers/InscriberRecipe.java),
[consumption and insertion](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/main/java/appeng/blockentity/misc/InscriberBlockEntity.java),
[matching and upper/lower symmetry](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/main/java/appeng/blockentity/misc/InscriberRecipes.java),
[native logic processor recipe](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/generated/resources/data/ae2/recipe/inscriber/logic_processor.json).

## Collision audit scope and method

- ATM10 **8.2**, from the exact archive pinned in [the compatibility profile](../../tests/compat/profiles/atm10.json):
  464 top-level mod JARs.
- Added the available compatibility download cache, deduplicating by artifact filename: **483 mod JARs** total.
  Different versions of a mod are separate artifacts; these are not 483 distinct AE2 addons.
- Also inspected the pack's `sawmill.zip` datapack, NeoForge **21.1.251** universal JAR, Minecraft **1.21.1** extra
  resource JAR, and loose JSON resources under the pack's KubeJS data/datapack locations.
- Successfully parsed **85,555 recipe JSON resources** and **9,737 item-tag resources**, including duplicate
  recipe IDs across different artifact versions. Found **41,514 shaped**, **12,109 shapeless**, and **71 AE2
  Inscriber** recipe objects. These numbers describe source resources, not the final loaded RecipeManager.
- Expanded nested item-tag references, including the framework-provided `c:dusts/redstone` tag, and supported
  ordinary item/tag ingredients and ingredient alternative lists. Normalized unnamespaced vanilla recipe types.
- For six occupied crafting slots, tested assignments of three redstone and three ender dust, including
  ingredient alternatives; separately checked the proposed two-row arrangement and shapeless matches.
- Tag definitions were conservatively unioned across resources; datapack replace/load order and recipe
  conditions were not executed. Custom Ingredient predicates/recipe serializers were not fully evaluated.
  Search results cannot rule out dynamic or generic custom recipes accepting arbitrary inputs.
- Inspected 193 KubeJS JavaScript files by text search for Ender Dust identifiers/tags. Three files referenced
  the dust directly: Actually Additions, Immersive Engineering, and Ender IO crushing recipes. Those occurrences
  produce dust from pearls and do not add the proposed crafting recipe. Also read the AE2 script's universal
  press helper and its calls; it does not add the proposed core or processor recipe.
- JavaScript was not executed. Generated identifiers, script-created tags, nested JARs, external datapacks,
  resource overrides, and custom runtime matching remain outside this static guarantee.
- Three Pam's HarvestCraft food recipe JSON files failed parsing. Manual inspection identified them as malformed
  campfire/smelting/smoking recipes for tofu rabbit, unrelated to these crafting/Inscriber inputs.

## Closest existing recipe

ExtendedAE **2.2.38** and **2.2.39** both include `extendedae:entro_seed`, a shapeless recipe producing two seeds:

| Ingredient | Occupied slots |
|---|---:|
| Sand (`c:sands`) | 1 |
| Ender Pearl Dust (`c:dusts/ender_pearl`) | 3 |
| Redstone Dust (`c:dusts/redstone`) | 2 |
| Glowstone Dust (`c:dusts/glowstone`) | 2 |
| Sky Stone Dust (`ae2:sky_dust`) | 1 |

This requires nine occupied slots and additional ingredients, so it does not match the six-slot Nexus Core
proposal. Sharing Ender Dust and Redstone Dust does not by itself constitute a recipe collision.
No inspected AE2 Inscriber recipe accepted actual Ender Dust as its middle input through the ordinary
item/tag matching covered by this scan. A direct reference to Federation's own new core gives the proposed
assembly a further distinct ingredient; avoid assigning the core a generic substitute tag without a reason.

## Cost and implementation consequences

- One core batch plus sixteen assemblies consumes **3 Redstone Dust + 19 Ender Dust + 16 Printed Silicon**.
  This excludes the upstream silicon ingredients and processing energy. Native AE2 pearl-to-dust yield makes
  the dust cost equivalent to 19 vanilla pearls; integrations/modpack recipes may offer other yields.
- Per processor, the amortized cost is 3/16 redstone, 19/16 ender dust, and one Printed Silicon.
- The first processor needs four dust available: three for the batch and one for assembly, leaving 15 cores.
  This initial batch cost should be distinguished from the amortized cost when evaluating early availability.
- Compared with the current implementation, this removes the requirement to consume a completed native Logic
  Processor. The revised progression and gold requirement therefore change; this is a design consequence,
  not a reason to add another intermediate automatically.
- Current production recipe is still `common/src/main/resources/data/ae2federation/recipe/federation_logic_processor.json`:
  native Logic Processor plus Fluix Dust, with PRESS mode. The current registry path remains
  `ae2federation:federation_logic_processor`. The planned implementation replaces it with
  `ae2federation:nexus_processor` and updates all current recipe references. Per the user's explicit instruction,
  preserving old-ID items in existing saves is not required; do not add migration or compatibility machinery.
- Decide during implementation whether Ender Dust is an exact AE2 item or the conventional dust tag. This audit
  evaluated the actual AE2 dust stack; it did not exhaustively test every substitute item accepted by that tag.

## Verification still required after implementation

The new core and recipes do not exist yet, so no runtime success is claimed. Before shipping, check:

1. The selected six-slot shaped arrangement produces exactly sixteen cores; extra ingredients and wrong counts
   do not match. Native shaped crafting offsets/mirroring should behave normally.
2. Inscriber assembly consumes one core, one dust, and one Printed Silicon per output, including automated insertion.
   The native reversed top/bottom arrangement should also work.
3. Check the loaded ATM10 recipe set and crafting result, including active KubeJS scripts, and the clean AE2-only
   baseline. Ensure a full output slot pauses processing without consuming ingredients.
4. JEI/GuideME show the intended ingredients and output counts using the project's existing integrations.

## Audit reproduction in this research session

The standard-library-only scanner was kept outside the repository in temporary storage. With the same local
compatibility cache available, the exact invocation is:

```sh
python /tmp/nexus-recipe-audit.py
```

Expected signals: `exact_hits` and `six_ingredient_candidates` are empty; three unrelated food JSON parse errors
are reported; the two same-ID ExtendedAE seed recipe versions are the ordinary crafting recipes using both target
materials. Detailed output is in `/tmp/nexus-recipe-audit-result.json`. These temporary artifacts are not durable
repository tooling; the scope, results, assumptions, and source references above are the persistent record.
