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
