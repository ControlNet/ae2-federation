# Neo ECO smart doubling across Federation (2026-10-25)

Neo ECO 21.2.10's "ECO 智能翻倍" is dispatch-time batching in its own CPU, unlike ExtendedAE-Plus' Smart Doubling
(plan-time scaled pattern copies, already covered by `ExtendedAEPlusCompatGameTests`).

## How Neo ECO decides

- `cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingPatternDispatcher.supportsScaledDispatch(request, provider)`
  reads only `request.pattern()` and the provider. It accepts:
  - `ECOParallelCraftingProvider` (Neo ECO's public API, `cn.dancingsnow.neoecoae.api.me.provider`);
  - a provider whose "logic" is AE2's `PatternProviderLogic` (with Neo ECO's `PatternProviderLogicAccessor` mixin) or
    an Advanced AE provider. The logic is the provider itself, or what a public `getLogic()` found by reflection
    returns;
  - AE2LT providers, and Mek Energistics through its `Contract`.
  - It refuses when external blocking is on (`ECOExternalProviderBlocking`), when the unwrapped pattern is not the
    request's own object, or when a provider reports a non-NORMAL `getProviderMode`/`getWirelessDispatchMode`/
    `getPushDirection`.
- The scaled push itself still goes through `ICraftingProvider.pushPattern` (ECOSingleCraftingExecutor), with a scaled
  pattern such as `ECOProcessingExecutionPattern(pattern, n)` and n times the inputs.

## Measured (compat profile `neoecoae`)

`NeoEcoCompatGameTests.computationSystemSmartDoubling`: the consumer's ECO CPU runs 16 runs of a projected
cobblestone -> stone pattern from an AE2 Pattern Provider on the provider network.

- Neo ECO's own check: real provider `true`, `PatternProjection` `false`.
- 16 pushes crossed the projection (statistic `pushed`): no batching, but the job completes exactly.

`laneSmartDoubling`: a Federation Pattern Provider's `NativeProviderLane` (a `PatternProviderLogic` subclass) on the
CPU's own network passes Neo ECO's check.

`NeoEcoScaledDispatch` (compattest) calls the package-private check reflectively; Neo ECO's jar is an automatic module,
so `setAccessible` works.

```bash
python3 tools/compat_run.py --accept-eula neoecoae --tests computationSystemSmartDoubling,laneSmartDoubling
```

## Fix (2026-10-25)

- What AE2 holds for a projection shows the public `ProjectedProviderLogic.getLogic()` only when the real provider is
  AE2's exact `PatternProviderLogic` class or a `NativeProviderLane`: `ProjectionShowingLogic` (direct delegation, no
  reflection) without add-on markers, the marker proxy with them. Other projections are unchanged (no `getLogic` at
  all). Neo ECO then batches through the projection: 16 runs in 5 pushes, return ledger settled.
- Mode parity: Neo ECO also refuses providers whose class answers non-NORMAL to `getProviderMode`,
  `getWirelessDispatchMode` or `getPushDirection`. Only AE2LT/ae2ltpp logic classes declare those (scan of every compat
  jar, 2026-10-25), and their projections show no logic, so the projection and the real provider get the same answer.
- Why not every logic: Neo ECO's `ECOAe2LtDirectDispatch` (class name `OverloadedPatternProviderLogic`, `pushChunk`)
  and `ECOOmniSequenceDirectDispatch$Delegate.unwrap` push into the logic they find directly, which would skip the
  projection and the return ledger. ExtendedAE-Plus' reflective `getLogic` users are menu packets on block entities.
- If the proxy cannot combine the interface with an add-on's markers (class loaders), the markers win.
- Regression: dev gate 299/299, every addon profile green (`addons-all` 111/111, `useless-mod-addons` 111/111).
- Advanced AE (same day): Neo ECO's `ECOAdvancedAEPatternScaling` only reads AdvancedAE's
  `AdvPatternProviderLogic` (isProvider, isBlocking, config) and scales the pattern; the push goes through
  `provider.pushPattern`. No compat jar subclasses that logic. The projection now also shows a real provider whose
  class name is exactly `net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic` (by name: no compile
  dependency). Before: real `true`, projection `false`, 16 pushes; after: 5 pushes, ledger settled.
- Profile `neoecoae-advanced-ae` (AdvancedAE 1.6.12 + Neo ECO 21.2.10; groups advanced-ae, neoecoae and
  `neoecoae-advanced-ae`) is the only one with both mods on a current Neo ECO; `useless-mod-addons` has Neo ECO
  21.2.0, `nast` 21.2.9.

