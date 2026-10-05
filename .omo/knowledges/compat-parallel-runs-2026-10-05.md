# Parallel compatibility runs (2026-10-05)

`ProductionGameTestRunner` (compattest) runs tests in batches of up to `ae2federation.compat.parallel`, which
`tools/compat_run.py -p/--parallel` sets (default 32; `-p 1` is one test per batch, the old serial mode).

## Why it was slow

Each test was its own batch, so batches waited one after another. Most of a profile's time went to a handful of
tests that wait on a fixed tick count (Endpoint Local-mode tests ~22 s each, smartDoublingEndpoint ~42 s). Server
start is only ~15-20 s.

| Profile | serial | -p 32 |
|---|---|---|
| addons-all (69) | 574 s | 139 s (90 s with -p 64) |
| atm10 (71) | 815 s | 256 s |
| cus2 (52) | 592 s | 193 s |
| ae2-lightning-tech (29) | 257 s | 55 s |
| `--all -j 8` | ~15 min | 5 min 12 s |

## Pitfall: scenes build far outside their template

`harness_native_smoke` is 3x3x3, but scenes put blocks at x=10-11 (storage bus target) and multiblocks well beyond.
Vanilla's `StructureGridSpawner` leaves 5 blocks between templates (pitch 8 for this template). Serial runs hid this:
a later test only overwrote a finished test's leftovers. In parallel, 4 of 69 failed (storageBusTank, modStorageBus,
ecoDistrictOrdering, storageSystemDismantled; the ECO pair were 8 blocks apart). `CellGridSpawner` fixes it: each
test gets a chunk-aligned cell, one chunk before the template on each axis and its size rounded up to chunks plus one
more chunk after it (48 blocks for a 3x3x3 template, 80 for scale_36_empty, 96 for matter_fabrication_well).

## Level-wide state

Mark a test `@RunsAlone` when it changes something the level shares. Today only `solarObservatory` needs it, because
it sets the time of day and weather. Policies, the ledger and energy sharing are keyed per network. `makeMockPlayer`
is not added to the player list. A test that throws a non-assertion exception fails every test running beside it,
because the ticker cannot say which one threw; rerun with `-p 1` to attribute it.
