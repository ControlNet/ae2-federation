# Option 1 gap measurement (2026-10-27)

Measurement only; no route decision. Option 1 is the CPU route with a minimal projection kept as fallback: real
providers on the consumer, a ledger fed by a few provider-side hooks, and the projection only for providers those hooks
cannot see. The question was how often current addons push in ways those hooks miss.

## How to run

```sh
P="baseline data-energistics neoecoae neoecoae-advanced-ae advanced-ae ae2-lightning-tech extendedae extendedae-plus \
megacells ae2-pattern-disk ae2cs omnisequence useless-mod appflux aeallpattern neoecoprototype appliedenhancements addons-all"
python3 tools/compat_run.py --accept-eula $P -j 7 -p 4 --run-suffix gap-joined \
  --jvm-property ae2federation.dispatchProbe=true --jvm-property ae2federation.compat.joinBeforeOrder=true
python3 tools/compat_run.py --accept-eula --no-build $P -j 7 -p 4 --run-suffix gap --jvm-property ae2federation.dispatchProbe=true
python3 tools/dispatch_probe_report.py data-energistics-gap-joined ae2cs-gap-joined
```

Joined runs decide: there the consumer sees real providers, as it would under option 1. Federated runs only see
projections, which no addon recognises. Probe runs replace each pattern provider's send list with a recording
`ArrayList` (`RecordingSendList`); ordinary runs are unchanged.

Known protocols (the hooks option 1 would keep): `PatternProviderLogic.pushPattern`, Data Energistics
`pushExpanded`/`pushExpandedToTarget`, Neo ECO `LargeWorkstationPatternProvider.eco$pushPatternBatch` and FastPath
`Preparation.push`, Thunderbolt `pushBatch`. Gaps: an executor hand-over that opened none of them; a pattern provider's
`onPushPatternSuccess` outside them; its send list growing outside them and outside `readFromNBT`.

## Results on the 2026-10-26 pins

- Federated: 18/18 profiles pass, so the probes change nothing. Joined: the same federation-only failures as P0;
  `addons-all` joined hit the 900 s deadline and wrote no probe file, so it is not measured.
- Out of scope: 0 pushes in every run. Scope attribution held everywhere; the "refuse an out-of-scope push" fallback
  was never needed.
- Send list growth outside known protocols: 0 in every run.
- One private protocol on a plain `PatternProviderLogic` (data-energistics joined, 1x):
  `TrinityDataCoreCpuLogic.dispatchToAvailableProvider` -> `CraftingDispatchCommitter.commit` ->
  `PatternProviderBatching$OneShotAdmission.commit` -> `prepareStandardBatch` lambda ->
  `dataEnergistics$invokeOnPushPatternSuccess`. DE's standard batch completes a push without `pushPattern` or
  `pushExpanded`. It ran inside DE's participant scope and was caught only by the `onPushPatternSuccess` indicator.
- Provider classes whose pushes no known protocol saw (joined), each of which option 1 would serve through the
  projection or a per-class adapter:
  - Advanced AE `AdvPatternProviderLogic` (AE2 CPU, AAE CPU, Neo ECO CPU)
  - AE2 Lightning Tech `PigmeePatternProviderBlockEntity`
  - AE2 Crystal Science `MeteoritePatternProviderLogic` (34 of 38) and `ResonatingPatternProviderLogic` (4 of 8; it
    overrides `pushPattern` and still calls AE2's `onPushPatternSuccess`)
  - ExtendedAE `TileAssemblerMatrixPattern`
  - Neo ECO `ECOCraftingPatternBusBlockEntity`
  - OmniSequence (`com.atir.molecularmanipulator`) `MolecularManipulatorLogic` and `MatterFabricationPatternLogic`
  - Useless Mod `AdvancedAlloyFurnaceBlockEntity` and its Smart Doubling provider proxy
- Not seen in these scenes: Neo ECO exact-order dispatch, Thunderbolt batches into real providers.

## Reading

- The feared gap exists but is small: one DE path, detectable through `onPushPatternSuccess`, which carries the
  pattern but not the run count.
- `onPushPatternSuccess` sees more than `pushPattern`: it also catches subclasses that override `pushPattern` but keep
  AE2's lock bookkeeping (Resonating) and DE's standard batch.
- The provider-side hooks cover plain AE2 providers and subclasses that keep `pushPattern`; about ten provider classes
  in seven of the tested addons fall outside them.
- Correction (same day): that list does not make the projection a second path in regular use. Every hand-over above,
  including those to the ten classes and the DE standard batch, was seen by an executor probe (AE2, Advanced AE,
  Neo ECO, Lightning Tech, Data Energistics), and no provider-side push in any joined run was missed by the executor
  probes. Executor adapters account a push whatever the provider class; provider-side hooks only matter for an
  executor we do not know. The case nothing covers is an unknown executor handing work to a provider class outside
  the AE2 family. No such executor appeared in these profiles.

## Arrival sources for a demand-based fallback (2026-10-27)

Owner direction: the fallback for pushes no hook sees is network listening (what the consumer's CPUs request, plus who
inserted the output), and no provider wrapper is kept. `NetworkStorageArrivalProbe` records, for every outermost insert
into a grid's storage of a key some addon provider owes from an accepted hand-over, what its `IActionSource` names.
Federation's own providers are left out. Run: the 17 profiles above (not `addons-all`), joined, suffix `src-joined`.

| The source names | Arrivals | What they were |
|---|---:|---|
| the provider that owes it | 200 | AE2 providers and AdvPatternProviderLogic, ae2lt Overloaded and Pigmee, ae2cs Meteorite and Resonating, Pattern Disk, DE Adaptive, OmniSequence's two logics, Useless Mod's alloy furnace |
| a machine without a provider | 162 | Neo ECO CPU final delivery 99, AE2 CPU final delivery 4, Federation Lanes about 55, ExtendedAE assembler matrix frame 6 |
| no machine | 80 | test scenes' simulated machines (`ChestMachine.collect`, `RoutedMachine.collect`) and Federation's own hand-backs |
| another provider | 7 | multiblocks and wrappers: ae2lt matrix port, ExtendedAE-Plus super assembler matrix, DE Trinity job finish, Useless Mod furnace behind its Smart Doubling proxy |

- A provider's own return names it for every single-block addon provider seen.
- It does not for multiblocks whose output block is another part than the provider part (assembler matrices, the
  ae2lt matrix) or for providers wrapped by another mod's proxy.
- Neo ECO crafting pattern bus outputs never pass a grid's storage insert (owed, never arrived), like the Trinity
  pattern core's direct ingress.
- Not covered by any scene: outputs a player routes back through an ME interface or import bus. Their source names
  that interface or bus, so a fallback for them can only match by key and demand.
