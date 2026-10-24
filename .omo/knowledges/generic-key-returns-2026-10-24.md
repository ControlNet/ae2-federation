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
