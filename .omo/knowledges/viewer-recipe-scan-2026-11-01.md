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

- GameTests: `--manifest --match viewer` (8): stand-in clients in `test/automation/StandInViewer` answer through
  the payload codecs; lease handover, two-minute expiry (clock injected), second sync reads only changes (4,500
  recipes, 16 buckets, two viewers), deletion rule (mod test injected), per-category saving.
- UI: `./gradlew :neoforge-1.21.1:runViewerUiTestClient -PenableViewerCompatibility=true
  -PfederationUiSelection=ui.automation-viewer-pattern -PfederationUiOutputDir=$PWD/build/viewer-pattern-check
  -PfederationUiWatchdogSec=600` under `xvfb-run`; source set `viewerTest` (`common/src/viewertest/java`), game
  directory `run-viewer-uitest`.
- Recipe sync page: same command with `-PfederationUiSelection=ui.viewer-recipe-sync` (about 80 s; the world is
  fresh each run, `saves/ldtest_<pid>`, so the first sync is always a first sync). Shots: first sync, done, Rescan
  all (reads nothing), a row opened, its forced rescan (reads the whole category, changes nothing); Chinese is its own
  scenario `ui.viewer-recipe-sync-zh` (language chosen before the screen opens; switching with the screen open leaves
  the XML labels English; run it alone, as scenarios in one run share the world and its first sync). GameTest
  `aForcedRescanReadsTheWholeCategory` checks the status model (bucket states, viewer states, revision).
- `timeoutMs` in a uitest scenario covers only the next step; put it before every long wait.

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

Implemented in `JeiRecipeScanner` (2026-11-02): a JEI object that is neither `RecipeHolder` nor `Recipe` is skipped
before layout; a recipe whose outputs and byproducts equal its inputs' first stacks (multiset) is dropped; a holder
keeps its ID only if the client's copy of the server's recipes has it, a bare recipe gets the ID of the holder whose
value is that very object. This relies on NeoForge 1.21.1 sending every recipe to the client; 1.21.2+ sends only some,
so the lookup must move to the server there. Viewer UI scenarios (Mekanism + Create test pack): 5,482 -> 4,784 recipes;
the LDLib2 test display recipe that threw NoClassDefFoundError in layout is no longer laid out. Check step
`the scan kept only real recipes that make something else`; logs in worktree `build/recipe-filter-check/`.

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

## Mod change on ATM10 (measured 2026-11-02, raw data in worktree `build/mod-change-check/`)

Real processing recipes (128,612), digest C = network bytes else ingredients, A = ingredients with components.

- Noise floor: two client starts against one server start: C 97, A 49 recipes. Server restart with the same mods:
  C 4,851, A 2,495 (Universal Sawmill `sawmill:woodcutting` regenerates its ~18k no-ID recipes whenever the mod list
  changes and comes out different each time; IE potion mixer/bottling and Create automatic_* change C only).
- Update mekmm 1.4.1 -> 1.4.2 (no new items): A changes only its own recipes (+2,367); other mods 0.
- Remove iceandfire: A: its own 425 gone, other mods' 1,337 with ID + 1,073 without ID changed (shared tags), 42
  gone; matches the offline blast radius (2,474). C: 93,007 to read (72%): 73,070 recipes with unchanged ingredients
  changed their `Recipe.STREAM_CODEC` bytes, category by category all or nothing. Inferred cause: the network encoding
  writes registry entries by number, and removing a mod renumbers them.
- Consequence (assistant choice): the digest uses the datapack codec (`Recipe.CODEC` to NBT text, entries by name),
  else the ingredients; to be measured again with the implementation.
- oritech cannot be removed from ATM10: its KubeJS pack ships oritech worldgen JSON and the server stops at
  "Failed to load registries".

## Bucketed sync design (assistant plan, 2026-11-02; owner approved the direction and GUI variant 1)

Order: scanner filters (done, 9553df74) -> bucket math (done, `RecipeBuckets`, 63b0af1a) -> per-category storage
(done: `BucketedRecipes` + `ViewerRecipes.CategoryFile`, files `ae2federation_vr_<id part>_<6 hex>`; the old
whole-scan protocol replaced) -> client fingerprints + protocol + leases (done: `ViewerScans` hello/task/prints/
reads/records, `ViewerScanClient`, `JeiRecipeScanner.Printing`; Mekanism+Create UI run: hello 32 s after JEI, 4,975
recipes + 109 skipped in 56 categories, ~25 s; the old whole scan saved 4,784: all 191 extra are
`create:automatic_brewing` potions its dedupe key merged, as `AEFluidKey.toString` leaves out potion components;
file `build/viewer-storage-check/old-vs-new-categories.txt`) -> protocol + leases -> GameTests -> ATM10 -> Recipe sync tab (canvas board
RecipeSyncV1Downloader + V1First/V1Done/V1Failed).

