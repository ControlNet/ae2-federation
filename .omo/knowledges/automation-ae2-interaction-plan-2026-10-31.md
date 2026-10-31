# Automation: deeper AE2 interaction (plan, 2026-10-31)

Decisions by the owner on extending Federation Automation beyond storage, crafting requests and redstone.

## Decided

- **Crafting control: option 1.** `ae2.cpus(network)` lists CPUs (name, busy, storage, co-processors,
  selection mode, job and progress from `CraftingJobStatus`); `ae2.craft(..., cpu=...)` may name a CPU.
  No job priority (AE2 has none; queueing and preemption were rejected).
- **Existing bug to fix first:** `ProgramCrafting.tick()` passes each free CPU as the explicit `target` of
  `ICraftingService.submitJob`. AE2 only runs `findSuitableCraftingCPU` when `target` is null, so programs
  ignore `CpuSelectionMode` (they take PLAYER_ONLY CPUs) and AE2's size check. Fix: let AE2 choose with a
  machine action source; `freeCpus`/`couldRequest` must count only machine-selectable CPUs.
- **Device settings: one generic function** (`ae2.set_setting(block, name, value)`, `ae2.settings(block)`),
  not one function per setting. Setting names complete from `appeng.api.config.Settings` (terminal display
  settings left out); the value argument completes from the chosen setting's values (needs a new
  argument-dependent completion). Runtime checks against the device's own `IConfigManager.getSettings()`,
  which also covers addon settings (AE2's `Settings.register` is private, so addons build their own
  `Setting` objects). Priority via `IPriorityHost`, filter slots via `IConfigInvHost`.
- **P2P: do it.** `P2PTunnelPart` is AE2 internal (not API). Relinking changes Federation cable capacity
  (a P2P pair counts as C), so a relink can push a domain over its limit; test and document that.
- **Terminals: not now.**

## Recipes for automatic pattern writing

- Programs run unattended, so the runtime must not depend on a player being online. Recipe viewers (JEI,
  EMI, REI) run only on clients; at most they could feed a one-off import stored in the world.
- AE2 itself encodes processing patterns from JEI/EMI on the client: `itemlists/EncodingHelper.encodeProcessingRecipe`
  turns viewer slots into `GenericStack`s and sends `InventoryActionPacket`. The server already accepts
  client-chosen processing pattern contents; crafting patterns are checked against the recipe on decode.
- Server source: `RecipeManager`. Encoding helpers in `PatternDetailsHelper`: `encodeCraftingPattern`,
  `encodeStonecuttingPattern`, `encodeSmithingTablePattern`, `encodeProcessingPattern`.
- Proposed layers for processing recipes: (1) raw codec JSON, always; (2) best-effort `inputs`/`outputs`
  from common formats (vanilla Ingredient/ItemStack, NeoForge SizedIngredient/FluidStack/
  SizedFluidIngredient) with a `complete` flag; (3) learn from existing patterns in the network.
  Layer 2 is pending a coverage measurement on ATM10 recipes.

## Done

- ce1789c4: programs submit without a CPU target (AE2 chooses); `ae2.free_cpus` counts machine-usable CPUs.
- 427f5a52: `ae2.cpus(network)` with the `Cpu` record and `CpuMode`; `ae2.craft(..., cpu=name)` runs on the
  smallest free CPU of that name the job fits, never a PLAYER_ONLY one; unknown name is a ValueError. A CPU's
  name is what its units were renamed to in an anvil. The GameTest sets the other CPU to MACHINE_ONLY, because
  with two equal ANY CPUs AE2 happened to pick the named one anyway and the test proved nothing.

## Recipe coverage measurement (2026-10-31)

Runtime dump of every recipe's codec JSON on ATM10 8.3, NAST 2.2.1 and CUS2 12.9.0 server packs (throwaway dump
mod, without our mod), checked by a structural recogniser of the common formats:

- ATM10: 94,364 recipes, 374 types; 42,017 machine recipes (17,320 of them `sawmill:woodcutting`, always complete).
  Common formats alone: 51% strict, 73% with lenient modifiers, quantity aliases and the
  `getIngredients()`/`getResultItem()` fallback (woodcutting excluded); 84.0% of recipes that show resources on both
  sides. NAST 64.1%, CUS2 82.9% on the same measure.
- Biggest gaps: Mekanism chemicals (969 ATM10, 1,158 NAST), chance/weight wrappers in many key spellings (1,628),
  Modern Industrialization `amount` on items (2,455), a bare `fluid` key whose role differs by mod (input for
  casting, output for the Productive Bees centrifuge), bare id strings.
- Adding chemicals, a `fluid` role, bare ids and `#tag` fluid strings would reach about 95% / 91% / 93%
  (estimate), but the `fluid` role would mislabel up to about 256 recipes.
- Meaning is the real risk, not shape: 7.8% of machine recipes have chance outputs in five forms, some at recipe
  level (Mekanism `secondary_chance`) where a leaf view drops them; 5.6% have non-consumed inputs with no common
  format; 12,664 recipes put an ingredient list under a singular key where alternatives and several inputs look
  the same.
- `getIngredients()` is empty for 28% of machine recipes and `getResultItem()` for 19% (Mekanism implements
  neither); field-path roles agree with them on 75.8% of leaves and are right where they disagree.
- Codec encoding fails for 71 of 94,364 recipes, both by returning an error and by throwing; key types by the
  serializer, since `getType()` returned unregistered objects for two addon types.
- A processing pattern whose output list holds a chance output can leave an AE2 job waiting for something that
  never comes, so normalized outputs must mark chances and encoding must keep only guaranteed outputs by default.

## Pattern writing, first batch (owner, 2026-10-31)

- First batch: crafting-family patterns only; processing patterns later.
- `mc.recipes_for(item)` gives `Recipe` records (id, kind, output, per-slot accepted items, shaped size).
- `ae2.write_pattern(provider, recipe, items=None, substitutes=False)` (owner's choice over a separate pattern
  description step): the recipe's kind decides the pattern kind; takes one blank pattern from the domain's storage
  and writes straight into the provider. Processing recipes raise "not supported yet" in the first batch.
- Reading a provider's patterns gives the same `Recipe` records (processing patterns: no id, inputs/outputs only).
- `ae2.providers(network)`: lists the network's pattern providers as the Pattern Access Terminal does
  (`PatternContainer`), so programs reach providers without an Automation Interface in front of each.
- Crafting patterns run in a Molecular Assembler next to the provider; players build that.
- Open: stonecutting and smithing in the first batch (recommended); how to express a processing pattern that comes
  from no recipe (second batch).
- Order: after B (device settings) is reviewed and committed, since both touch the library, lang and guide files.
- Owner agreed (2026-10-31): stonecutting and smithing join the first batch. `Provider` record fields: id, name,
  target (item of the machine it faces, from `PatternContainer.getTerminalGroup()`), group, block, network,
  x/y/z/dimension, slots/free, makes, in_terminal. Full pattern contents through a separate
  `ae2.provider_patterns(provider)`; `write_pattern` accepts a `Provider` or a selected `Block`.
- Naming (owner, 2026-10-31): reads stay nouns and actions verbs, no `get_` prefix (Python style). Rename
  `ae2.patterns` (list of craftable resources) to `ae2.craftables`, pairing with `ae2.craftable`, so that
  `ae2.provider_patterns` is the only "patterns" read and returns real patterns. Done with the pattern batch.
