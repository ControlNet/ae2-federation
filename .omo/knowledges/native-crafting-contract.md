# Native Crafting Research

## 2026-09-14 Task 9 Verified Lifecycle

- Locked AE2 19.2.17 exposes `beginCraftingCalculation`, `submitJob(...) -> ICraftingSubmitResult`,
  `ICraftingSubmitResult.link()`, `ICraftingLink.writeToNBT`, and `StorageHelper.loadCraftingLink`.
- Terminal-equivalent submission uses a player action source and null requester. Automation uses a managed
  `ICraftingRequester` plus pinned `MultiCraftingTracker`.
- `ICraftingSimulationRequester.getGridNode()` must return the native Grid node or AE2 skips provider-pattern exploration.
  Capture the node on the server thread before starting the asynchronous calculation.
- A requester destination must be a real sink such as the physical ME chest inventory. Do not feed callback output into
  the complete Grid aggregate because its `CraftingServiceStorage` mount intercepts crafted output for CPUs.
- Track accepted requester insertion separately from `ICraftingLink.isDone()`; native completion can occur even when the
  requester accepts less than the full final output.
- Restore persisted links before the replacement requester node joins the Grid so `CraftingService.addNode()` can consume
  `getRequestedJobs()` and reconstruct the native nexus.
- For deterministic cancel-before-dispatch proof, suspend the busy CPU after submission. AE2 checks cancellation before
  suspension, then returns the complete CPU inventory through its native cancellation path.
- Exact five-case schema-v3 QA, persisted consumption, rebound adversarial probes, focused contracts, and `check build`
  pass. No planner, CPU, material policy, link ledger, or API shim was introduced.

## 2026-09-14T08:02:00Z Independent Review Repair

- A useful duplicate-path proof is a second `MultiCraftingTracker.handleCrafting` call for the same active slot after the
  first call installs its native requester link. AE2 returns `false`, retains one requester UUID, consumes two planks once,
  and delivers four sticks once.
- Derive `uniqueNativeTasks` from UUIDs observed on native requester/CPU links. Persist the UUID set and require it to have
  cardinality one; do not emit a fixed count detached from runtime identity.
- `CraftingCpuLogic.getStored(inputKey)` is the authoritative pre-cancel CPU inventory observation. The restart case waits
  until Grid material is zero and CPU material is 64 before suspension, then proves native cancellation returns exactly
  those 64 planks.
- Persisted verification cross-checks counts against UUID sets and link IDs. Rebound probes mutate duplicate invocation,
  UUID cardinality, Grid extraction, CPU inventory, and returned material independently.
