# Phase 4 quest progression

- Driver: `PlaythroughProgression` (GameTest `playthroughProgression`, group `playthrough-progression`, excluded from
  `all`). Runner: `tools/compat_playthrough_progression.py`, one NAST server process per segment over one saved world.
  Unit tests: `tools/test_compat_playthrough_progression.py` (synthetic fixtures).
- Two persistent bases in one GameTest: baseline at test-relative (48,0,0), federated at (96,0,0), equipment survey
  scratch at (0,0,64). `PlaythroughOffsetHelper` (a `GameTestHelper` subclass overriding `absolutePos`/`relativePos`)
  shifts every fixture call, so `PlaythroughFactory` and `PlaythroughMachines` need no origin parameter.
  GameTest clears only a 3-block margin around the 36-block template, so the bases survive a new process placing the
  test at the same `originX`. Their chunks stay forced through `RestartChunkTickets`, which saves with the world.
- Resume, not replay: a segment stops at an order boundary after `stopAfterSeconds`, writes
  `playthrough-progression-state.json` (both `BaseState`s, owned equipment, timeline, rows, saved stock), and the server
  saves the world on halt. The next process re-attaches with `PlaythroughFactory.attach` and must find identical
  total stock, idle CPUs, no ledger debt or transit, and the saved Federation rules (`restartChecks`).
  Autosaves can save mid-order, so the runner snapshots world + state before every segment; a process whose deadline
  cut an order is restored and repeated with `maxOrders` = orders it finished, or `deferNext` if it finished none.
- A requester network without a controller is ad-hoc: 8 channels. Nine ME Chests plus the CPU leave the federated
  requester with no CPU (`getCpus()` empty while the CPU block is formed and on the grid). Bank cells live in one ME
  Drive (one channel, up to 10 cells) above the requester chest. Output-cell chests are checked against the ad-hoc
  limit before an order (`channelLimit`) and for channels while it builds (`requesterWithoutChannel`).
- Growing a formed CPU by `setBlock` over its blocks throws in AE2 `CraftingBlockEntity.updateSubType`
  ("node has already been initialized") while the old cluster breaks. A persistent base removes its old CPU blocks
  before placing the larger CPU.
- Machine earning: the target's proof uses every enabled adapter minus blocked recipes. Each shelf adapter the base
  does not own is earned first: `PlaythroughMachineAdapter.equipment` (default: the block at the machine position, its
  AE2 upgrade cards, the recipe's catalysts; Energizing adds the rod) is surveyed once per recipe in the scratch area,
  then taken from base storage (both bases equally), supplied when Phase 0 provides it, or ordered. Equipment orders
  acquire their own sites' parts recursively with a cycle guard. Once every adapter of the proof is owned, that same
  proof runs. Computing a second proof from owned machines only picked shorter stand-in routes and left the earned
  machine unused.
- An unobtainable part blocks the recipes that need it until the base owns another machine. If a target has no proof
  only because of blocked recipes, it is `machine-unobtainable`, not `both-fail`. AE2 Lightning Tech's Lightning
  Simulation Room comes only from `lightning_transform` (a world strike, no adapter); the Assembly Chamber is made in
  the Simulation Room and the Overload Processing Factory in the Chamber, so none of the three can be earned.
- Per-order stock balance: after = before + plan expected − used + source extractions. Reused stock is used − source,
  between 0 and the stock before. Stock is compared per member (`PlaythroughFactory.members`: requester = chest + bank
  + output cells, materials, factory, extra faces by name): everything in `requester`, other members empty, and the
  federated members equal the baseline's (a member the baseline lacks = empty). `arrived` is the requester's delta.
- Settling waits (≤400 ticks) for no busy CPU/provider and no owed/transit/jobs/unknownJobs in the ledger for every
  NetworkId seen (`refreshNetworks`). Ledger `jobs` of a consumer vanish on the 20-tick sweep once nothing is owed.
  A divergence or unsettled base → class `diverged`, `stopReason`, GameTest fails, tool exits 1
  (`stopped-on-failure`). Failed restart checks and a base not idle within 1200 ticks do the same.
- A fixture exception inside one order fails that order only: IllegalStateException → `fixture-limit`, anything else →
  `harness-error`; neither blames a base.
- `endOrder` restores a disturbance the order ended inside before cancelling the job; `order()` refuses to start while
  a cut block is missing or a base rule is disabled. `disturbanceTicks` (default 40) lengthens a disturbance; with a
  long one, a STORAGE or router cut still lets the job finish (forced `baseline-only` rows, stock equal), but a cable
  cut stalls it, and `retire` then discards the machine's in-flight items, so the debt never settles and the run stops.
- Log watch: both bases share the server thread, so only Federation loggers / Federation frames / `$ae2federation$`
  mixin handlers are charged to the federated base; other errors are unattributed → both-fail. Harness packages
  (`...compat.`, `...test.`) are recorded but do not fail orders. The window spans previous order end → settle.
- Runner: `--resume` loads the latest checkpoint's `properties.json`; only stopAfterSeconds, wallSeconds and sprint
  may change. A clean boundary counts only with server status pass and exit 0. Task IDs are pinned in the state.
- Chunk tickets are released when the run ends (completed, failure stop, harness error, failed attach) and forced again
  on resume; a boundary keeps them for the next process.
- Equipment owned by a base is taken out of ME storage and never returned; retired sites discard their drops.
  `state.sources` / `machineTimeline.*.equipmentSources` split each take into stock, crafted (equipment order output,
  tracked in `state.crafted`) and provided.
  One site per machine recipe, so a proof with eight Inscriber recipes owns eight Inscribers.
- `waitedFor` is the final proof's machines that the base did not own when the target was first considered; a first
  proof's machine can be blocked and replaced by another (AE2 processors: Overload Processing, then the Inscriber).
- Stock reuse is reported only for passing results: a plan that only simulated (missing inputs) uses nothing.
- Quest task SNBT keeps empty compound keys (`draconicevolution:creative_capacitor`: `{"":"BOOLEAN"}`); parse it with
  `QuestCoverageCompatGameTests.parse`, which Phase 1 and Phase 4 share, never `TagParser.parseTag` directly.
- Huge plans: `progression.maxCrafts` (60000) records `craft-limit` before execution
  (`compactmekanismmachines:compressed_wind_generator_x532480` plans 2,848,884 crafts); `progression.exclude` skips
  items outright. `appflux:core_256m` (56,115 crafts, about 10.5 minutes per base) fits one 1400-second process
  only by itself.
- Creative/infinite cells fail planning in both bases because their inputs carry item components
  (`extendedae_plus:basic_core` with `core_stage`/`core_type`), and stand-in supplies are plain keys. The plan-inputs
  reason now lists the missing keys.
