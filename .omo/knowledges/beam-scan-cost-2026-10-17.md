# Beam path check cost (2026-10-17)

Measured for the Beam Former plan (`.omo/plans/beam-former.md`, D12) with the manual GameTest
`BeamScanBenchmarkGameTests.perfBeamScan` (`python3 tools/perf_benchmark.py --tests perfbeamscan --label <x>`), median
of 3 runs on the development host. Reads go through `getChunkSource().getChunkNow` (never loads a chunk) and
`LevelChunkSection.getBlockState`, skipping sections with `hasOnlyAir()`; the obstruction test is a test-only prototype
(air, `c:glass_blocks` and `c:glass_panes` pass; otherwise a non-empty collision shape blocks).

| Case, 3x3 x 1024 = 9216 blocks | Whole volume | Per block |
|---|---|---|
| Open sky (all-air sections, skipped) | 11 µs | 1.2 ns |
| Air in sections holding other blocks | 153 µs | 16.6 ns |
| Glass (every block read and tested) | 299 µs | 32.4 ns |

A Federation topology change costs far more than the scan: toggling one cable in a 901-node domain raised the tick
from 1.08 ms to 1.33 ms (`perftopologyplane`), so debounce, not the scan budget, is what protects the server from a
flickering obstruction.
