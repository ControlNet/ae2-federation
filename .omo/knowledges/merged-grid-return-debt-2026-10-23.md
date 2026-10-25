# Projected return debt across a mid-job Grid merge (2026-10-23)

## Symptom
- A modpack playthrough removed only the Bridge part during a projected job. The fluix cable under it joined the
  requester and factory into one AE2 Grid; restoring the part split them again.
- Afterwards the requester had its one output and an idle CPU, buffers and transit were empty, but
  `CraftingReturnLedger` still owed the requester one output with held=0. Removing the whole Bridge host passed.

## Root cause
- Both networks carry debts and rules, so the merged Grid is `AMBIGUOUS_MERGE` (`resolveMerge` never folds ids with
  crafting returns). Neither NetworkId is confirmed, `federationDomains.grid()` is empty for both.
- The reviewer's suspected `consumer == executingGrid` skips (router insert, `sweepReturns`) are never reached.
- Reconcile withdraws the projections and `syncRouters` unmounts the router (wanted lacks the executing id).
- The machine output enters the merged Grid. AE2's `CraftingServiceStorage` is mounted first at `Integer.MAX_VALUE`
  (added in the `CraftingService` constructor; `NetworkStorage.insert` walks same-priority mounts in list order), so it
  hands the output to the waiting CPU. No Federation code observes that delivery, so the debt never falls.
- After the split no CPU waits. The factory's router claims the next unit of that key stored on the factory as held,
  and the sweep hands it to the requester because the watched job is DONE. Reproduced: a player's stone stored on the
  provider network after the job ended up in the consumer's chest.
- In the merge tick itself the merged CraftingService does not list the CPU yet (end-of-tick update); an arrival then
  is held by the still-mounted router and handed back after the split. That path was already correct.

## Fix
- `mixin/compat/CraftingCpuReceiptMixin`: `@Inject` at the head of `CraftingCpuLogic.insert`; in MODULATE it reports
  `min(amount, getWaitingFor(what))`, exactly what the method will take (it returns early only without a job or a
  wait and caps the amount at the wait).
- `CraftingProjectionService.cpuReceived`: the consumer is the receiving CPU node's lineage; for each network owing
  that consumer the key, oldest first, it calls `ledger.take` only while the CPU's Grid carries that network's nodes
  (`IdentityClaimIndex.carries`). In a normal layout the consumer's Grid never carries the executing network's
  lineage, so the router remains the only path there. No statistics meter: this is a native delivery.
- Why not public API (user rule: public API before mixin): `CraftingCpuLogic.addListener` passes only the key.
  `ListCraftingInventory.clear()` notifies after clearing when a job finishes, and `CraftingLink.markDone()` is a no-op
  without a tie, so for a standalone job a finishing clear looks like a receipt and would drop debt for outputs that
  never came (cake buckets). `ElapsedTimeTracker` exposes only float progress. Storage-side interception is closed by
  the mount order above. The orchestrator/user may veto the mixin.

## Not covered
- An owed output arriving in the merged Grid when no CPU waits (job already ended) is stored natively and keeps its
  debt; the next unit stored on the factory after the split is then claimed. Handling it needs the router kept on an
  ambiguous Grid and `ownStorage` of a merged Grid.
- (Fixed 2026-10-24, see the last section) Independent addon CPUs (Neo ECO, Advanced AE, Lightning Tech) did not run
  `CraftingCpuLogic` and were not observed.
- In a merged Grid AE2 does not say where stock came from; any unit of the key reaching the CPU settles the debt.

## Tests
- `projectionmergedbridgereturn`: fluix cable under the Bridge, only the part removed, then restored.
- `projectionmergedcablereturn`: a fluix cable placed between the consumer CPU and the provider cable, then broken.
- Both use the hand-run cobblestone-to-stone machine of `PatternProjectionFixture`, wait until the merged Grid lists
  the busy CPU and the router is gone, run the machine, split after the CPU finished, then store one more stone on the
  provider network and assert it stays there, the debt is zero and the job retired.
- Before the fix both failed with owed 1, held 0, job DONE after the split, and the player's stone in the consumer's
  chest. `IdentityClaimIndexTest.aMergedGridCarriesEachNetworkUntilItsNodesLeave` covers `carries`.

```sh
python3 tools/dev_gametests.py projectionmergedbridgereturn projectionmergedcablereturn
```

