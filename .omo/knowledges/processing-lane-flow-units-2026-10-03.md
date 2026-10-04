# Processing lane flow keeps each resource type apart (2026-10-03)

DESIGN (connection flow effects) says fluids, items and energy each use their own quantity unit. The Provider lane
flow used to break that: `LevelObservabilityService.recordLaneFlow` summed every accepted amount on a lane into one
window, so 4 items plus 1,000 mB of water showed as "sent 1004" on the processing wire.

## Shape now

- `LevelObservabilityService.LaneKey(provider, lane, returned, keyType)`: one flow window per AE key type id
  (`AEKeyType.getId()`, e.g. `ae2:i`, `ae2:f`). `ProviderObservationRegistry` passes `resource.getType().getId()` for
  both sends and aggregate returns.
- `collectLaneFlow(provider, lane, returned, LaneFlowTotals)` adds a lane's active windows into
  `observability/meter/LaneFlowTotals`: delivery events summed (unitless), amounts kept per key type in a sorted map.
  It is MC-free, so JUnit covers it (`LaneFlowTotalsTest`).
- The policy session sends `laneSent` / `laneReturned` as JSON objects `{keyTypeId: amount}`. The endpoint flow rows
  in `pairFlowText` carry only `events` and `returnedEvents`. Their summed `amount`/`returned` fields were removed:
  nothing read them, and they mixed units.
- `FederationProcessingGraph` formats each entry with `AEKeyType.formatAmount(amount, AmountFormat.FULL)` (items with
  no unit, fluids in B, add-on types with their own symbol) and joins the entries with ", ". `AEKeyTypes.get` throws on
  an unknown id, so the client catches that and falls back to "amount id".

## Not changed

- The per-rule `pairFlows` rows (storage flow per policy key) still sum amounts across types into `amount`. The client
  reads only their events, so nothing shows the mixed number. Split them the same way before anything displays that
  amount.
- The Task 33 UI scenario only dispatches items, so a mixed item-and-fluid lane is covered by the JUnit test and by
  AE2's own formatter, not by a screenshot.

## Harness note

`federationUiTest` re-hashes the dirty worktree, including untracked files outside `.omo/`, after the run. If any file
such as `docs/ideas/*` is edited during a run, the run fails with "UI report source, dependency, product, or LDLib2
identity is stale" even when every scenario passed. Check `attempt-*/result.json` → `assertions` and
`dirty-identity.txt` across attempts to tell the two apart.
