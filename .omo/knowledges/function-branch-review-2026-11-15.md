# feature/function-system review (2026-11-15)

Read-only review of `feature/function-system` at 44b3eaa1 (313 commits over dev's 9b946c94), asked by the owner
("尽可能进行深度review"). Done by reading the code directly (no review agents, per the owner's 2026-11-13 rule), with
the 2026-10-30 review (`automation-review-2026-10-30.md`) as the baseline. Focus: what changed since 556d7a45 — the
recipe viewer sync, pattern/tunnel/device calls, program crafting, the scheduler and runner limits. Nothing was
changed; two throwaway timing tests were run and deleted.

## Findings

1. **A run can stall the server tick for seconds (confirmed, measured).** `ProgramScheduler` ticks every run and
   every condition with `CREATIVE_STEPS` (10,000 steps) on every device, and `Meter.MAX_WORK` lets each step walk up
   to 5,000,000 values. A loop of `ys = xs[:]` over a 100,000-item list took 1.8 s per tick, `xs == ys` 2.2 s
   (one run; an installation can run 4 in parallel). The guide (`automation/language.md:71`, `compute.md:22`) says
   `while True:` cannot freeze the server. The editor's dry run is a second way in: `DryRun.BUDGET` is 4,096 steps
   under the same per-step cap, run at once in the packet handler (throttled to one per 10 ticks per player), with no
   installation needed. Compute (CU) only admits installations and never limits a run's speed. My recommendation, not
   a decision: a per-tick work budget (values walked, or wall time) instead of a bare step count.
2. **Recipe sync never finishes a category that grew far past its saved size (code traced).** `ViewerScans.next`
   sets a saved category's bucket bits from the saved size only (`recipes.split`), never from the count the viewer
   listed, and a saved file splits only past 2,000 recipes a bucket. `prints` refuses a task whose fingerprints pass
   32,768 (eight buckets, so about 4,096 a bucket): a dense category that roughly doubles (pack update, scripts
   added) is enough. The refused viewer is stopped for the rest of the round, the task goes to the next viewer, which
   is stopped too, and the category never syncs. My recommendation: re-bucket to `max(split, bitsFor(listed.count()))`
   before leasing.
3. **Each run start and each condition check re-runs the checker (measured).** `ProgramRun.start` calls `Checker.check`
   on the pushed file every start; 15 ms for a 62,000-character file (after JIT warm-up). A condition with interval 1
   costs that every tick per installation. Could be cached per prepared version and catalog revision.
4. **Rescan of a category no viewer lists keeps the round open (code traced, low).** The Recipe sync page offers
   Rescan on unlisted rows; `ViewerScans.rescan(category)` adds forced work no viewer can take, so the round stays
   open and "checked at" does not update until a full rescan or a reload.
5. **Design notes, not bugs.** Program crafting plans that fail (missing items) are dropped with a debug log only, so a
   program learns of it only through `ae2.crafting`/`delivering`. Any player's viewer can add processing recipes that
   every program's `mc.recipes_for` then lists (an existing assistant choice, mirroring AE2's terminal).

## Checked and fine

- Recipe sync parts over 32,767 bytes: in NeoForge 21.1's patched `CustomPacketPayload.codec` a registered payload
  takes its codec from `NetworkRegistry.getCodec`; the 32,767 cap is only on `DiscardedPayload`, the fallback for
  unknown IDs. `GenericPacketSplitter` splits large packets both ways. So the client's 40-byte estimate for skipped
  entries with IDs is harmless.
- `CraftingCpuLifecycle.jobLink`/`stillToMake` (the branch's only crafting-path change) read AE2's counts as the
  October fixes intended.

## Not covered this time

- `PatternProjection`, `NeoEcoBatching`, `DataEnergisticsCountedProjections` differ from the merge base only by dev's
  later commits (not the branch's); they belong to a dev review.
- The ECO - N computation package (reviewed 2026-10-30), the editor and graph client code, and the language checker
  beyond its run limits were not re-read.
- Library call ranges (`ae2f.select` reach 1-4, priority, config slot, redstone level) are checked in
  `Evaluator.checkLibrary` from `StandardLibrary` signatures.
- Lease handling (leave, expire, refuse, give back) and the client keep-alive parts agree with each other.

## Fixes (2026-11-15, owner: "以上内容都要进行修复")

The owner chose to count a run's work by values processed (over wall time), and a server switch for recipe uploads
defaulting to everyone. Everything else below is an assistant choice.

1. **Work budget per tick.** `Runner.tick(steps, work)`: a node costs `Runner.STEP_COST` (20) plus what `Meter`
   counts for it: values walked (`Meter.work`) and items made (`Meter.made`), now both in `Meter.taken()`. A node that
   passes the budget finishes; the excess is `owed` and later ticks pay it first (`mc.wait` still counts down, an
   answer that came is still taken). `Ops` now charges what it scans or copies: equality and order by items and
   characters, `in`, `count`/`index`/`remove`, `insert`/`pop(i)` by the items that move, `sort` n log n plus string
   comparisons, slices, `strip`, `int`/`float` of a string, dict/set copies, and every `ListValue` copy.
   Substring search (`in`, `find`, `count`, `replace`, `split`) uses `Ops.Search`: Java's `indexOf` for needles up to
   16 characters, Knuth-Morris-Pratt above, because `indexOf` is quadratic for a long needle that almost matches.
   `ProgramScheduler` gives each run and condition `WorkShare.PER_RUN` (200,000 = the old 10,000 plain steps) and all
   of them `PER_TICK` (2,000,000) a tick; once spent, the rest get nothing and the next tick starts with the first
   installation left out. Dry runs stop at `DryRun.WORK` (1,000,000) as well as 4,096 nodes.
   Measured (test JVM, ms per tick of one run): copy a 100k list 1750 -> 1.5, compare two 2150 -> 1.7,
   `s.count` on 100k chars 1441 -> 1.2, `1 in xs` 1507 -> 1.3, plain steps 0.45 -> 0.45.
   Tests: `TickWorkTest` (6), `WorkShareTest` (5), `DryRunTest.theWorkBudgetStopsAHeavyLoop`.
2. **Grown categories.** `BucketedRecipes.split(listed)` / `ViewerRecipes.split(category, listed)`: before leasing,
   a saved category also splits for the count the viewer lists. GameTest
   `aCategoryThatGrewFarPastItsSavedSizeStillSyncs` (3 saved, 40,000 listed) fails on the old code (stuck at 3).
3. **Check reuse.** `ProgramRun.check` keeps each file's `Checker.Result` with a `RecordedCatalog` of the catalog
   questions it asked; a start reuses it while the catalog gives the same answers (`ProgramRunCheckTest`).
4. **Unlisted rescans.** `ViewerScans.rescan(category)` does nothing when no ready viewer lists it; the Recipe sync
   page shows no Rescan (nor its note) on such a row, failed IDs and the failed note stay. GameTest
   `aRescanOfACategoryNoViewerListsLeavesTheRoundDone`; UI scenario `ui.viewer-recipe-sync-unlisted`, before/after in
   `build/recipe-sync-unlisted-check/unlisted-row-before-after.png`.
5. a. **Crafting not started** is logged to the installation: `ProgramScheduler.note`, from `ProgramCrafting`
   ("crafting 4 minecraft:stick not started: missing minecraft:oak_planks x2", plan failure, no free CPU of the name,
   submit refused). GameTest `programCraftingMissingMaterialsIsLogged`.
   b. **`recipeViewerUploads`** (`[automation]`, `EVERYONE` | `OPERATORS`, `ViewerUploadRule`): with `OPERATORS`, a
   non-operator's hello is dropped and a ready viewer that lost the right gets no more tasks; the singleplayer/LAN
   owner always may. GameTest `viewerUploadsForOperatorsLeaveOtherPlayersOut`, unit `ViewerUploadRuleTest`.

Guide pages updated (EN and ZH): `language.md` (work per tick), `compute.md`, `library.md` (upload switch, crafting
log line).