## Generic receipt on `CraftingService.insertIntoCpus` (2026-10-24)
- `CraftingCpuReceiptMixin` (head of AE2 `CraftingCpuLogic.insert`) is replaced by `CraftingServiceReceiptMixin`: a
  MixinExtras `@WrapMethod` of `CraftingService.insertIntoCpus`, priority 2000, observe-only. AE2's crafting storage
  routes every Grid insert there first, and Neo ECO, Advanced AE, Thunderbolt (Lightning Tech) and Data Energistics
  feed their own CPUs from injects or a `@WrapMethod` on that method. An exported transformed `CraftingService` from the
  NAST pack (`-Dmixin.debug.export=true` in a profile's `jvmArgs`) shows ours outermost: `insertIntoCpus` -> our wrapper
  -> DE's wrapper -> body with the Neo ECO, Thunderbolt and Advanced AE injects.
- Measure: `CraftingProjectionService.waiting` = max(`getRequestedAmount`, sum over `getCpus()` of
  `CraftingCpuLifecycle.waiting`), read before and after; `taken = min(amount, before - after)`; settle
  `min(owed, max(0, taken - max(0, before - owed)))` over debts whose executing and consumer network the Grid both
  carries (`IdentityClaimIndex.carries`), oldest first.
- Why not the method's result (the brief's first design): AE2 returns `job.link.insert(...)` for a final output, 0
  without a requester, although the CPU took it. With the result, all three merged dev tests failed (owed stayed 1).
- Why not `getRequestedAmount` alone: Neo ECO 21.2.9's `getRequestedAmount` RETURN inject is cancellable and calls
  `setReturnValue`, so the method returns before Advanced AE's and Thunderbolt's injects. In NAST the Advanced AE and
  Lightning Tech merged tests then failed (owed 1000 left). `getCpus()` uses a builder and lists every CPU.
- Why cap at `amount`: a finishing job clears its other waits; per CPU, `min(offered, wait drop)` equals what it took.
- Normal path: a router hand-back enters C's `insertIntoCpus`, but C's Grid does not carry P, so nothing settles there.
  Dropping the executing-network check made 7 projection tests fail (double settlement).
- Mixed demand (`projectionmergedmixeddemandreturn`): both jobs need a requester, or one stone ends both requester-less
  jobs' waits (AE2 passes the full amount on when the link returns 0). Uses `ConsumerCpus.BOTH`.
- Tianshu (Lightning Tech `BigCraftingService`): plans only from Matrix controllers on its Grid
  (`BigGraphExport.capture`), dispatches with `acceptBigCrafting`, keeps outputs in an escrow; never a projection.
- Trinity (Data Energistics): admits a plain AE2 plan and pushes through `ICraftingProvider.pushPattern`, returns via
  DE's `insertIntoCpus` wrapper; a test needs the 27x28x32 main structure (autobuild by reflection, creative mock
  player), a taller template, the depot block and a DE crafting-graph revision gate. Not built.

```sh
python3 tools/dev_gametests.py projectionmergedbridgereturn projectionmergedcablereturn projectionmergedbridgefluidreturn projectionmergedmixeddemandreturn
python3 tools/compat_run.py --accept-eula neoecoae advanced-ae ae2-lightning-tech
```

## Trinity merged test (2026-10-24)
- `DataEnergisticsCompatGameTests.trinityDataCoreMergedFluidReturn` (group `data-energistics`) supersedes the "Not
  built" above. It reuses `trinityPlanning`'s `TrinityDataCore` (guide snbt main + CPU + crafting children, depot,
  `matter_fabrication_well` template, no `@RunsAlone`) with `AddonCraftingScene.lavaMachine()` and
  `consumerCpuStructure(...).craftingOnly().outputStoredOnConsumer().mergingAfterPush()`; it asserts the plan is a
  `TrinityCraftingPlan`. No graph-revision gate was needed: `ready()` plus the scene's `isCraftable` wait sufficed.
- Trinity has no `CraftingCpuLifecycle` adapter, so its wait reaches `CraftingProjectionService.waiting` only through
  DE's `@WrapMethod` on `getRequestedAmount` (adds every Trinity runtime's wait to `original.call`, so Neo ECO's
  cancellable inject inside the body cannot cut it short; NAST with DE 3.3.5 passes). Its job is watched as unknown
  (`jobs` stays empty).
- Fault checks: receipt mixin disabled -> fails (owed 2000, after the split held 1000 on the provider);
  `waiting` = lifecycle count only -> fails the same way; `waiting` = public total only -> passes (Trinity does not
  depend on the per-CPU count). The AE2 control `nativeCpuMergedFluidReturn` fails only with the mixin disabled.

```sh
python3 tools/compat_run.py --accept-eula data-energistics
python3 tools/compat_run.py --accept-eula --tests trinitydatacoremergedfluidreturn nast
```
