# Dual-registration spike (2026-10-26)

The main risk of P3 in [remote-crafting-cpu-route-plan-2026-10-26.md](remote-crafting-cpu-route-plan-2026-10-26.md):
one real provider object registered on its own Grid and on a consumer's.

## AE2 Pattern Provider, measured

`CpuRouteSpikeGameTests.dualRegisteredPatternProvider` (group `cpu-route-spike`, not part of `all`): two plain AE2
networks P and C, no cable or Federation device between them. P has a Pattern Provider with a Molecular Assembler, a
chest and a CPU; C has a chest and a CPU. P's `PatternProviderLogic` is added to C with
`ICraftingService.addGlobalCraftingProvider`.

```sh
python3 tools/compat_run.py --accept-eula baseline --tests dualRegisteredPatternProvider --run-suffix spike
grep AE2F_SPIKE neoforge-1.21.1/build/compat/runs/baseline-spike/logs/latest.log
```

| Topic | Observed |
|---|---|
| Registration | C offers the pattern 1 tick after `addGlobalCraftingProvider`. |
| Push | C's CPU pushes into the provider; the assembler crafts. |
| Return | The 4 sticks land in P's chest, none in C's; C's CPU waits for them forever. The provider returns through its own node's Grid. |
| Pattern change | 20 ticks after the pattern leaves the provider, C still offers it; `refreshGlobalCraftingProvider` on C drops it. `requestUpdate(mainNode)` reaches P only. |
| Concurrent orders | P and C order in the same tick; both pushes go through. P's job finishes, C's waits; P's chest ends with 8 sticks. C's outputs are ordinary items on P, which P's CPU may take for its own job. |
| Broken provider | C still offers the pattern 20 ticks later. An order from C is refused (the provider's node is inactive): cancelling gives back both planks, nothing is lost. |

## Neo ECO, from bytecode (21.2.11)

- `ECOLargeIntegratedWorkingStationBlockEntity.deliverBatchOutputs` and `ECOCraftingThread.ejectOutputs` (crafting
  system workers) take `getMainNode().getGrid().getCraftingService()`, their own Grid, and call
  `ECOCraftingOutputRouter.neoecoae$insertIntoCpuForJob(jobId, ...)` (a mixin on AE2's `CraftingService`). That looks
  the job up only among that Grid's CPUs; what no CPU there takes goes into the same Grid's storage.
- So passing the real job id to a provider on P does not deliver to a job on C. Not run in game.
- Upstream candidate: resolve the job's CPU through the level-wide `ECOCraftingJobLifecycle` registry instead of the
  own Grid.

## Lightning Tech, from its API

`DeferredCraftingProvider.pushPattern(details, inputs, OutputSink)` hands the provider a sink bound to the CPU's job.
An LT deferred provider registered on C would enqueue its outputs straight into C's CPU, with no network hop. Only
LT's own machines implement it.

## What P3 needs

1. A return path stays necessary: outputs of a real provider land on its own Grid (AE2, Neo ECO with or without the job
   id). The ledger and return router carry over; only LT deferred providers can skip them.
2. Fan out pattern changes: on every provider refresh on P, call `refreshGlobalCraftingProvider` on each consumer that
   holds it.
3. Remove registrations when the provider leaves P (`globalProviderRemoved` already sees this), or C keeps offering a
   dead provider. Pushes into it fail safely, so this is a correctness of the offer, not item loss.
4. Outputs owed to C are fungible on P while in transit: P's own CPUs can take them. The ledger must keep C's debt
   until it is delivered, as it does for projections today.
