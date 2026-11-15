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
