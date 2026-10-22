# Automation guide audit against code (2026-10-22)

A read-only audit of the automation guide pages (EN + ZH) against `feature/function-system` found eleven
mismatches. Three were code bugs and are fixed; the rest were guide text.

## Code fixes

- **`ae2.crafting` counts what jobs will still deliver.** It used `ICraftingService.getRequestedAmount`, which counts
  only `waitingFor` (outputs of pushed patterns) on native CPUs: nothing while AE2 plans the job, nothing for a final
  output whose pattern is not pushed yet, so `keep_stocked` could order twice. `ProgramCrafting.delivering` now sums,
  per CPU whose job's final output is the resource, `waitingFor + pendingOutputs` capped at the job's amount
  (`CraftingCpuLifecycle.stillToMake`; addon CPUs without `getPendingOutputs` count their whole job), plus the plans
  programs asked for (`Pending.amount`). Intermediates no longer count: they are consumed, not stored.
- **`ae2.cancel(resource, network)` honours `network`.** Pending plans match by grid; started jobs match when their
  CPU is in one of the named grids' CPU lists (grid objects change when networks re-form).
- **A paused device keeps its events.** `ProgramEvents` clears grid and block events every tick, and the paused branch
  of `ProgramScheduler.tick` skipped `checkEvent`, so those events were lost. The paused branch now calls
  `checkEvent`, which only sets the gate's `waiting`; the run starts on resume. Schedule, condition and resource
  triggers already caught up.

Tests: `ProgramHostGameTests.programCountsAJobAndCancelsItOnItsNetwork` (before the fix: `True 0 True 0 False 0`),
compat `NeoEcoComputingCompatGameTests.aPausedDeviceKeepsItsEventsForLater` (fails without the fix:
`[paused, resumed]`, no run). A device pauses only when its capacity drops after installing: `install` refuses past
the thread count, so the test takes the cell out of a device whose installation reserves fixed bandwidth.
Run it with `python3 tools/compat_run.py --accept-eula --tests neoecoae-computing neoecoae`. A test that throws a
non-assertion exception fails every test in its batch.

## Guide facts fixed (both languages)

Return type optional (inferred); breaking a device removes its installations, settings changes stop runs,
`ae2.cancel` cannot reach jobs from before a restart; `set_filter` is storage only; `network=None` means every
network through its own storage, not "as a terminal sees it"; `mc.wait` is not an action; the harvest interface faces
the wheat; strings share the 100,000 cap; programs hold at most 64,000 characters; Delete is refused while another
program calls it; Push takes the called programs as they are then and is refused unless all check clean, and pushing
a called program does not update its callers; pause conditions include threads; `ae2.craft` asks for a job and AE2
may drop the plan.

## In-game evidence

`build/automation-guide-audit-check/`: `crafting-count-before-after.png` (dedicated server, scene from `scene.txt`,
`/ae2federation programs run|log`: before `crafting 0`, after `crafting 4`) and `library-page-before-after.png`.
A stick pattern from commands: `ae2:pattern_provider{patterns:[{Slot:0,id:"ae2:crafting_pattern",count:1,
components:{"ae2:encoded_crafting_pattern":{inputs:[9 stacks, {} for empty],result:{...},recipeId:"minecraft:stick",
canSubstitute:0b,canSubstituteFluids:0b}}}]}`; a filled cell: `"ae2:storage_cell_inv":[{"#t":"ae2:i",
id:"minecraft:oak_planks","#":64L}]` in `ae2:chest{inv:{item1:{...}}}`.
