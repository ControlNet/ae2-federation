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

## Neo ECO, from source (21.2.11, upstream main 1c1e084b)

- The job id reaches a machine only through the batch protocol (`ECOBatchDispatchContext.craftingJobId()`); a plain
  `pushPattern` carries none. With Neo ECO installed, C's AE2 CPUs dispatch through that protocol too.
- `ECOLargeIntegratedWorkingStationBlockEntity.deliverBatchOutputs` and `ECOCraftingThread.ejectOutputs` (crafting
  system workers) take their own Grid's crafting service and call `ECOCraftingOutputRouter.neoecoae$insertIntoCpuForJob`
  (a mixin on AE2's `CraftingService`), which looks the job up only among that Grid's CPUs.
- When no CPU there has the job, the outputs are held, not stored: the workstation stops with `CPU_INSERT` until the job
  is terminated; a crafting thread waits (`WAITING_FOR_OWNER`) and recovers to storage after 2400 ticks. A job on C
  waits for those outputs, so a real Neo ECO provider registered on C stalls (workstation) or stalls for 2400 ticks
  (crafting system). Not run in game. The projection avoids it today by pushing without the job id (`NeoEcoBatching`).
- `ECOCraftingJobLifecycle` is a server-wide set of terminated job ids only; it cannot locate a live job's CPU.
- P3 therefore needs the Neo ECO batch-protocol hook to drop the job id for mirrored providers, whatever upstream does.

## Lightning Tech, from its API

`DeferredCraftingProvider.pushPattern(details, inputs, OutputSink)` hands the provider a sink bound to the CPU's job.
An LT deferred provider registered on C would enqueue its outputs straight into C's CPU, with no network hop. Only
LT's own machines implement it.

## What P3 needs

1. A return path stays necessary: outputs of a real provider land on its own Grid (AE2, Neo ECO without a job id). The
   ledger and return router carry over; only LT deferred providers can skip them. Neo ECO batches pushed with a job id
   are held on P instead (see above), so the job id must be dropped for mirrored providers.
2. Fan out pattern changes: on every provider refresh on P, call `refreshGlobalCraftingProvider` on each consumer that
   holds it.
3. Remove registrations when the provider leaves P (`globalProviderRemoved` already sees this), or C keeps offering a
   dead provider. Pushes into it fail safely, so this is a correctness of the offer, not item loss.
4. Outputs owed to C are fungible on P while in transit: P's own CPUs can take them. The ledger must keep C's debt
   until it is delivered, as it does for projections today.
