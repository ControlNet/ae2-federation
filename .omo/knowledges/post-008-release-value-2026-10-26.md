# Release value since 0.0.8 (2026-10-26)

## Comparison

- Published baseline in local Git: `v0.0.8` = `6a141db4`; inspected development head: `9840c48d`.
- Exclude `fd9ddd40` (restoration of release-excluded placeholder-art content) when describing new release value.
  Comparing that restoration commit to HEAD isolates subsequent production changes. No release or source changes
  were made for this assessment, and no fresh build, gameplay suite or remote CI inspection was performed.

## Player-facing value

- Large-domain performance: batch native storage mount and Bridge binding reconciliation, enumerate configured
  relationships instead of every member pair, reuse cable capacity counts and shared energy readings, schedule
  cable/Router/Switch work on demand with periodic recovery sweeps, avoid idle CPU/debt work, spread statistics writes
  over ten ticks and reduce metering/stock enumeration allocations.
- Remove the internal domain recomputation traversal limit (formerly 4096 node visits / 24576 port visits).
  This does not remove gameplay cable capacity limits or guarantee unlimited practical server performance.
- Neo ECO batching through projections: ordinary/Advanced AE providers expose the appropriate native logic;
  crafting-system fast-path and Large Integrated Working Station batch dispatch preserve Federation return accounting.
  Recorded targeted tests: 16 crafts in 5 pushes instead of 16; crafting system 256 crafts in 96 ticks / 9 pushes
  versus 2400+ ticks before and 92 ticks on one native Grid; workstation 64 crafts in 1 push instead of 64,
  with approximately unchanged completion time at this size. Source: neoeco-smart-doubling-2026-10-25.md.
- Statistics now use overworld game time (20 ticks/second); pause/offline time does not advance the chart, and rates
  are per game second. `StatisticsSavedData` changes format 2 to 3 and returns a new empty book for older formats:
  old history and book-stored pins are not migrated. Include this in upgrade notes.
- Switch face recovery handles missed neighbor events; Mekanism Cardboard Box scenarios were added. This also
  protects the newly introduced lazy face-node behavior; do not claim every such issue existed in 0.0.8.

## Evidence and scope

- Performance notes: bus-farm-benchmark-2026-10-24.md. The final three-run reference uses 10,000 buses, a test-only
  BulkItemStorage, forced infinite AE2 channels and saving disabled during measurement. At 1000 subnets x 10 buses:
  one native Grid 14.6 ms median, one Federation domain 13.9 ms, 10,046-block domain 14.1 ms, Bridge topology 17.7 ms.
  These compare systems in a controlled workload, not a direct 0.0.8-versus-release benchmark or universal speedup.
- Added NAST quest/machine/topology/progression harness and per-push regressions, modpack freshness automation,
  historical addon qualification and AE2 Crystal Science tests. These improve evidence and regression protection;
  they are not new player blocks or a guarantee of complete modpack playthrough coverage.
- Beam Former art and existing P2P/bundle/statistics features are not new since 0.0.8. Function-system work remains
  on a separate branch and is not included in this dev comparison. Placeholder converters/cube remain excluded
  under the owner's requested release scope.

## Recommendation

There is sufficient player value for 0.0.9 as a performance and compatibility update without waiting for new art.
Content sufficiency is distinct from release readiness: adapt the previous exclusion patch and newer Quantum
playthrough selections, run checks on the exact release artifact, and document the statistics reset.
