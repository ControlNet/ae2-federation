# Addon crafting planners vs. pattern projection (static audit, 2026-10-24)

Follows `addon-crafting-planner-coverage-2026-10-10.md`. Read-only: `javap -p -c` over the compat-cache jars
(`~/.cache/ae2federation-compat/downloads`), no game runs. Scripts: a mixin-target scan (which mixin classes name
`appeng/crafting/*`, `CraftingService`, `NetworkCraftingProviders`, `ICraftingProvider`, CPU classes) and a per-method
scan of pattern lookups, provider lookups, `pushPattern` calls and `instanceof`/`checkcast PatternProviderLogic(Host)`.

## How projection meets them
`PatternProjection` is a plain `ICraftingProvider` added with `ICraftingService.addGlobalCraftingProvider`; its patterns
are the remote provider's own `IPatternDetails` objects, and `pushPattern` forwards to the real remote provider.
So anything that reads patterns from `ICraftingService.getCraftingFor` / `NetworkCraftingProviders` and providers from
`CraftingService.getProviders` sees remote patterns exactly like local ones.

## Findings per addon (versions as in the compat pins)
Planning: every special planner found reads patterns through the service, so remote patterns take part, including
cycle planning.
- Neo ECO 21.2.9 (`ECOCraftingPlannerService`, cycle graph): `ICraftingService.getCraftingFor`,
  `CraftingService.getProviders`; `patternsStillAvailable` re-checks through the service.
- Applied Enhancements 1.1.0 (Aelis planner, raw cycle variants): `ICraftingService.getCraftingFor`.
- Thunderbolt 2.0.1 and AE All Pattern 0.2.6 (`FastCraftingPlanner`): `getCraftingFor` + `getProviders`.
- Data Energistics 3.3.3 (Trinity graph): reads the `craftingProviders` field (`NetworkCraftingProviders`), which
  holds global providers too.
- Useless Mod 2.4.5.10 smart doubling, Omnisequence 2.0.7, ExtendedAE Plus 1.6.3 (`CraftingSimulationStateMixin`):
  `CraftingService.getProviders`.

Execution: scaled/batched dispatch is gated on the provider's class, so a projection gets per-copy pushes.
- Neo ECO `supportsScaledDispatch` = `instanceof PatternProviderLogic`, else `invokeNormalProvider` -> `pushPattern`.
- Omnisequence `requiresSerialDispatch` checks `instanceof PatternProviderLogic`.
- Data Energistics batching lives in its `PatternProviderLogic` mixins; it runs on the REAL remote provider once the
  projection forwards the push.
- Thunderbolt batch adapters (Useless/NeoECO/ExtendedAE Plus bridges) wrap known provider types; others push singly.
Effect: correct but possibly slower for very large orders; the fallback is confirmed by bytecode only for Neo ECO.

UI-only features that walk `getMachines(PatternProviderLogicHost)` (Lightning Tech pattern upload targets, ExtendedAE
Plus jump-to-provider / global toggles, Data Energistics provider sync) only see local providers. Expected: remote
providers belong to another network.

## Gap that remains
Compat tests submit through a plain `ICraftingSimulationRequester`, so Neo ECO's `ECOPlannerRequest` session,
Thunderbolt's engine choice, Aelis cycles etc. never run in tests. A test per planner must enter through its own
request/menu path, use a pattern that exists only on the remote network (including a cycle such as 1 seed -> 2 seeds),
assert which engine ran, and check the result and the returns.

## Planner tests added (2026-10-24, dev)
Each test makes the addon's own planner run on a pattern only the provider network has, and proves it ran.
- Neo ECO `NeoEcoCompatGameTests.ecoPlannerCycle`: gate = requester `ECOPlannerRequester(plain, new ECOPlannerOptions(cycle,
  ignoreSubst, Set))`; proof = `ECOPlanningResultRegistry.isECOOwnedPlan(plan)`. ECO-owned plans go only to an ECO
  computation system (`submitJob` gives NO_CPU_FOUND on an AE2 CPU), so the consumer builds one.
- Applied Enhancements `AppliedEnhancementsCompatGameTests.aelisPlannerCycle` (@RunsAlone): gate = global config
  `Config.ENABLE_AUTOMATIC_AELIS_PLANNER` (default false; set, then restored); proof =
  `AelisExactCraftingPlanApi.read(plan).path() == AELIS`. Plain AE2 CPU runs cycle plans (Aelis' CPU mixins).
- AE All Pattern `AEAllPatternCompatGameTests.routePlannerCycle`: gate = `CraftingRoutePolicyContext.withPolicy(DEFAULT, ...)`
  around `beginCraftingCalculation` (thread-local); no mark on plans, the scene is the detector. Plan only: its cycle
  plans stall on AE2's own CPU even fully local (control run 2026-10-24), as its own tests only plan.
- Omnisequence `nexusCrafting` now asserts Aelis made the consumer's plan: a powered Nexus on the requesting grid
  hands the root request to Aelis (no cycle support on that path, so no cycle test).
- Thunderbolt `LightningTechCompatGameTests.tianshuOrdersFromMatrix`: the Tianshu Supercomputer controller offers
  `thunderbolt:v2` (priority 0); `CraftingAlgorithmCalculationStatus.track/selected/forget` proves v2 was chosen.
- ExtendedAE Plus `smartDoublingTurnedOnLater`: found a real bug: `PatternProjection.refresh()` compared patterns with
  `equals`, so marks set on newly decoded pattern objects (Smart Doubling switched on after linking) never reached the
  consumer. Fixed: compare by object identity.
- Useless Mod `alloyFurnaceSmartDoubling`: Smart Doubling only counts providers implementing the empty interface
  `SmartDoublingCraftingProvider` (its furnace and ME Pattern Assembly). Projections now carry the real provider's
  method-less add-on interfaces (not java/minecraft/neoforge/appeng ones) through a `java.lang.reflect.Proxy`
  (`PatternProjection.registered()`, `isProjection`). Only Useless, Thunderbolt and Applied Enhancements read that
  marker (searched all compat jars and the three packs); they all push through `ICraftingProvider.pushPattern`.
- Data Energistics `DataEnergisticsCompatGameTests.trinityPlanning`: the Trinity planner runs only when the grid has a
  formed Trinity Data Core. The test builds main + CPU child + crafting child from the mod's guide snbt
  (`assets/data_energistics/ae2guide/structure/trinity_data_core_*.snbt`), the Information Exchange Depot where the
  guide shows quartz vibrant glass under the controller, controller last. Proof: plan class `TrinityCraftingPlan`
  (refusal = `TrinityDiagnosedCraftingPlan.diagnostic()`); Trinity plans leave `patternTimes()` empty, compare
  `patternFirings()` keyed by `TrinityPatternIdentity`. The crafting child is required even with no patterns: without
  it the depot's provider reports custody incomplete and the Trinity CPU never dispatches (inputs taken, job waits).
  Projection itself needed nothing: it appears in Data Energistics' provider publication index.

Scene: `CyclePlanningScene` (1 seed -> 2 seeds, store 1, order 4; AE2's planner returns a simulation plan because it
will not use a pattern for its own input). Options: consumerCpuStructure, planningWithin (thread-local gates),
planningOnly, finishing (restore global state).
