# Routers placed face to face link directly (2026-10-03)

The user found the old behaviour in the guide and asked for it to change. Two Routers placed face to face used to stay
disconnected. DESIGN.md never asked for that; it was a side effect of how a face resolves.

## Why they did not connect

- Each Router face owns an AE2 boundary node (`CANNOT_CARRY`, 0 idle power), exposed on that face only.
- Two facing Routers therefore exposed nodes toward each other, and AE2 joined the pair into a two-node Grid of its
  own.
- `RouterFacePort.resolve` then saw both a native attachment (the other boundary node, in the same Grid) and a valid
  reciprocal `FederationPort` (the other Router's face). "Both" resolves as `DISCONNECTED`.

## The fix

`RouterFacePort.hideNodeIfFederation`: a face that touches a valid reciprocal Federation port stops exposing its
boundary node (`setExposedOnSides(EnumSet.noneOf(...))`). AE2 needs both sides exposed to connect, so no Grid forms and
the face resolves as Federation.

- **When the Router is placed**, `initialize()` decides this before `boundaryNode.create()`, reading the neighbour's
  capability directly, because the capability cache only exists after the node does. A Router placed next to an
  existing one never joins its face for a tick. It also skips seeding the face's identity from a neighbouring
  boundary node.
- **On every later resolve**, `resolve()` applies the same rule before the native check. `InWorldGridNode
  .setExposedOnSides` runs `updateState()` synchronously, so a stale connection is already gone when the check runs.
- **When the port goes away**, for example when the other Router is replaced by an ME device, the face is exposed
  again. AE2 rescans, the node listener marks the face dirty, and it resolves as native.
- The rule never names the Router. Provider and Endpoint fronts expose no ME node on their front, so hiding the
  Router's node there changes nothing.
- `RouterTopologyContractTest` still finds the literal `setExposedOnSides(EnumSet.of(face))` in the builder.
- The Federation screen needs no change. Its topology comes from the domain registry's nodes and memberships, not
  from walking Cable blocks.

## Checks

- `router.adjacent-federation` / `routeradjacentfederation` is the sixth Task 12 case, with 9 assertions.
  - Routers are placed one after the other.
  - Both touching faces resolve as FEDERATION, both of their boundary nodes have no connections, and the link is
    reciprocal.
  - The far-face networks share a domain.
  - Replacing the second Router with an ME Chest turns the first face back to NATIVE_ME with a connection.
  - The test failed with DISCONNECTED before the fix.
- Updated alongside it: the manifest row, the `RouterTopologyContractTest` case set, the QA gradle file
  (`taskTwelveCases`, the assertion map, a semantics block, the case fan-out), `docs/acceptance-matrix.md` and
  `docs/testing/manual-client.md`.
- Passing:
  - federationVerify on all six router cases.
  - `federationTaskTwelveEvidenceSelfTest`.
  - The domain merge-split, redundant-membership and partial-unload GameTests.
  - `identityrouterbeforecable` and `storagecontrollerdriverouter`.
- Not covered: shared ME power across a direct Router pair. It goes through the same domain-driven energy overlay as
  a path through Cable, but every `energy.*` case has Cable between its Routers.
