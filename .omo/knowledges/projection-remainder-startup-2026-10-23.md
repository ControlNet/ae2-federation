# Projected remainders during service startup

`PatternProjection.containerItems` correctly counts cake's three buckets before the real assembler consumes its
input holders. The router records actual arrivals as held when the requester CPU no longer waits for them. The
periodic `CraftingProjectionService.sweepReturns` must observe the completed native job, return held stock through
`HeldReturnTransfer`, and retire its ledger watch after delivery settles every debt.

The obsolete `STARTUP_TICKS = 100` gate suppressed that whole sweep. A short first job could finish and remain at
owed=3, held=3, transit=0, watched RUNNING even after 25 idle ticks. The focused vanilla cake GameTest reproduced
exactly that state with `ledgerSweeps() == 0`. An unrestricted eventual-success test passed before the fix; a second
cake in the same server also passed because the startup window had elapsed. This is bounded startup latency, not
evidence that debt would remain forever after normal sweeps start. Bucket craftability is not required.

The fix keeps the 20-tick cadence but removes the startup gate. Existing loaded-grid, live-link and CPU lifecycle
checks still decide eligibility. Actual accepted delivery remains the only operation that reduces debt; unloaded,
unformed and unknown CPU lifecycles retain ownership. No change to container derivation, storage priority, or ledger
persistence is needed for this reproduction.

New manifest tests:
- `projectioncakeremainders`: real vanilla cake through a native provider/assembler and a separate requester CPU;
  freezes the result after 25 idle ticks so an eventual return cannot hide the delay.
- `projectioncakecraftableremainders`: also offers the vanilla iron-to-bucket crafting pattern.
- `projectionpartialcancelledreturnpromptly`: existing hand-operated processor returns one stone, cancels the
  three-stone job, then releases the other two; verifies delivery and retirement after 25 ticks.
- `projectionendpointcancelledreturnpromptly`: existing explicit test-only processor returns through a real
  Federation Endpoint and Provider lane after cancellation, with the same bounded settlement assertion.

Run the focused tests with:

```sh
python3 tools/dev_gametests.py projectioncakeremainders projectioncakecraftableremainders projectionpartialcancelledreturnpromptly projectionendpointcancelledreturnpromptly
```

All four passed after the fix. The full fast gate passed 275/275 without SHARED results, and the strict build
passed with `--no-daemon` (the existing daemon saw a read-only project cache). Verification and diagnostic logs
are listed in the git-ignored
`build/remainder-fix-handoff.md`. Do not overlap dev GameTest runner processes: they share the runtime directory.
