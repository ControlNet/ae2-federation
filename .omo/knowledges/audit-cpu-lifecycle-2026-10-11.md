# CPU lifecycle audit, 2026-10-11

Base: `2f0f99fc`, isolated branch `audit/cpu-lifecycle-20261011`.

## Confirmed findings

- `CraftingProjectionService.end` treated the first different crafting ID at an addon block anchor as completion.
  Neo ECO 21.2.1's threading core has `ECOCraftingCPU[getCPUThreads()]`: several real CPUs share one owner block.
  Two independent native planning calculations and submissions reproduce a RUNNING job becoming DONE while both
  CPUs still wait for their remote processing output. Repair scans all exact crafting IDs, keeping missing jobs
  unresolved instead of interpreting a different slot as completion.
- Advanced AE 1.6.12's `AdvCraftingCPU` independently implements `ICraftingCPU`. Without a lifecycle adapter its
  cancellation left a persisted unknown flag and prevented ordinary-storage delivery of already dispatched output.
  Existing `quantumcorecancel` failed with owed 2 instead of 0. Adapter reads public `getSrc().machine()` for a
  dimension-bearing block anchor, public `craftingLogic`, and public `getLastLink/getWaitingFor`; no private cluster
  access or alternate task execution is introduced.
- Neo ECO retires completed CPUs. A standalone AE2 CPU-side `CraftingLink.markDone()` does nothing without a nexus,
  so retaining the link alone cannot prove completion. Neo ECO's own persisted
  `ECOCraftingJobLifecycle.isTerminated(Level, UUID)` provides native terminal evidence after retirement. Its UUID
  comes directly from `ExecutingCraftingJob.link.getCraftingID()` in `ECOCraftingJobLifecycleController`.

## Restore safeguard and compatibility boundary

Neo ECO can remove a cancellation marker in `resumePersistedJob`. Therefore the terminal hook requires a formed
owner and no pending entries in its private `deferredInit` array. A quarantined failed restore suppresses inference
for all that owner's slots. All reflective dependencies are centralized in `CraftingCpuLifecycle` and pinned to
Neo ECO 21.2.1. Missing API, inaccessible state, or unknown CPU implementations stay unresolved.

`JobEnd.TERMINATED` is additive saved state for a native addon-proven ending whose completion/cancellation outcome
is not exposed by that API. Existing saves load; older builds cannot load this new enum value after it is written,
so downgrading such a save is unsupported. Debts are still discharged only by actual delivery.

## Validation and evidence distinctions

- Real addon JARs; native planners, real concurrent CPU submissions and actual return storage routing.
- `CpuLifecycleAuditScene` uses an explicitly hand-operated chest processing machine, not a production furnace.
- Retirement regression saves/reloads Federation SavedData and removes that job's transient link to model restart
  observation. This is not a process restart or real independent CPU chunk-unload test.
- Deferred guard regression deliberately injects one empty pending NBT tag and temporarily marks the real owner
  unformed, restoring each in `finally`. These are explicit fault injections, not natural unload evidence.
- Initial test development errors (reusing the same plan object and reading a block entity on the asynchronous
  calculator thread) were corrected; those runs are not treated as bug reproductions.
- Clean baseline reproduction: `/tmp/audit-cpu-anchor-red2.log`, Neo ECO 28/29, running shared-anchor job returned DONE.
- Baseline Advanced AE reproduction: `/tmp/audit-cpu-red.log`, Advanced AE 28/29, cancellation debt stayed at 2.
- Intermediate fixes: `/tmp/audit-cpu-green.log`, Neo ECO 29/29 and Advanced AE 29/29.
- Final command: `python3 tools/compat_run.py --accept-eula neoecoae advanced-ae ae2-lightning-tech -j 1`.
  Final result: Neo ECO 30/30 (56 s), Advanced AE 29/29 (57 s), Lightning Tech 30/30 (64 s), exit 0.
  Full log `/tmp/audit-cpu-final.log`; JSON receipts and production source hashes in
  `.omo/evidence/audit-cpu-2026-10-11/`.

Existing consumer-wide unknown lifecycle markers are not retroactively erased merely because a newly supported CPU
is now visible. The old unknown job cannot be reconstructed from that observation; retained ambiguity remains the
conservative behavior. The new adapters prevent that ambiguity for newly observed supported jobs.

Independent read-only review by the return-transfer audit worker checked exact-ID matching, native terminal marker
call paths, deferred restore and unformed guards, test evidence, and save-format documentation; accepted without
blocking findings.