## Crafting system fast path (same day)

- Measured: consumer computation system, provider crafting system L4 (one worker), 512 planks -> sticks (256 crafts).
  Federation 2400+ ticks, one push every 10 ticks; both networks joined into one Grid 92 ticks.
- Why 10 ticks: the crafting system gives each worker one thread (`getThreadObjectCapacityForWorker` returns 1). An
  ordinary `pushPattern` puts one craft into it; a thread advances 10 progress per tick (more when overclocked) to 100.
  The parallel cores' capacity is used only by the fast path's `pushBatch`. Neo ECO's CPU can push 64 times a tick.
- The fast path: `ECOFastPathFacade.supports/resolveProvider` take a provider that is `instanceof
  ECOFastPathDispatchProvider` (no registry; `ECOParallelCraftingProviders` is only for the Large Integrated Working
  Station). `eco$prepareFastPath(ECOBatchDispatchContext)` returns a `Preparation` record whose `dispatch` predicate
  takes a `Batch` (craftCount, inputTotal, outputTotal, remainingTotal, exactInputTotal).
- Job id trap: with the context's `craftingJobId`, the crafting thread inserts outputs only into the owning CPU through
  its own grid's `ECOCraftingOutputRouter.neoecoae$insertIntoCpuForJob`, waits for it, and after 2400 ticks recovers
  them to storage. Across networks that CPU is elsewhere. Ordinary pushes through a projection were safe because the
  projection is not `ECOJobAwareCraftingProvider` (job id null).
- Fix: `NeoEcoBatching` (reflection, as `CraftingCpuLifecycle` does for Neo ECO). The marker proxy also implements the
  real provider's `ECOFastPathDispatchProvider`; `eco$prepareFastPath` rebuilds the context without the job id, asks
  the real bus, and wraps `dispatch` through `PatternProjection.pushBatch`, which owes `outputTotal` and
  `remainingTotal`. `eco$prepareExactFastPath` answers null (exact batches require the job id).
- After: 256 crafts in 9 pushes, 96 ticks; ledger settled. `craftingSystemThroughput(Joined)` in the neoecoae group.
- Exact orders (`ECOExactCraftingPlan`, full virtual mode): with no exact preparation the dispatcher falls through to
  the ordinary fast path. `craftingSystemExactOrder`: 9 pushes, 96 ticks; the log shows Neo ECO's `[big-order] exact
  dispatch diagnostic reason=provider-rejected-or-long-only` for the projection.
- Submit exact plans as Neo ECO's `CraftConfirmMenuMixin` does: `ECOCraftingCPU.getCluster().submitJob(grid, plan,
  source, null)`. Through `CraftingService.submitJob`, Thunderbolt Core 2.0.1 (AE2 Lightning Tech) cancels at HEAD
  with `CPU_OFFLINE` for any plan that is not `appeng.crafting.CraftingPlan` when none of its own CPUs takes it
  (`ExtendedCraftingCpuServiceMixin`). That failure used the test's full 2400-tick timeout and pushed the serial
  `addons-all`/`useless-mod-addons` runs past their 900 s profile timeout (it looked like a hang).

## Large Integrated Working Station (same day)

- `LargeWorkstationPatternProvider` (a `PatternProviderLogic` subclass) implements `ECOParallelCraftingProvider`
  (`eco$getAvailableParallelSlots`, default `eco$pushPatternBatch(details, holders, crafts, UUID jobId)`). Neo ECO
  21.2.10 finds it through the registry `ECOParallelCraftingProviders.find`, which falls back to instanceof; 21.2.0
  (`useless-mod-addons`) has no registry class, so the test helper falls back to instanceof itself.
- The workstation also returns outputs only to the job's CPU when given a job id, so `NeoEcoBatching.Parallel` pushes
  with null and owes outputs x crafts plus the pattern's container items.
- Result: 64 energized crystal crafts in 1 push, 108 ticks (joined 107). Before: 64 pushes, also 108 ticks: the
  CPU pushes up to 64 a tick, so the gain is the push count (statistics, ledger entries), not time at this size.
- Test scene: `MultiBlockPlacementService.preview(level, absoluteControllerPos, state, NEMultiBlocks
  .LARGE_INTEGRATED_WORKING_STATION, 1, false)` returns absolute `worldPos` values (the controller excluded): place
  them with `level.setBlock`, not `helper.setBlock`. Try each horizontal facing until the interface lands at
  `PROVIDER`; then `rebuildMultiblock()` on the controller. Recipes need fluid: water goes in a 4k fluid cell in the
  consumer's ME Chest (`Machine.inputCell`).
