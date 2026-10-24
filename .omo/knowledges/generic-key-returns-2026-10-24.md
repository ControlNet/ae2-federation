# Projected returns for non-item outputs (2026-10-24)

## Finding
- Pattern projection returns fluids and Applied Mekanistics chemicals exactly as it returns items. No defect was
  found and no main code changed.
- The whole return path is `AEKey`-generic: `CraftingReturnRouter` (preferred mount, CPU hand-off, held arrivals),
  `CraftingReturnLedger` (owed/held/transit keyed by `AEKey`, saved with `toTagGeneric`), `HeldReturnTransfer` and the
  sweep in `CraftingProjectionService`. No `AEItemKey`/`AEFluidKey` check exists in `crafting/projection`.
- The new tests do catch a key-type defect: with an item-only filter injected into the router
  (`what instanceof AEItemKey` in `isPreferredStorageFor` and `insert`), all four fluid GameTests and both chemical
  compat tests failed (fluid stays owed, held stays 0, the late chemical is never delivered). The injection was
  reverted.

## Test setup notes
- The consumer must be able to store the key, or AE2 keeps the final output in the CPU; the provider must be able to
  store it, or a late or unlinked arrival is refused and stays in the machine. `PolicyBridgeFixtures` has item cells
  only.
- `PatternProjectionFixture.withFluidCells()` adds an ME chest with a 1k fluid cell to each network
  (`BASE.south(2)`, `BASE.north(3)`), placed in the fixture's first tick so identity settles as for the rest.
  `runMachine(key, amount)` is the key-generic hand-run machine.
- `AddonCraftingScene.outputStoredOnConsumer()` adds an ME chest with the machine's output cell on the consumer and,
  at the end, checks owed/held/transit 0, no output on the provider network, the watched job retired and the CPU empty.
  Use it with `craftingOnly()`: with the Storage rule on, the consumer stores the output on the provider's cell
  through Federation storage, which is why the old `oxidizerChemicalProcessing` never checked the factory or ledger.
- `cancellingAfterPush()` now accepts a real machine: it waits until the ledger owes `requested()` (every push
  accepted), then cancels. The Chemical Oxidizer takes about 100 ticks a batch, so the job is still waiting then.
- AE2 hands a standalone job's final output to `CraftingLink.insert`, which returns 0 without a requester; the CPU
  still counts it, and `NetworkStorage` passes it on to the next mount (the consumer's cell).

## Tests
- Vanilla (manifest, required): `projectionfluidreturn`, `projectionmixedfluidreturn`,
  `projectionfluidcancelledreturn`, `projectionfluiddisconnectedreturn`.
- Compat (`appmek` group, `mekanism` profile): `oxidizerchemicalprojectedreturn`, `oxidizerchemicalcancelledreturn`.

```sh
python3 tools/dev_gametests.py projectionfluidreturn projectionmixedfluidreturn projectionfluidcancelledreturn projectionfluiddisconnectedreturn
python3 tools/compat_run.py --accept-eula --tests oxidizerchemicalprojectedreturn,oxidizerchemicalcancelledreturn mekanism
```

## Not covered
- A fluid or chemical debt across a save/reload (the ledger uses the generic tag codec; only items are reload-tested).
- Chemical outputs held while unlinked, and fluid outputs through a real fluid machine (the vanilla tests use the
  hand-run chest machine inserting into the provider network).
- Merged-Grid receipt (`cpuReceived`) with a non-item key.

## Second round: restart, unlinked chemicals, merged Grid, addon CPUs (2026-10-24)
- No defect in main code; no main code changed. One known gap reproduced: independent addon CPUs in a merged Grid.
- Restart: `projectionfluidrestartreturn` holds 2 buckets while the Bridge is removed, moves one into transit
  (provider fluid cell out, extract-only `FaultStorage`, hand-back to a target that takes nothing), then closes the
  service, round-trips the ledger through `save`/`load` and reloads both networks' block entities. Owed 2000,
  held 2000 and transit 1000 come back for the fluid key; after the cell and Bridge return the job gets both buckets.
  `PatternProjectionFixture.reloadAll()` now skips refreshing the Bridge part while it is removed.
- Chemicals while unlinked: `oxidizerchemicaldisconnectedreturn` (mekanism). `disconnectingAfterPush()` now accepts
  a real machine; `relinkingAfterDisconnect()` places the Bridge back instead of cancelling, after checking held and
  a detached `save`/`load` copy of the ledger for the chemical key.
- Merged Grid with a fluid: `projectionmergedbridgefluidreturn` (dev, required). `preferConsumerChest()` also raises
  the consumer's fluid chest when the fixture has fluid cells, or the finished job may store its lava on the
  provider's cell inside the joined Grid (a layout effect, not a defect).
- Addon CPUs with fluid output: `computationsystemfluidreturn`, `computationsystemfluidcancel` (Neo ECO),
  `quantumcorefluidreturn` (Advanced AE), `mentalmathunitfluidreturn` (Lightning Tech). They use
  `AddonCraftingScene.lavaMachine()` (the hand-run chest machine, a bucket of lava per cobblestone, 1k fluid cell).
- Gap: merged Grid on an addon CPU. `AddonCpuMergedGridProbes` (group `addon-cpu-merged-probes`, in no profile)
  fails for all three addons the same way: while merged owed 2000, the CPU takes its lava natively; after the split
  the debt is still 2000, the next player bucket stored on the provider is claimed and handed to the finished job
  (consumer ends with 3000, owed 1000). `nativecpumergedfluidreturn` in the same class passes (control).
  - Cause: only `CraftingCpuReceiptMixin` (AE2 `CraftingCpuLogic.insert`) reports receipts; addon CPUs run their own
    logic (`ECOCraftingCPULogic`, `AdvCraftingCPULogic`, `Ae2LtTimeWheelCraftingCpuLogic`).
  - Addon APIs checked (Neo ECO 21.2.9, Advanced AE 1.6.12, ae2lt 2.1.1 / Thunderbolt 2.0.1): each logic has a
    public `insert`, `getWaitingFor` and a key-only `addListener(Consumer<AEKey>)`; none has a receipt event. Neo
    ECO's `ECOCraftingLifecycle` listener reports job start, pattern dispatch and job finish only.
  - Options: per-addon optional mixins at the head of each logic's `insert` (owner approval needed); or a sampled
    waiting-amount diff through the existing lifecycle adapter, which cannot tell the final receipt from a finishing
    clear and so is not safe; or accept and document (current state).

```sh
python3 tools/dev_gametests.py projectionfluidrestartreturn projectionmergedbridgefluidreturn
python3 tools/compat_run.py --accept-eula --tests oxidizerchemicaldisconnectedreturn mekanism
python3 tools/compat_run.py --accept-eula --tests mentalmathunitmergedfluidreturn,nativecpumergedfluidreturn ae2-lightning-tech
```