- Fingerprint per recipe, computed on the client from `IRecipeManager.getRecipeIngredients` (no layout, ~0.07 ms):
  partition = hash(category + "\0" + "id:" + server ID), else hash of the ingredients per role without data
  components (item/fluid ID + amount, sorted); digest = hash(server ID + `Recipe.CODEC` NBT text; was STREAM_CODEC bytes, see Mod change) when THIS recipe
  encodes, else hash of the ingredients per role with components (a codec error written as `{!}`). Per recipe, never
  "give up B for the category": that would make the digest depend on JEI's order and two clients disagree.
- Cheap filters at fingerprint time (object kind, an input and an output role non-empty); the layout-based filters
  (outputs == inputs, unreadable slots, too large) only at read time, where the result is stored as a "skipped" or
  "failed" entry with its fingerprint, so the bucket still matches next time and nothing is asked again.
- Server storage: one index SavedData (categories, machines, bits) + one SavedData per category (flat file name:
  sanitized ID + short hash), entries (partition, digest, status, record); bucket digests cached; per-category output
  index, so a change re-indexes and re-saves only its category (the old whole-catalog replace stalled 35 s).
- Protocol per task = (category, bucket range of <= 8 buckets), one lease per task with an attempt number; a task's
  server bucket digests go to the client, the client answers with the fingerprints of the buckets that differ, the
  server answers with the fingerprints to read, the client lays those out and sends records (pages <= 30,000 bytes);
  each bucket is committed when all of its reads arrived. Lease expiry (no message for 120 s) or the player leaving
  hands the task to the next ready viewer; committed buckets stay, so the next one only checks them.
- A check round runs once per server start, `/reload` and Rescan all (Rescan of a category forces all its buckets);
  categories already checked in the round are not handed out again.
- Readiness: the client waits until JEI's category set and counts are unchanged for 30 s (MineColonies categories are
  absent at runtime-available and arrive within ~21 s), then says hello with (category, count, machines); later changes
  send another hello for the changed categories.
- A stored category the viewer does not list is removed only when the server has no mod of its namespace (the pack
  dropped the mod); otherwise it is kept (that viewer lacks a client mod).
- A new category gets bits from the viewer's count; a category splits its buckets on the server (from stored
  partitions) once it holds 4x what they were made for.

## Recipe sync page (built 2026-11-02, commits 3bac840a server model, 5727a20c page)

- Server keeps every category of the round (`State.round`) with per-bucket states (`ViewerSyncStatus.Bucket`), read /
  asked / dropped / changed counts, per-lease asked and received counts and start time; `revision` bumps on every
  message. `ViewerScans.status(server)` builds `ViewerSyncStatus`; the menu sends it as a tag only while the page is
  open, rebuilt at most once a second on a new revision and every 5 s anyway (players joining).
- `changed` = entries added that were not saved + saved ones that did not come back, per committed bucket; a bucket is
  UPDATED only if its digest changed. So a forced rescan (Task `force`, every bucket treated as differing, the server
  drops and re-adds) of an unchanged category reports 0 changed and MATCHED buckets.
- Rescan from the page: both-ways string binding `open/<n>/<category or *>`, n counted by the page so one press
  rescans once; gated on the menu's domain context (assistant choice). Skipped/failed entries carry the recipe ID
  (saved as `i`) so the failed row can name them.
- Client page `FederationRecipeSyncView`: one custom-drawn board (statistics-view primitives), viewer colours by
  order, the bucket grid shrinks 4/3/2 px and then shares squares (most important state wins).
- Differences from canvas V1 (assistant choices): the legend says "to check", not "to re-read" (a queued bucket may
  match); a viewer that has not said hello is "Waiting for a recipe viewer" (the server cannot tell no JEI from JEI
  still loading); the Changed column shows only what committed buckets changed, not a forecast.
- Payload registrar version 3 (Task gained `force`, Entry its ID).
- Bug found by the Chinese run (fixed with the round's `open` flag and `finish()` after every assign): JEI lists
  categories in another order per language; a listed category with 0 recipes leaves the work only when a viewer next
  looks for a task, so when it came last the round ended without `checkedAt` or its log line. GameTest
  `aForcedRescanReadsTheWholeCategory` lists an empty category last.

## Open (assistant choices, not owner decisions)

- Trust: any player's JEI replaces the catalog for the whole world.
- EMI without JEI sends nothing; recipes making only fluids/chemicals are unreachable (`recipes_for` keys by Item).
- No recipe constructor for scripts yet.
