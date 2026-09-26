# Task 28 Native Automation Qualification

## Scope

Task 28 qualifies native AE2 automation across an authorized Federation Storage projection. Federation does not own
demand, scheduling, in-flight work, reservations, allocation, replay, or result buffering. Native `InterfaceLogic`,
`MultiCraftingTracker`, `ImportBusPart`, and `ExportBusPart` remain the behavior owners.

## Canonical Cases

- `automation.interface-stock`
- `automation.crafting-card`
- `automation.native-buses`
- `automation.reject-duplicate-demand`
- `automation.contention`

The evidence contract requires this exact five-case set, one native properties artifact and one execution log per case,
independent native-owner receipts, directional authority, and rejection of fully rebound owner, transfer, demand,
cancellation, contention, and authority substitutions.

## Fixture Lessons

- Seeded AE2 multipart nodes persist under the managed-node tag `gn`; reconnect restored bus nodes to their cable nodes
  with `GridHelper.createConnection`.
- Keep the second Interface at `BASE.south()`. `BASE.east(2)` touches the native crafting provider and collapses the
  intended provider/consumer Grid boundary.
- Crafting receipts must identify `target[0].getInterfaceLogic()`, the same owner used by `MultiCraftingTracker`.
- A fluid ME Chest added below an already-created provider cable can settle as an independent native identity before its
  later merge and block fixture readiness. The stable automation-only layout pre-places the fluid chest at
  `secondPosition.north(2)`, directly adjacent to the existing provider cable, and omits the callback probe in that slot.
- Real fluid qualification inserts 1500 mB water into the physical provider fluid cell, lets native Interface stocking
  request 1000 mB through the authorized projection, then observes 1000 mB stocked and 500 mB remaining.

## Verification

Canonical source-bound independent-authority repair evidence:
`.omo/evidence/task-28-authority-repair/attempt-20260920T115809462Z/result.json`.

The exact producer, persisted consumer, adversarial self-test, focused Task 9/21/25/27 native regressions, strict
`check build sourcesJar`, Java diagnostics, and diff hygiene passed. Task 28 remains unchecked pending independent review.

## Independent Authority Repair

- `AutomationAuthorityObservation` captures exact Interface owner objects and authorized projection objects before native
  operations, independently of semantic properties and `AutomationNativeObservation`.
- Runtime Mixin observations fail immediately unless the exact Interface owner or projection/key/operation tuple was
  pre-authorized.
- A topology change may legitimately replace the mounted projection. Capture each current projection after settlement and
  before its operation; persist the complete authorized identity set and require observed identities to be its subset.
- The persisted verifier rejects coordinated Interface-owner and projection substitutions against the independent receipt.
