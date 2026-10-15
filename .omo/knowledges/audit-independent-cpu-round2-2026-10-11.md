# Independent CPU lifecycle, second audit (2026-10-11)

Baseline: `2fa70d74f271a69e80baff583940d4debd3a65ed`, clean isolated worktree
`ae2-audit-r2-lifecycle` / `audit/lifecycle-round2-20261011`. No production planner, CPU,
completion scheduler, Storage policy or save schema changes.

## Confirmed and fixed: retired Lightning Tech CPU

Lightning Tech 2.1.1 removes a TimeWheel CPU once its native persistent state drains.
The standalone CPU-side AE2 link need not become `isDone()`. After transient link loss,
a saved historical job could therefore remain unresolved forever, preventing outstanding
returns from entering ordinary consumer storage when no CPU requested them.

`CraftingCpuLifecycle.terminated` now recognizes only the exact Pigmee owner class,
requires its native `isCpuActive()`, and reads the full native pool's
`hasPersistentState()`. The pool includes pending-loaded CPU objects, unlike a Grid's
active CPU list. Native draining checks jobs, pending restore tags, inventories, direct
remainders, seed quota, requester outputs and overload state. No native lifecycle method
is replayed. Reflection failure retains uncertainty. This uses public addon methods pinned
to 2.1.1; no additional private field dependency and no save schema change.

Independent review by the returns worker inspected the actual pinned jar bytecode and
accepted this narrow proof. Neo ECO's previous formed/deferred-slot guard was inspected
again: ordinary deactivation does not publish a terminal record, while resumed deferred
jobs can clear cancellation markers. No contradictory lifecycle proof was found there.

## Verification and distinctions

```bash
./gradlew --dependency-verification=strict :neoforge-1.21.1:jar :neoforge-1.21.1:compatTestJar
python3 tools/compat_run.py --accept-eula --tests mentalmathunitretirement,mentalmathunitchunkunload,mentalmathunitcancel ae2-lightning-tech
python3 tools/compat_cpu_restart.py --accept-eula --no-build
```

- `mentalMathUnitRetirement`: RED on baseline (native retired host yielded `null`),
  GREEN after adapter. The real addon CPU completes a real native job; a saved ledger
  copy and explicit transient-link removal isolate the historical lifecycle observation.
  This is targeted fault injection, not an entire extra-output processing simulation.
- Final targeted run: **3/3 passed**, including existing actual cancellation.
- Actual independent CPU chunk unload: consumer Grid stays loaded, nether CPU block
  entity and chunk really unload, two arrivals remain held for four sweeps, then the
  same saved native CPU resumes and returns exactly two items. Storage is never enabled.
- Two actual Java server processes reuse the same world: prepare saves with CPU unloaded,
  resume verifies a different process PID, same saved job UUID and identities, four more
  unloaded sweeps, then loads the native CPU and completes without a replacement order.
  Fixed-source prepare and resume both passed. Reports and metadata are in
  `.omo/evidence/audit-cpu-round2-2026-10-11/`; the world is retained under the worker's
  `neoforge-1.21.1/build/compat/runs/independent-cpu-restart-1791690264911480243`.
- These are real pinned addon implementations, not native CPU subclasses or fake CPU
  substitutes. Compatibility tests use a separate test mod, a hand-operated chest
  processor and test-only chunk tickets. They are not production-client/no-testmod proof.
- The first unload fixture expected an executing CPU before submission; Lightning Tech
  correctly exposes its pool sentinel while idle. Correcting the fixture then passed on
  baseline. That initial failure was not a production defect.

## Confirmed open boundary at round-two handoff: Advanced AE retirement

Follow-up: `audit-advanced-cpu-retirement-2026-10-11.md` records the subsequent repair and
actual restart validation. The history below preserves the original failing evidence.

A second distinct audit pass ran the same saved-history observation against actual
Advanced AE 1.6.12. `quantumCoreRetirement` failed: after native CPU completion and removal,
with its transient link absent, the service still returned `null`. This is evidence of
conservative unresolved history, not proof of lost items or every Advanced AE job failing.
The test completes and delivers its one output before examining the isolated ledger copy.

The exact failing test remains in the worker worktree; its registration patch is retained
in `advanced-retirement-pending.patch`, and the shared real retirement scene supports its
`advanced=true` invocation. Reproduction after applying that patch:

```bash
python3 tools/compat_run.py --accept-eula --tests quantumcoreretirement advanced-ae
```

Expected currently: **0/1 failed**, message "A loaded native host with no live or pending
CPU must resolve the retired job: null". No assertion was weakened and no failure deleted.
This exploration is deliberately not added to the passing required compatibility gate.
The parent requested bounded delivery of the already validated TimeWheel fix and explicit
reporting of this new unresolved case. Advanced AE `getActiveCPUs()` mutates/prunes native
state and must not be reused as a read-only terminal probe. A future adapter needs reviewed
full persistent-state and deferred-restore evidence, not simply an empty Grid CPU list.
