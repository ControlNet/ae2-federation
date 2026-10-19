# Addon crafting planner compatibility coverage

Date: 2026-10-10. Read-only audit; no new game runs or compatibility fixes performed.

## Evidence inspected

Production profiles pin AE2 19.2.18, Neo ECO AE Extension 21.2.1, AE2 Lightning Tech 2.1.1,
and Thunderbolt Core 2.0.1. Addon JARs were downloaded to temporary storage from the URLs in
`tests/compat/mods.json` and checked against their SHA-512 pins. Mixin manifests and relevant classes
were inspected with `javap -p -c`, avoiding claims based only on upstream branch names.

- Neo ECO's `ae2.crafting.CraftingCalculationMixin.captureNetworkPlanningMode` returns immediately
  unless the simulation requester implements `ECOPlannerRequest`. Otherwise it captures inventory and
  creates an `ECOCraftingPlannerService` session with options including cycle planning, ignoring pattern
  substitutions, and fuzzy item IDs. It also patches crafting service, tree nodes, plans, and CPU execution.
- Thunderbolt's `ae2.crafting.CraftingCalculationMixin` supports selected planning-engine candidates,
  captured planning input, fallback to vanilla, and cancellation/budget control. Its `CraftConfirmMenuMixin`
  hooks calculation tracking and selected-engine summary. Lightning Tech adds custom CPU execution and
  provider hooks. Do not infer all special planning paths execute just because these mixins load.

## Current Federation behavior

`CraftingProjectionService` registers real remote providers through AE2's global-provider API.
Federation does not replace `CraftingCalculation`; its mixin manifest has no direct calculation/service
planner injection. This favors compatibility but is not proof that every addon planner enumerates projected
providers or understands every special pattern identically.

`CraftingReturnRouter.waiting` handles unknown addon requested amounts: it inspects active CPUs and uses
the return debt as a cap when a non-native CPU is busy, AE2 reports the key as requested, and native CPUs
have no positive waiting amount. This is execution/return compatibility, not planner coverage.

## Existing evidence and its limits

`docs/compatibility/production-runs.md` records Lightning Tech profile 30/30, Neo ECO 24/24, and the combined
addons profile 86/86. These are historical recorded results, not results of this audit. Tests include
Neo ECO computation as the only CPU and crafting/storage multiblocks, Pigmee Mental Math Unit execution,
cancel/disconnect, and Tianshu ordering from a Matter Warping Matrix.

However, `AddonCraftingScene.begin` constructs a plain `ICraftingSimulationRequester`, calls
`beginCraftingCalculation`, and submits via `submitJob`. It does not implement `ECOPlannerRequest`.
Thus this path does not activate the inspected Neo ECO planner session entry. Current addon tests do not
explicitly select or assert a Thunderbolt planning engine either. CPU execution success cannot establish
custom planner success, and UI/menu-specific planning paths are not covered by direct service submission.

Recommended next verification: explicitly activate each planner through its supported request/menu path,
assert the actual selected engine (including no unnoticed fallback), compare local and projected remote
patterns/materials, and exercise representative cycle/substitution/batch cases plus cancellation and returns.
Combined-addon planning should be tested too. No present incompatibility is established by this gap alone.
