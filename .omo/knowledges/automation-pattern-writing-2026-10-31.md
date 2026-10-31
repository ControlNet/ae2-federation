# Automation: pattern writing, first batch (2026-10-31)

Programs read recipes, list pattern providers, read their patterns and write crafting-family patterns. Spec:
`automation-ae2-interaction-plan-2026-10-31.md` ("Pattern writing, first batch", "Naming").
Code: `automation/PatternCalls.java` (providers, read, write), `automation/RecipeIndex.java` (recipes by output, the
`Recipe` record), `mixin/SmithingTransformRecipeAccess.java`, `StandardLibrary` (records `Recipe`, `Provider`, typed
string `RecipeKind`).

## API

- `mc.recipes_for(item: Item) -> list[Recipe]`; `ae2.providers(network=None) -> list[Provider]`;
  `ae2.provider_at(block: Block) -> Optional[Provider]`; `ae2.provider_patterns(provider: Provider) -> list[Recipe]`;
  `ae2.write_pattern(provider: Provider, recipe: Recipe, items: dict[int, Item] = {}, substitutes: bool = False) -> bool`
  (action). `ae2.patterns` is now `ae2.craftables`.
- `Recipe`: `id: Optional[str]`, `kind: RecipeKind` (crafting, stonecutting, smithing, processing),
  `inputs: list[list[Stack]]`, `outputs: list[Stack]`, `width`, `height`. Inputs are fixed slot layouts: 9 for
  crafting (3x3 row by row, shaped recipe top-left, `[]` for an empty cell), 1 for stonecutting, 3 for smithing
  (template, base, addition), one per input for a processing pattern. `width`/`height` are 0 except shaped crafting.
- `Provider`: `id` (`"minecraft:overworld 12 64 -3"`, plus `" north"` for a part on a cable), `name`, `targets`,
  `group`, `block`, `network`, `x`, `y`, `z`, `dimension`, `slots`, `free`, `makes`, `in_terminal`.
- No union types: a selected block becomes a provider through `ae2.provider_at`; `write_pattern` and
  `provider_patterns` take only `Provider`. A provider is looked up again by `id` at each call, on the record's
  network first, then on the domain's other networks; gone -> `[]` / False. `provider_at` gives None for a provider not on a network of the domain.
- `items` is by slot number; a pick a slot does not accept, or a slot that is empty in the recipe, is a ValueError.
  Unpicked multi-choice slots take what the domain stores most of, else the first alternative. Fluid substitutes off.
- Processing recipe -> `NotImplementedError: processing patterns are not supported yet` (checked before anything).

## AE2 / Minecraft facts found on the way

- `PatternContainer` (appeng.helpers.patternprovider, not api) is what the Pattern Access Terminal enumerates via
  `grid.getMachineClasses()` + `getActiveMachines`. We use `getMachineNodes` + `node.isActive()` to keep the node
  (in-world connections, `getVisualRepresentation()` for `block`, `getService(ICraftingProvider)` for `makes`).
- `IPart` exposes no host, side or position in the API. Position comes from `PatternProviderLogicHost.getBlockEntity()`
  (every real provider has one) or the container being a `BlockEntity`; a part's side is found with
  `IPartHost.getPart(dir) == container`. Containers with neither are skipped (none known).
- `PatternProviderLogic.getActiveSides()` is private. Replicated: `host.getTargets()` minus in-world connections to a
  `PatternProviderLogicHost` or a same-grid `InterfaceLogicHost`. Do not drop all same-grid neighbours: the Molecular
  Assembler is itself a node on the provider's grid.
- `PatternContainerGroup.fromMachine` counts any block entity with an item or fluid handler. The ME Chest has a fluid
  handler, so in `NativeCraftingFixtures` the provider's targets are `ae2:chest, ae2:molecular_assembler` and its
  terminal group is "ME Pattern Provider" (several machines), not "Molecular Assembler".
- A named provider's terminal group is its own name and hides the machines; hence `targets` computed per face.
- AE2 decodes smithing patterns only for `SmithingTransformRecipe` and `SmithingTrimRecipe`; their ingredient fields
  are package-private (AE2 uses an access transformer). We read them with a mixin `@Accessor`.
- `SmithingTrimRecipe.getResultItem` is a fake trimmed iron chestplate; results with a component patch are skipped,
  which drops trims (and any addon result with data a program could not name by ID).
- NeoForge 21.1 has no `IShapedRecipe`; only `ShapedRecipe` gives width/height. Other `CraftingRecipe`s fill the grid
  in order and the write checks `matches`.
- No vanilla 1.21.1 recipe (1,291 files) has a result with components; `firework_rocket_simple` is plain.
- A datapack reload replaces the server's `RecipeManager`: the index is a `WeakHashMap<RecipeManager, RecipeIndex>`.
- `InternalInventory.addItems` stacks onto non-empty slots first; insert with `insertItem` into an empty slot.
- Inserting into `getTerminalPatternInventory()` triggers the provider's `updatePatterns()` by itself; the crafting
  service sees the new pattern a tick later (the test waits on `ae2.craftable` before `ae2.craft`).
- Blank pattern key from the API: `AEItemIds.BLANK_PATTERN`.

## Tests

- GameTests in `ProgramHostGameTests` (manifest `programs.host-recipes-for` ... `programs.host-dry-pattern`, 9):
  recipes, providers/provider_at, write + craft end to end, stonecutting/smithing/birch pick, no blank, full provider,
  processing raises, wrong item, dry run. Scene helper `patternScene`: interface at (1,4,2) facing down, reach 2 to the
  provider. `NativeCraftingFixtures(helper, cpu, withPattern=false)` starts the provider empty.
- Disabling the blank-pattern checks and the dry-run return made the no-blank and dry-run tests fail (verified).
- `ProgramSync` "dry" passes the interface ID: a dry-run test function needs `port: Interface`.
- In test programs a list or dict literal of strings is `list[str]`; annotate `list[Item]` / `dict[int, Item]`.

## Not done

- Processing patterns (second batch). A crafting-family pattern whose recipe the index skips reads back with its kind
  and id but condensed inputs, and writing it is a ValueError.
- No test with an addon provider or a part provider (`id` side suffix untested in game).
- Costs not measured: the first `RecipeIndex` build on a large pack (every recipe, `getItems()` of every ingredient),
  and `ae2.providers()` computing `targets` (capability lookups per face) for every provider on each call.
- Java pitfall met: `found(null)` with overloads `found(Object)` and `found(List)` picks the List one; renamed.
