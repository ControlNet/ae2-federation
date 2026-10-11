# Accepted push lifecycle audit — 2026-10-11

Baseline: `2fa70d74f271a69e80baff583940d4debd3a65ed`. The second audit round follows a complete green local and remote
gate; green tests did not exclude reentrant provider callbacks.

## Confirmed defect

`PatternProjection` invoked the real provider before recording return responsibility. `CraftingProjectionService.owe`
added debt, then looked up the current projection entry to discover the consumer CPU and executing Grid. A successful
provider callback can already have removed that entry by changing physical topology. The early return then skipped
both CPU lifecycle observation and return-router installation, even though the machine accepted the input.

The regression performs a real native calculation and CPU submission on real AE2 Grids. An explicitly named independent
provider test double consumes one real input and removes the actual Switch during its accepted push. Before repair,
the ledger owes one output but has zero watched jobs (expected one). Storage sharing is never configured. This callback
is fault injection, not evidence that a named addon performs this exact action.

## Repair

Prepare a read-only snapshot of busy CPU observations before invoking the external provider. Only a true acceptance
commits that snapshot and debt; rejection still writes neither. Capture the executing Grid from the installed
projection's context rather than finding a possibly removed map entry afterward. Reinstall the return router when
the accepted dispatch newly requires it. Each push keeps its own snapshot, including under reentry.

AE2 continues to plan, dispatch, wait and finish the job. The snapshot does not predict completion, assign exact
job-to-output provenance, or introduce a scheduler. Reading CPU state on an attempted push is necessary before the
callback can change that state; unlike the old implementation, even a subsequently rejected attempt pays this read
cost, but it has no ledger side effects. A per-tick CPU cache remains unsafe because another CPU can start in the same
tick. No save-format change is needed.

## Evidence and counterevidence

- Before repair: `projectionacceptedpushdisconnect` fails with expected watched jobs one, actual zero.
- After repair: that regression, immediate global withdrawal, real native request and container push pass together
  (4/4). One output is accepted locally while disconnected, held once, delivered after physical reconnection, and the
  original native CPU finishes with zero debt and exactly one physical output.
- The independent CPU unknown-state regression is adapted to the new read/commit method boundary without changing
  its assertions. The required manifest includes the accepted-push regression.
- A separate hypothesis about a provider emitting synchronously *inside* `pushPattern` is not accepted as a Federation
  bug: AE2 19.2.17 `CraftingCpuLogic` itself registers `waitingFor` only after `pushPattern` returns. Native behavior
  must first be compared before promising support for this callback timing.

Raw red/green logs are under `.omo/evidence/audit-round2-2026-10-11/`. Integrated-source and later audit outcomes must
be recorded separately; this individual regression does not establish a true process restart or all-mod coverage.
