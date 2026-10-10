# Neo ECO Prototype compatibility (2026-10-09)

Neo ECO Prototype (github.com/reliqwq/NeoECOPrototype, GPL-3.0, GitHub releases only) is an L1 tier for Neo ECO AE
Extension. 1.3.2 needs neoecoae `[21.2.1-beta2,)` and LDLib2 `[2.2.40,)`, so profile `neoecoprototype` pins
neoecoae 21.2.1 (the `neoecoae` profile stays on 21.2.0). Result: 34/34 (core 19, neoecoae 5, neoecoprototype 10).

## Upstream bug found

1.3.2 crashes at item registration without MEGA Cells, with or without Federation (`--bare` run):
`SimplifySingularityCellItem.<clinit>` calls `AEItemKey.of(AEItems.SINGULARITY)` (and later `ae2:inscriber`) inside
its item supplier, and its `ae2` dependency has `ordering = "NONE"`, so its RegisterEvent handler can run before AE2's.
Its optional `megacells` entry has `ordering = "AFTER"`, and MEGA Cells loads after AE2, so with MEGA Cells present it
boots. The profile includes MEGA Cells. Reported upstream as reliqwq/NeoECOPrototype#6 (2026-10-10).

## How the tests reuse Neo ECO's scenes

- The L1 definitions mirror eco's L4 layout (interface on the other hand), and the `Simplify*ClusterCalculator`s
  accept both hands, so `NeoEcoCompatGameTests` placements take a `Tier` (namespace + renames from the L4 names).
- L1 drives mount only L1 cells unless the server config `[l1_storage] additional_storage_cells` names others: use
  `simplify_item_storage_cell_1m` and `simplify_computation_cell_1m`. `getCellStack`/`setCellStack` exist as on eco's.
- `simplify_pattern_provider` is a `PatternProviderBlockEntity` (27 slots), `cable_pattern_provider` a
  `PatternProviderPart`; `simplify_stonecutting_assembler` is a `MolecularAssemblerBlockEntity` that passes AE2
  crafting patterns to super.
- `superconductive_interface` is an `IPassiveEnergyGenerator` (4000 AE/t). AE2 injects passive generation each tick
  into the grid's per-node buffer, so it powers other networks over the ME power rule with no energy cell anywhere.
- Trinity is left out: upstream marks it not implemented.

Neo ECO 21.2.1 includes the `getRequestedAmount` fix (tag is ahead of 76bfb358).
