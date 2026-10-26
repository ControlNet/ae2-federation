# P0 dispatch coverage (2026-10-26)

Result of P0 in [remote-crafting-cpu-route-plan-2026-10-26.md](remote-crafting-cpu-route-plan-2026-10-26.md).

## How to run

```sh
python3 tools/compat_run.py --accept-eula baseline data-energistics neoecoae advanced-ae ae2-lightning-tech \
  neoecoae-advanced-ae addons-all -j 7 -p 4 --run-suffix probe --jvm-property ae2federation.dispatchProbe=true
python3 tools/dispatch_probe_report.py baseline-probe data-energistics-probe neoecoae-probe advanced-ae-probe \
  ae2-lightning-tech-probe neoecoae-advanced-ae-probe addons-all-probe
```

Add `--jvm-property ae2federation.compat.joinBeforeOrder=true` (and another suffix) for the joined control.
`addons-all` needs `-p 4`; with `-p 1` it passes the 900 s deadline. Joined `addons-all` still times out, because
many scenes fail their federation-only assertions once joined and wait for their own timeouts.

## What the probe records

- Executors: AE2 `CraftingCpuLogic.executeCrafting`, Advanced AE `AdvCraftingCPULogic`, Neo ECO
  `ECOCraftingCPULogic.invokeNormalProvider` (single, batch, plain) and `pushPatternForJob`, LT
  `Ae2LtTimeWheelCraftingCpuLogic.tryPushPattern`, Thunderbolt `BatchExecutor.runBatchOnly` (full descriptor needed,
  the method is overloaded), DE `CraftingDispatchCommitter.commit`.
- Scope: one `@WrapMethod` on `CraftingService.onServerEndTick` pushes the ticking service; DE's
  `GridCraftingDispatchParticipant.dispatchStep` runs after the tick and pushes its own `craftingService`.
- Provider side: `PatternProviderLogic.pushPattern`, `PatternProjection.pushPattern` and `pushBatch`, DE
  `PatternProviderBatching.pushExpanded`/`pushExpandedToTarget`.

## Results on the 2026-10-26 pins (federated, all tests pass)

| Profile | Tests | Projection pushes | Out of scope | Scope service holds the projection |
|---|---|---:|---:|---|
| baseline | 20/20 | 11 | 0 | 11/11 |
| data-energistics | 30/30 | 35 | 0 | 35/35 |
| neoecoae | 42/42 | 39 + 17 batch | 0 | 56/56 |
| advanced-ae | 34/34 | 30 | 0 | 30/30 |
| ae2-lightning-tech | 35/35 | 31 | 0 | 31/31 |
| neoecoae-advanced-ae | 37/37 | 52 + 17 batch | 0 | 69/69 |
| addons-all | 131/131 | 167 + 17 batch | 0 | 184/184 |

The same holds on the previous pins (Neo ECO 21.2.10, DE 3.3.3, ExtendedAE 2.2.39).

## Conclusions

- Every push happened inside a crafting-service scope, and the scope named the right consumer every time. Scope
  attribution covers all tested CPUs with two generic hooks plus one DE scope.
- Per-executor hooks miss Neo ECO's batch paths: the 17 batch pushes came through
  `ECOCraftingFastPathDispatcher.tryDispatch` -> `ECOBatchExecutor.executePrepared` (16) and
  `ECOCraftingProviderDispatcher.tryDispatchOrdinaryBatch` (1), which no executor probe saw. Hooking each Neo ECO
  dispatch path would mean about 11 mixins; the scope plus the batch protocol (`eco$pushPatternBatch`, fast-path
  `Preparation.push`) covers them.
- Per addon the CPU route needs about: Neo ECO 1 (batch protocol), Thunderbolt 1 (`IBatchCraftingProvider.pushBatch`),
  DE 2 (participant scope, `pushExpanded`, which writes `sendList` without `pushPattern`), plus one per custom provider
  class that does not extend `PatternProviderLogic`.
- The Thunderbolt executor row reports 0 runs while the projection saw 7 pushes from it: the probe reads
  `BatchRunResult` counts that are 0 for those runs. Probe artefact, not a coverage gap.
- Joined runs: 0 out of scope; scope-holds misses match pushes made before the join. Batch samples there are few.
- A provider object registered on two grids at once, and where its outputs return:
  [remote-crafting-dual-registration-spike-2026-10-26.md](remote-crafting-dual-registration-spike-2026-10-26.md).

## Runner fix found on the way

Vanilla `GameTestInfo.tick` notifies listeners only when `tickInternal` returns normally. A test throwing anything but
`GameTestAssertException` was failed by the runner without notifying, so the next batch never started and the server
waited for the deadline. `GameTestSequenceFailureMixin` (always on in the compat-test jar) fails such a test through its
listeners.
