# Manual test failure analysis: Bridge/Router with two controller networks (2026-09-27)

Static analysis of a player report (two networks, each creative cell + controller + drive + cell + terminal).
Not yet reproduced by a GameTest.

## Confirmed: reverse direction unreachable in a two-member domain

- `PolicyEditorSelection.initial` sorts members by UUID string and fixes consumer=0, provider=1.
- `nextDistinct` skips the excluded index, so with two members it always returns the current index.
- `FederationDomainPolicySession.selectTarget` rejects `consumer:<provider index>`, and the choice list
  (session ~L313) removes the opposite side's member. There is no swap action.
- Result: only "members[0] consumes members[1]" can be configured; the reverse policy key is never editable.

## Confirmed: Router/Bridge boundary node mints its own NetworkId when placed before its neighbor

- `NetworkIdentityGridService.addNode`: a node without saved data, created outside the CableBus
  `NativeIdentityInitialization` scope, in an empty fresh Grid, gets `newGridId` and is durable (not provisional).
- `RouterFacePort.initialize` / `MultipartBridgePart.seedBoundaryNodes` seed the boundary node only if a settled
  neighbor already exists. Router placed first, cables second => every face node has a fresh NetworkId.
- A cable later joining that face and network A produces a Grid with two network ids => `AMBIGUOUS_MERGE`.
  `adoptEstablishedLineage` returns early when durable ids > 1, so the new cable keeps whatever id it got from the
  first connected neighbor (possibly the Router's). After the Router is removed that cable still carries the foreign
  id, so network A stays unsettled permanently.
- Symptoms: Router shows no networks; a re-placed Bridge reports VALID topology but `bridgeFederationDomain` finds no
  confirmed ids, and the UI shows `bridge_reason.valid` text ("no unique domain with at least two networks"), which
  hides the real cause (identity unsettled).
- Recovery: break and re-place the cable(s) placed while the Router was present (the lineage lives in part NBT).
  Workaround: always build/connect ME cables first, place Router/Bridge last.

## Coverage gap

No GameTest uses an ME Controller or ME Drive, and no test places a Router/Bridge before its native neighbors.

## Fix (2026-09-27, dev working tree)

- Reproduced with `BoundaryIdentityGameTests` (`identityrouterbeforecable`, `identitybridgebeforeouternetwork`): both
  were `AMBIGUOUS_MERGE` before the fix. Run one test with
  `./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:runGameTestServer -PfederationGameTestId=<id>`.
- `storagecontrollerdrivebridge` (controller + drive + 1k cell on each side, Bridge, policy via `PolicyService`) passed
  even before the fix: the storage backend works once identities are settled, so the reported "nothing visible" was
  the one-direction UI limit and/or identity poisoning, not the mount path.
- Identity fix: `IdentityNeutralNodeOwner` marker on `RouterFacePort` and `MultipartBridgePart`. Their nodes keep a
  lineage (node id for energy sources, persistence) but never publish a registry claim, never count as durable
  evidence, never enter the provisional set. A naive "neutral nodes follow the network id" variant scanned all nodes on
  every add (O(n^2) on load); it is unnecessary because unclaimed lineage never affects settlement.
- Selection fix: `PolicyEditorSelection.withConsumer/withProvider/swapped`; choosing the opposite side swaps, and
  cycling in a two-member domain swaps. Session choice lists now include every member on both sides.
- Bridge diagnostic: VALID topology with an unconfirmed side now shows `bridge_reason.identity_unsettled`.
- Existing poisoned worlds are not healed automatically: a cable that already saved a foreign NetworkId must be
  broken and re-placed.
- Test layout lesson: two adjacent transparent cables connect directly; use different colors for the Bridge host cable
  and the outer cable.

## Router faces never bound a late-attached neighbor (2026-09-27, follow-up)

- Symptom: after the identity fix the Bridge worked, but a Router with one controller + drive network per face still
  showed no networks. `storagecontrollerdriverouter` reproduced it: the Router domain existed with `members={}` and
  every face binding was `Disconnected`.
- Cause: `RouterFacePort`'s node listener was a lambda, i.e. only `onSaveChanges`. A newly placed cable bus creates its
  AE2 node on its first tick, after the block update that dirtied the face, so the face resolved before the connection
  existed. The later `onInWorldConnectionChanged` / `onGridChanged` events were ignored. Existing tests placed cables in
  the same tick as the Router, so the Router's first-tick initialize already saw them.
- Fix: those two events set the face `dirty` only (no synchronous registry/service reconcile, because they fire inside
  AE2 Grid propagation and chunk unload); `RouterBlockEntity.serverTick` resolves and publishes. A changed `Grid` makes
  a new `NativeAttachment`, so the binding compares unequal and the Router republishes.
- Remaining gap: if a face publishes `Unsettled` evidence and the identity later settles with no node/grid event, the
  Router does not republish (the Bridge compares confirmed ids on every refresh; the Router does not).
