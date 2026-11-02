# Processing recipes from a client JEI scan (2026-11-01)

Owner decision (2026-11-01): recipes the server can read reliably (vanilla furnace types, crafting, stonecutting,
smithing) come from the server's RecipeManager (`RecipeIndex`); every other machine's recipes come from a player's
JEI, scanned on the client and cached in the world save.

## Pieces

- `automation/ViewerRecipes` - SavedData `ae2federation_viewer_recipes` (overworld). Categories (id + catalyst item
  IDs) and recipes (category, optional id, input slots with optional item tag, outputs, byproducts). Saved recipe by
  recipe, so a removed mod drops only its recipes. `making(Item, named)` gives `Recipe` records for `mc.recipes_for`;
  recipes with a stack whose ID does not round-trip (components) are left out.
- `automation/ViewerScans` - server protocol: client says Ready, server asks the first ready player (scan number),
  pages arrive in order, last page replaces the catalog. Dropped on out-of-order page, decline (index -1), logout or
  `/reload` (OnDatapackSyncEvent with null player clears ready players; JEI restarts and re-announces).
  `requestWith` swaps the transport (GameTests stand in for a client; a mock player cannot receive our payload
  because NeoForge refuses unnegotiated channels with UnsupportedOperationException).
- `client/automation/ViewerScanClient` - 2.5 ms per client tick (AE All Pattern's budget, owner decision), pages <=
  30,000 bytes measured by encoding.
- `compat/jei/FederationJeiPlugin` + `JeiRecipeScanner` - all categories; the six vanilla types map to the server's
  recipe type IDs and are not read (machines only). Chance outputs: slot cycles several stacks or a tooltip line the
  slot adds to the ingredient's own reads as a chance. `IRecipeSlotDrawable.getTooltip(...)` is forRemoval in JEI
  19.64 with no replacement; catch LinkageError too.
- `compat/ae2jeiintegration/Ae2JeiStacks` - other ingredient types (Mekanism chemicals) via AE2 JEI Integration's
  `IngredientConverters`, only when `ae2jeiintegration` is loaded.
- `RecipeIndex.record(Entry, more)`: viewer catalysts merge into processing recipes only. JEI's crafting catalysts
  (crafting table, modded stations) must not reach crafting recipes: AE2 pushes crafting patterns into assemblers only.

## Facts found on the way

- Real run (JEI 19.64.0.455 + Mekanism 10.7.19.85 + Applied Mekanistics 1.6.3 + AE2 JEI Integration 1.2.1): 1964
  recipes in 54 categories, sent in about 7 s after joining.
- A newly placed Mekanism machine exposes no item handler on any face until its side config is set
  (`ISideConfiguration.getConfig().getConfig(TransmissionType.ITEM).setDataType(DataType.INPUT, side)` then
  `sideChanged`). AE2's `PatternContainerGroup.fromMachine` needs an item or fluid handler on the face, so a
  provider's `targets` miss an unconfigured Mekanism machine. That is AE2's own rule, kept.
- A registry's `byNameCodec` reads an ID it no longer has as its default value (air for items) instead of failing,
  so a saved AE2 stack of a removed mod's item decodes without error. `ViewerRecipes.load` keeps a recipe only when
  it encodes back to the same tag (any key type). GameTest `asavedviewerscanislistedwithnoplayeronline` saves,
  tampers one recipe's item ID, loads, and lists the rest with no player online.
- Timing: one scan per server start and per `/reload` (the done flag is in memory only); until the last page arrives
  programs read the saved catalog, and `replace` swaps it in one server-thread call.
- `GameTestHelper.makeMockServerPlayerInLevel` is forRemoval but the only mock that joins the player list.
- `RegistryFriendlyByteBuf(ByteBuf, RegistryAccess)` is deprecated in NeoForge 21.1; pass the ConnectionType.
- `AddonCodeContractTest` treats two/three-letter roots as optional mods; `io.netty` is now on its required list.

## Runs

- GameTests: `programwritesarecipeviewerrecipe`, `aviewerscanoutoforderasksthenextviewer`.
- UI: `./gradlew :neoforge-1.21.1:runViewerUiTestClient -PenableViewerCompatibility=true
  -PfederationUiSelection=ui.automation-viewer-pattern -PfederationUiOutputDir=$PWD/build/viewer-pattern-check
  -PfederationUiWatchdogSec=600` under `xvfb-run`; source set `viewerTest` (`common/src/viewertest/java`), game
  directory `run-viewer-uitest`.

## Create 6.0.10 (scan read 2026-11-01)

- With Create added to the viewer client: 5482 recipes in 77 categories, about 20 s under xvfb (Mekanism alone
  1964 in 54, about 7 s). The scenario's scan wait is 240 s.
- Read right: crushing, milling, washing chances become byproducts (Create's slot tooltip says "Chance"), e.g.
  `create:crushing/raw_iron` -> crushed raw iron, byproduct experience nugget; tag slots kept (`#c:ingots/iron`);
  fluid inputs (spout filling, mixing) kept. Catalysts give machines (crushing_wheel, millstone, encased_fan,
  mechanical_press + basin, ...).
- Not carried by JEI slots: a mixing recipe's heat (blaze burner), sequenced assembly's steps and loops (read as one
  recipe of all step inputs; no catalyst, so no machines), item application (no catalyst).
- Create shows server recipes again under its own categories with the server's IDs: automatic_shaped (1703,
  mechanical crafter), automatic_shapeless and automatic_packing (mixer/press + basin), block_cutting (saw),
  fan_blasting/smoking. `write_pattern` used to take the server's recipe of that ID and wrote a crafting pattern for a
  basin; it now uses the server's recipe only when the record's type matches (GameTest
  `aviewerrecipewithaserverrecipeidiswrittenasitreads`, UI `ui.automation-viewer-create`).
- Ponder's tooltip handler measures text on vanilla's search-tree worker thread; the test-only font-thread guard logs
  it (only one claim-conflict scenario checks the count).

## ATM10 8.3 (2026-11-01, production client via portablemc under Xvfb, server pack mods minus IrisSearch)

- 202,381 recipes in 543 categories (400 with recipes). Biggest: create:automatic_shaped 38,414,
  sawmill:woodcutting 18,657, minecolonies:* about 56,000 together, irons_spellbooks:arcane_anvil 8,889.
- Old scanner: 40,551 recipes in 35 min (about 19/s). JEI's `IRecipeSlotView.getTagKey` walks every item tag per
  slot; a per-scan table (items in order -> tag, smallest ID among equal tags) made it 1589 s for all (about 127/s,
  4 ms budget). Same 5,482 recipes in the Create pack before/after (ignoring which of equal tags and JEI's order).
- What remains is JEI itself: layout per recipe and the slot tooltip (every mod's tooltip handler) for chance outputs.
- 49.8% of recipes have an ID, unique within their category; 187 categories have none (JEI-side generated:
  sawmill, MineColonies, anvil, FTB Quests). The anvil category's order changes between runs (Create pack).
- JEI has no lookup by ID; `IRecipeLookup.get()` streams a category.
- After the last page the server fell 35 s behind (replace + index + save of 202k recipes); cache file 6.2 MB,
  `save-all` about 2 s.

## Content keys for recipes without IDs (ATM10 measurement, 2026-11-01, probe not committed)

- Proposal (not approved): split each category into hash buckets (about 500 recipes each) of the recipe ID, or of a
  content key when there is none; a task is "category X, bucket k", which any client can answer from its own JEI; a
  per-bucket digest tells the server which buckets changed.
- Content key = SHA-256 of `IRecipeManager.getRecipeIngredients(category, recipe)` per role, each ingredient as
  `type:getUniqueId(UidContext.Recipe)xamount`, sorted within the role. No layout, no tooltips.
- Run it at least two minutes after `onRuntimeAvailable`: MineColonies (about 60k) and FTB Quests add their recipes
  later; at runtime-available they are missing.
- Scanned scope (400 categories, 253,924 recipes): 21.8 s of render-thread CPU for all keys (about 0.086 ms a
  recipe; layout scan is about 0.6 ms). No-ID categories alone: 104,399 recipes, 9.4 s. create:automatic_shaped is the
  costliest (6.7 s for 38,450). Server-read types are far costlier (smelting about 23 ms a recipe) but not scanned.
- Two client starts: 21 of 1,021 buckets differ (12 of 453 no-ID buckets). Unstable: ftbquests:quest (2,110 keys),
  evilcraftcompat:spirit_furnace (412), productivemetalworks:item_casting, xycraft extractor, tombstone:combine; JEI
  brewing (vanilla and Create) has stable contents but its generated IDs move between recipes. MineColonies, sawmill,
  anvil and arcane anvil are stable.
- Why keys move (pre-hash text diffed across two more runs):
  - Recipes rolled at random when the client loads: EvilCraft spirit furnace (mob drops and their counts, e.g. arrow x1
    vs x2), FTB Quests (loot rewards, e.g. lava bucket vs quartz), Tombstone combine (which item of a group is shown).
    The recipe really differs per run; no key can be stable.
  - A subset picked from an unordered collection: Productive Metalworks casting (which fluids are listed),
    XyCraft extractor (whether sugar water is listed).
  - A mod's subtype ID prints a Java object: Supplementaries' tipped bamboo spikes put `Potion@4119b59c` in
    `getUniqueId`, so every recipe holding them (Create shapeless and washing, Sushi Go rice cooker) moves. A key
    built from the item ID plus its data components encoded by codec would not depend on mod subtype interpreters.
  - JEI's brewing IDs: contents identical, but 24 of 788 IDs sit on different recipes each run (built while walking
    hash sets); not traced further.
- About 1,000 recipes in scope throw NullPointerException from the ingredient supplier (create:automatic_shaped 402,
  minecolonies:sawmill 389, mechanic 91, domum_ornamentum:architects_cutter 82); they need another key.
- Same-key duplicates in no-ID categories: 2,511 of 104,399 (e.g. Industrial Foregoing mycelial generators list the
  same ingredients); harmless when the bucket is the unit.
- The compat assembler hard-links the client's and server's mod jars: `cp` into one replaces the other's jar in
  place. Stop both JVMs before copying a new jar into either.
- The earlier ~1,000 NullPointerExceptions came from JEI/mod subtype interpreters behind `getUniqueId`, not from
  `getRecipeIngredients`: encoding ItemStacks ourselves had 0 failures.

## Fingerprint variants (ATM10, two client starts, 2026-11-01, probe not committed)

Scope: 536 categories, 418,043 JEI recipes (all but the six server types and ftbquests:quest; includes
generatorgalore:solid_fuels 127,242, which the scan itself does not send). Raw data: worktree
`build/fingerprint-check/` (compare.txt, textdiffs, Create chance dump).

- A = ingredients per role, item/fluid ID + data component patch via codec + amount: 29.3 s render thread
  (0.070 ms/recipe, about 25% over the JEI-uid key); costliest create:automatic_shaped 5.9 s, minecraft:anvil 4.4 s,
  arcane_anvil 4.2 s, domum architects_cutter 13.5 ms/recipe. 0 failures; 934 component patches cannot be encoded
  (Industrial Foregoing mycelial disenchantment/potion).
- B = `Recipe.STREAM_CODEC` bytes when the JEI object is a RecipeHolder/Recipe: 10.5 us per covered recipe; covers
  128,065 (30.6%); 348 categories fully. Not covered: JEI anvil, MineColonies GenericRecipe (~60k), arcane anvil,
  fuel/generator displays, jei:information, brewing. Fails on generatorgalore (class init, every recipe: stop after
  the first failure in a category), mekanism nutritional_liquifier, create:block_cutting (empty stack), display-only
  recipe classes. Carries chances: create:milling/calcite bytes hold 0.75 (3f400000), as in Create's JSON.
- C = B else A: 19.6 s (about 6.5 min at 2.5 ms/tick).
- Stability (buckets differing between two starts): A 174/1,485 (11.7%), B 32/648 (4.9%), C 194/1,485 (13.1%),
  JEI-uid key 2%. Sources: item components filled at random (Relics `relics:data` 215 keys, 196 in anvil; Astral
  Sorcery artifact; Draconic provider UUID; IE revolver perks), hash-ordered sets/maps in components or serializers
  (`neoforge:cures`, jeistuff trades/loot, Apotheosis affixes, Draconic module NBT order), client-built recipes
  whose IDs move (Create potion mixing), loot-rolled recipes (EvilCraft, Tombstone, casting, extractor).
  In anvil (no IDs) a changed key moved recipes between buckets: 124 of 128 buckets dirty.
- Takeaways (assistant): the partition key must ignore components (item/fluid IDs + amounts) so recipes never move;
  the change digest (C) is per recipe, compared per bucket first and then per recipe, so noise costs re-reading the
  noisy recipes only, not whole buckets.

## Partition keys (offline, on the two fingerprint runs, 2026-11-02)

Same 290,801 recipes as the fingerprint runs minus generatorgalore:solid_fuels; bucket count per category = smallest
power of two >= count / 500, fixed from run 1 (1,229 buckets). Script and report: worktree `build/bucket-key-check/`.
Partition key hashed with SHA-256 (first 8 bytes) of category + key, bucket = hash mod count.

| Key | Largest bucket | Buckets with other members in run 2 | Dirty buckets (C digest) | Digests listed | Re-read |
|---|---|---|---|---|---|
| K1: item/fluid ID + amount | 664 (p99 527) | 7 (230 keys) | 143 | 48,479 (388 KB) | 1,058 |
| K3: K1 + component type names | 1,042 | 7 | 128 | 43,492 | 1,058 |
| KA: with component values | 537 | 163 (anvil 124) | 185 | 64,341 | 1,058 |
| KID: recipe ID, else KA | 537 | 153 (anvil 124) | 183 | 64,041 | 1,073 |

- K1 buckets stay even: same-key groups exist (Reliquary mob charm info 376, potion/enchanted-book combos 155) but no
  bucket passes 1,000. The 7 unstable K1 buckets are loot-rolled recipes (EvilCraft spirit furnace, Productive
  Metalworks casting, Tombstone combine, Astral altar, XyCraft extractor): their contents really differ per run.
- With the per-recipe diff the re-read count does not depend on the partition key (a recipe that moves bucket is
  re-read in its new bucket either way); the key decides how many buckets get dirty, the digest list size, and whether
  a finished bucket task stays finished.
- 465 of the 1,058 re-reads were industrialforegoing:mycelial_potion: the component codec fails and its error text
  holds `Potion@<hash>`. Hashing a fixed marker for a failed encoding instead: 593 re-reads, 142 dirty buckets.
  The rest: EvilCraft 212, anvil 210 (Relics random data), jeistuff chest loot 51, casting 27, Create brewing 26.
- Doubling a category's bucket count sends bucket b only to b or b + n (checked on every 50th recipe). A synthetic
  edit of 100 random run-2 digests (test-only data) was found exactly: 100 re-reads.

## Owner decision: real recipes only (2026-11-02)

The scan keeps only JEI recipe objects that are a `RecipeHolder` or a `Recipe`; every display object (built by JEI or a
mod's plugin: anvil, MineColonies workers, arcane anvil, fuel/generator tables, info pages, brewing, botany pots, ...)
is noise and is dropped, with no per-category opt-in. ATM10 (scope above): 159,333 display objects dropped, about
131,000 real recipes kept (128,065 encodable by `Recipe.STREAM_CODEC`, about 3,400 whose codec throws), of which
29,995 reach JEI as a bare `Recipe` without their holder ID. Lists: worktree `build/bucket-key-check/`
(`id-object-cross.txt`, `display-processing.txt`, `classes-by-category.tsv`).

Also dropped (owner, 2026-11-02): real recipes with no inputs, no outputs, or outputs equal to inputs (as a multiset;
e.g. jeistuff chest loot, `entangled:entangled_clear_nbt`): 2,786 in ATM10. Of the real recipes, 49 change their
ingredients between two client starts (Create automatic brewing: client-built recipes whose `potion_mixing_vanilla_<n>`
IDs move; Productive Metalworks casting and XyCraft extractor: JEI lists a subset of an unordered collection) and
about 100 more change only their `Recipe.STREAM_CODEC` bytes (hash-ordered sets: create:automatic_shaped, jeistuff).
Every JEI ID is unique within its category and present in both starts (`real-only.txt`).

## Server-known recipes and appearance time (ATM10 probe, 2026-11-02, probe not committed)

Raw data: worktree `build/recipe-class-check/` (recipes.tsv.gz, timeline.tsv, summary.txt). Server-known = present
in the recipes the server synced to the client (`getConnection().getRecipeManager()`, 97,301 holders).

| JEI object | server-known | not known |
|---|---|---|
| RecipeHolder | 69,682 (57,493 the synced holder itself; 12,189 same ID, other value) | 28,206 |
| bare Recipe | 23,912 (all by identity with a synced holder's value) | 9,670 |
| display | 1 | 159,332 |

- Same ID, other value: Create's automatic_shapeless/packing, block_cutting, Dragons Plus and CEI categories wrap
  the server's crafting/stonecutting recipe in their own holder; `byKey(id)` returns the server's recipe.
- Holders with IDs the server lacks are client-built: Dragons Plus coloring 10,170, MI/EI generated machine recipes,
  Create draining/automatic_brewing/deploying/spout_filling, jeistuff, IE potions, mekanism:smelting, casting.
- Bare recipes the server lacks: minecolonies:composting 3,789, mekanism:nutritional_liquifier 2,150, Productive Bees
  (beehive 1,849, incubation, centrifuge, breeding), Productive Trees, PneumaticCraft heat properties, ...
- `mekanism:(de)condensentrating`: JEI's `getRegistryName` differs from the holder ID (`mekanism:rotary/...`).
- Server recipe type differs from the JEI category ID in 79 categories (create:sawing -> create:cutting, ...).
- Appearance: every holder is in JEI at `onRuntimeAvailable`; the only late categories (0 at T0, full within 20.8 s,
  not even listed by JEI at T0) are the 32 minecolonies:* (about 56k, all display except composting), EvilCraft spirit
  furnace (display) and ftbquests:loot_crate. MineColonies logs its client data sync at T0+6.6 s: the late recipes
  come with the server's data sync. Nothing changed after 20.8 s (watched to 205 s).

## Blast radius of one mod (offline, real processing recipes, 2026-11-02)

Recipes of other mods that mention a mod's items or fluids (upper bound of what removing the mod, or changing its
tag entries, can change): median 79, p90 about 3,000; largest minecraft 52,596, productivetrees 16,744, bibliocraft
10,415, aether 9,940. Script and table: worktree `build/bucket-key-check/blast*`.

## Open (assistant choices, not owner decisions)

- Trust: any player's JEI replaces the catalog for the whole world.
- EMI without JEI sends nothing; recipes making only fluids/chemicals are unreachable (`recipes_for` keys by Item).
- No recipe constructor for scripts yet.
