# Beam Converter and operating power (2026-10-17)

Feature summary: [docs/features/operating-power.md](../../docs/features/operating-power.md). Plan with decisions
D1-D10: `.omo/plans/beam-converter.md`.

## Things that were not obvious

- **Which runtimes need power off.** `OperatingPowerRule` defaults to required. The GameTest server
  (`GameTestHooks.isGametestServer()`), the compat runner (`ProductionGameTestRunner.onServerStarted`) and the LDLib2
  UI test client (system property `ldlib2.uitest.run` non-empty) turn it off on `ServerStartedEvent`, after the server
  config has loaded; setting it earlier is overwritten by the config. The UI test client is not a GameTest server, so
  it needs its own check, or every UI scenario's domain stops.
- **Restart timing.** AE2's `EnergyService` counts the change tick as 0 and restarts when `ticksSinceChange > 30`, so a
  stopped domain runs on its 32nd paid tick, not the 31st. A fresh ledger starts running (AE2 starts `hasPower = true`).
- **Creative converter input.** It never drains, so its reported input is raised to the domain's use; otherwise the
  gauge's "input below use" rule would show a creative domain as draining (yellow).
- **Topology showcase power.** `TaskThirtyThreeShowcaseFixture.placeConverter` stands a 1x1x2 converter on the middle
  showcase cable (front down), sets each block to 82.5 % and only then turns power on; a test-only
  `ServerTickEvent.Pre` listener feeds `ceil(use)` each tick, so the gauge stays green around 82 %. Fractional uses
  (0.5 per cable) make an exact integer feed alternate below use and flicker yellow, hence `ceil`.
- **Gauge label width.** "Power" in the Minecraft font is 30 GUI pixels; a 25-pixel label hid the "r". The gauge is
  112 wide: name 31, bar from 38 (40 wide), value 26 right-aligned.
- **dev_gametests `--match`** filters by GameTest id (`operatingpower...`), not by manifest id (`power.*`). Pass the
  test ids directly to run the power tests.
- **Guide scene states.** A formed converter in an snbt needs `facing`, `formed` and the six connection booleans in the
  state string, and the same string in the palette (`everyStructureBlockStateIsInItsPalette`). The cable under a
  converter's front draws dense, as against a Router.
- **Statistics GameTests finish seconds themselves.** `FederationStatistics` records a second once the wall clock
  passes it, and a GameTest server ran about 1200 ticks in 5 real seconds: one recorded second then averaged the
  one-off 8000 FE insert into the input (38 FE/t instead of 5), and `book.latest` kept returning that stale point.
  `operatingpowerfeedsstatistics` calls `finishSecond` itself, once after the insert and then once per tick, as
  `StatisticsGameTests` does.
- **Chinese guide wording.** Bundled cables are 「粗线缆」 in the Chinese guide, not 「捆束」.

## Evidence (not committed)

- `ui.mapping` before/after: topology without a gauge (before, `6852bba3`), running on a converter (gauge green 82 %),
  converter removed (dark red canvas, full red bar, 0 %); compared with the design boards TopologyFedPowerGauge and
  TopologyFedPowerStopped rendered with headless Chrome.
- Guide page rendered with `runGuideClient` under Xvfb in English and Chinese; no PageCompiler warnings.
