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
  gauge would show a creative domain as draining (yellow).
- **Gauge flicker (fixed).** The gauge once compared one tick's input with use and floored the percentage, so a full
  buffer read 99 % and flicked yellow/green: a converter takes whole FE (`floor(room)`) against fractional use, and
  bursty sources push every few ticks. Now `OperatingPowerService.Reading.gauge()` rounds the percentage and calls a
  domain draining only when input less use over the last 100 ticks is below -1 FE a matching block
  (GameTest `operatingpowergaugeholdssteady`, fed 6 FE every other tick against a use of 3).
- **Input off the use chart.** The user asked (2026-10-17) to take the converters' input off the Federation power use
  chart, a deliberate change from the StatsFedPower design board: it is in the buffer chart's tooltip and the body list.
- **Topology showcase power.** `TaskThirtyThreeShowcaseFixture.placeConverter` stands a 1x1x2 converter on the middle
  showcase cable (front down), sets each block to 82.5 % and only then turns power on; a test-only
  `ServerTickEvent.Pre` listener feeds the front block `ceil(use)` each tick (the body shares one buffer), so the
  gauge stays green around 82 %. Fractional uses (0.5 per cable) make an exact integer feed alternate below use and
  flicker yellow, hence `ceil`.
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
- **A body is one buffer (user decision 2026-10-17).** Each block keeps its own `stored` in NBT, but
  `BeamConverterBlockEntity.body()` finds the formed cuboid by walking the connection bits to its corners (cached for
  the tick); `receiveEnergy` takes up to the body's room and spreads it by each block's room, the capability reports
  body totals, and `OperatingPowerService.settle` levels a body's blocks after the draw. Before this a pipe on one
  block filled only that block, which then accepted just its share of the drain while the rest ran down: a 3x3x6
  converter on one cable used about 1/54 of its buffer. Jade shows the body's buffer and summed input.
- **Chinese guide wording.** Bundled cables are 「粗线缆」 in the Chinese guide, not 「捆束」.

## Evidence (not committed)

- `ui.mapping` before/after: topology without a gauge (before, `6852bba3`), running on a converter (gauge green 82 %),
  converter removed (dark red canvas, full red bar, 0 %); compared with the design boards TopologyFedPowerGauge and
  TopologyFedPowerStopped rendered with headless Chrome.
- Guide page rendered with `runGuideClient` under Xvfb in English and Chinese; no PageCompiler warnings.

## Facing-free forming and the wrench (2026-10-17)

- User request: blocks merge whatever their facing; a wrench turns a formed body end for end; an unformed block's
  facing is meaningless (the smallest converter is 1x1x2), so nothing turns a single block.
- `BeamConverterShape.axis(min, max)`: only one axis of a box can be 2·max(M, N) long (x ≥ 2y and y ≥ 2x cannot both
  hold), so the size alone gives the axis. `judge` checks only the size.
- `FormableDevice.forming(level, body)` returns one `UnaryOperator<BlockState>` per body, computed before any member is
  rewritten (a per-block vote would shift as members change). The converter's rule: majority of members' `FACING` on
  the axis, else the one end face touching `FederationCableBlock`, else the positive end. Majority must come first: the
  wrench's `UPDATE_ALL` triggers a re-judge, and a cable-first rule would turn the body back.
- `RouterBlockEntity.setBlockState` marks face ports dirty on a `FACING` change too, or a turned body would not relink.
- `CableVisualConnections` draws a dense joint to a converter only when it is formed.
- GameTests: `beamconvertershapes` (tie, across, majority, toward cable), `beamconverterturnswithawrench` (AE2 Certus
  Quartz Wrench through `gameMode.useItemOn`; links, FE faces and an unformed block's kept facing).

## In-game FE source for captures (2026-10-17)

- `build/visual-v07` has no FE generator. A test-only Mekanism 10.7.19 jar (from `neoforge-1.21.1/build/compat/runs/
  mekanism/mods`) in both `mods` folders gives one; remove it afterwards. A `setblock`-placed
  `mekanism:creative_energy_cube` is empty and ejects nothing; fill it and make every energy side output with
  `data merge block X Y Z {energy_containers:[{container:0b,stored:1000000000000L}],component_config:{eject0:1b,config0:[I;4,4,4,4,4,4]}}`
  (`config0` is energy, `4` is `DataType.OUTPUT`; `config3` is items). Beside a full 2x2x4 converter it reproduced the
  old gauge's 99 % and yellow/green flicker (input 40-42 FE/t against a fractional use).
- `tools/visual/game_input.py close` closes the client window, not a screen; tap Escape through Xlib XTEST instead.
