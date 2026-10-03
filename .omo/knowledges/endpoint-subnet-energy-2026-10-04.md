# Endpoint subnet energy (design decisions, 2026-10-04)

Problem: an Endpoint's subnet (the ME network on its five other faces) often has no power. It never joins the
Federation Domain, so the pairwise ME power rule cannot reach it, and inputs cannot enter its storage nor its machines
run.

## Decided with the owner

1. **Source: the network of the Provider that claims the Endpoint.** Whoever uses the Endpoint powers it; releasing
   the Claim ends it.
2. **Shape: the shared pool**, exactly as network-to-network energy sharing (AE2 energy overlay, Quartz Fiber style):
   mutual and transitive, so the subnet joins the owner's whole pool and the subnet's own cells feed it too. No
   one-way supply (that model was Task 31 and was replaced by the pool).
3. **Control: one switch per Endpoint, on by default.**
4. **Display:** the topology link from the owner card to the Endpoint node is drawn as a Quartz Fiber while it shares;
   the Endpoint shows its subnet's energy, and an unpowered subnet gets a reason. GUI parts go through the design
   canvas first.
5. **Local mode is out of scope for now** (owner decision, 2026-10-04). It was first wanted, then dropped once the
   cost was clear: the native owner network holds no Federation node to carry the energy connection, an in-world
   front node cannot connect (a directional Pattern Provider hides its push side; an omnidirectional one is rejected
   by `EndpointCapabilityComposition.claimDiscovered`), so it would need an Endpoint-owned second node joined with
   `GridHelper.createConnection` and managed by hand, and a Local Endpoint never appears in a domain topology.

## Implementation facts (from the code survey)

- Energy pools: `FederationEnergyConnection` (an `IEnergyOverlayGridConnection` node service) sits on Router face
  nodes and Bridge nodes only; `EnergySharingService.reconcileAll` builds a symmetric adjacency from ME power rules.
  Every Grid in a pool needs its own connection node, or AE2 logs "already has a power graph".
- The Endpoint owns one AE2 node, its subnet node; the service can be added in the constructor, as the Bridge does
  (the main node is created in a field initializer of `AENetworkedBlockEntity`).
- The Federation Pattern Provider's main node has no connection today, so its network joins a pool only through a
  Router face or Bridge on it; the owner side needs the service on the Provider node.
- Claim and release do not bump the domain topology revision, so they must call the energy reconcile themselves; the
  owner Grid comes from the claim's `ProviderIdentity` via `ProviderObservationRegistry`.
- `EndpointBlockEntity.loadTag` throws on missing tags, so the new switch tag must be optional and default to on.

6. **Switch in both places** (owner decision, 2026-10-04): the topology's Endpoint panel and the Provider screen's
   Endpoint detail. The design was cut down to one fact row, `ME power  On  [switch]`, at the end of the existing facts
   box: green On while sharing, yellow On with the reason as tooltip while waiting, grey Off. No energy value line, no
   banner, no "shared with" text. On the Provider screen another Provider's Endpoint shows the switch locked.

## Implementation (2026-10-04)

- `EndpointBlockEntity` and `FederationPatternProviderBlockEntity` each add a `FederationEnergyConnection` to their
  main node in the constructor. The switch is the optional tag `endpointShareEnergy` (missing = on), mirrored into
  `EndpointTargetBinding.sharesEnergy()` so the service never looks up block entities.
- `EnergySharingService.endpointEdges()` lists (subnet node, subnet Grid, owner Grid) for every claimed, Federated
  Endpoint with the switch on whose owner Provider is in `ProviderObservationRegistry` with a Grid, and the two Grids
  differ. `reconcileIfChanged` compares this list with the last one, so claim, release, switch, load, unload and Grid
  replacement all reconcile on the next tick without explicit bump calls. `sharesEndpoint(level, node)` feeds the UI.
- `BY_NETWORK` breaks ties with `System.identityHashCode`: a subnet Grid may have no confirmed id, and an unstable
  peer order would make `reform` invalidate pools on every reconcile.
- Energy follows the claim, not the Provider's Federation face: a Provider turned away keeps its claim and keeps
  powering the subnet.
- Action `SET_ENDPOINT_ENERGY` (wire id 16), target `EndpointEnergyTarget` (`uuid:epoch/1|0`). The domain menu
  treats it as a mapping action; the Provider menu refuses an Endpoint another Provider claims.
- Facts `energyTarget`, `energy`, `energyShared`, `energyReason` (`local`, `unclaimed`, `owner_offline`,
  `same_network`, `waiting`) are added only for domain Endpoints, not for an Endpoint shown alone without a domain.
- The topology draws the owner-card-to-Endpoint link with `energyLine` (Quartz Fiber) while sharing.
- The Provider screen rebuilds its facts on every choices update (lane flow sends them while machines run), so it
  keeps an unchanged switch Button instead of recreating it; a recreated button could drop a press.
- Found on the way: a Button's inline `buttonStyle` textures beat the stylesheet's `__disabled__` rule, so locked
  rule switches never looked locked. Both rule rows and the energy switch now pick the `*_LOCKED` sprite in code.
- The network stat value column became `min-width` + `adaptive-width`: a pool that includes a creative cell reads
  `1845T / 1845T AE`, which overflowed the fixed 80 px compact column.
- Tests: GameTest `endpointSubnetEnergy` (manual, like the other Federation-face tests; run with
  `python3 tools/dev_gametests.py endpointsubnetenergy`), JUnit `EndpointEnergyTargetTest`, UI checks in
  `ui.endpoint` (switch off and on, server state) and `ui.mapping` (own switch usable, other Provider's locked).

## Open

- Not covered by a test: the subnet Grid splitting or merging while claimed (the edge list then names a new Grid,
  which the tick comparison should pick up).
- A subnet that is itself a domain member may already share with the owner through a rule; both join the same pool.
