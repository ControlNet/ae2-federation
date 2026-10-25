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
