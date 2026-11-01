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
- `client/automation/ViewerScanClient` - 4 ms per client tick, pages <= 30,000 bytes measured by encoding.
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

## Open (assistant choices, not owner decisions)

- Trust: any player's JEI replaces the catalog for the whole world.
- EMI without JEI sends nothing; recipes making only fluids/chemicals are unreachable (`recipes_for` keys by Item).
- No recipe constructor for scripts yet.
