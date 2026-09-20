# Task 27 Native Terminal Flow

## Production Boundary

- `crafting/terminal/NativeTerminalAdapter` selects the directional consumer-to-provider `PolicyKey`, requires
  `PolicyOperation.REQUEST`, applies the exact `PolicyFilter`, and obtains a Task 26 `CraftingSubmissionSnapshot`.
- `CapturedTerminalRequester` contains only the captured `IActionSource` and native provider `IGridNode`. AE2's calculator
  thread does not access world, Policy, Fabric, backend discovery, or GameTest helpers.
- `NativeTerminalRequest` starts native `beginCraftingCalculation`, exposes only completed plans on the server thread, and
  rechecks `CraftingBindingService.submissionAuthorityCurrent` immediately before native `submitJob`.
- CPU availability is deliberately not part of the submission authority recheck. This preserves AE2's authentic
  `NO_CPU_FOUND` result after a valid plan has been calculated.
- `CraftingSubmissionSnapshot` captures the exact service, source Grid, providers/nodes, and CPU objects used for discovery
  and evidence correlation. Federation does not connect Grids, inject providers, copy patterns, or own job/result state.

## Evidence

- Repaired current-tree results are retained under
  `.omo/evidence/task-27-acceptance-repair-final/attempt-20260920T075857792Z/result.json`.
- Five isolated GameTests pass: native discovery/planning/submission, callback plus physical result ownership,
  missing-material rejection, no-CPU rejection, and planner-thread mutable-access rejection with stale submission denial.
- `AE2F_CRAFT_AUTHORITY` independently receipts Task 26 native authority. `AE2F_TERMINAL_NATIVE_ENTRY`,
  `AE2F_TERMINAL_PLANNER`, and individual `AE2F_TERMINAL_RUNTIME` events correlate native entrypoint cardinality, identity,
  job UUID, CPU logic, provider push, callback owner, physical owner, and planner thread.
- Persisted consumer and adversarial self-test pass. The self-test rejects missing native children, fake discovery, copied
  patterns, widened storage, stale submission, canned errors, planner world access, duplicate jobs, aggregate-only result
  claims, and fabricated provider/CPU/planner/result ownership.
- `AE2F_TERMINAL_DISCOVERY_AUTHORITY` independently observes two real source craftables, zero consumer-native craftables,
  the exact allow-list, only the stick discovery, and the identities of both original provider patterns.
- `AE2F_TERMINAL_RESULT_AUTHORITY` captures the physical cell delegate before submission and correlates four exact phases:
  destination, native link/CPU submission, `CraftingCpuLogic` callback, and exact-key/exact-amount physical insertion.
- Fully rebound probes alter semantic/native/runtime channels while leaving authority untouched. Job UUID, logic/callback,
  destination, single-pattern source, forbidden discovery, widened filter, copied pattern, alternate provider, and
  projection-recursion substitutions all fail for their intended semantic reason.

## Verification

- Strict compile plus `NativeTerminalFlowContractTest` passes.
- Task 27 canonical five-case `federationVerify`, persisted consumer, and adversarial self-test pass.
- Task 26 four-case and Task 9 five-case native Crafting regression suites pass.
- Strict build, sources JAR, shared-JAR isolation, process-isolation contract, `git diff --check`, and Java compilation pass.
