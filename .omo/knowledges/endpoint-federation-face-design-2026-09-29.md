# Processing Endpoint: Federation-face design (confirmed with the user, 2026-09-29)

The shipped 0.0.1 Endpoint got this wrong: its "Federation face" was a fixed, inert EAST face (no
`FederationPortCapability`, no cable arm), the Endpoint was listed only when its own ME network was a Domain member,
and Provider → Endpoint needed a `PROCESSING` pair rule. The user's intended design:

1. **Orientation** — same AE2 orientation as the Federation Pattern Provider: FRONT is the Federation face, placed
   toward the clicked block, wrench-rotatable.
2. **Federation face** connects to a Federation cable, a Router, or a Federation Pattern Provider's front face. When
   connected, the Endpoint is a node of that Federation Domain.
3. **Other five faces** connect one ME network (the Endpoint's subnet). That network does **not** join the Domain (the
   Endpoint publishes no native membership evidence); it is only a processing target. Inputs go straight into the
   subnet's ME storage (no ME Interface); machine output pushed back into these faces returns to the owning Provider.
   All five faces share one node, so two different networks on two faces merge (same as an ME Interface).
4. **Access** — every Federation Provider in the same Domain can see and map the Endpoint; no rule is needed.
   Kept: one Provider owns an Endpoint at a time (Claim); the Provider's network must differ from the subnet.
5. **Policy** — the `PROCESSING` capability is removed from rules. Rules keep Storage, Crafting, ME power.
   `CRAFTING` is the native AE2 cross-network crafting (A ▸ B: A's terminal requests B's craftables with B's patterns,
   Pattern Providers and CPUs) and is unrelated to Endpoints.
6. **Topology** — each Endpoint is drawn as a small node on the Domain graph, linked to the network of the Provider
   that maps it (this replaces the "Processing ×N" link chip).
7. **Mode by what touches the Federation face** — Federation cable / Router / Federation Provider front → Federated
   mode; a native AE2 Pattern Provider (another network) → Local mode. Mutually exclusive; switching drains and
   releases the previous Claim first.
8. **No save compatibility** with 0.0.1 is required.
9. **Topology links only between networks that discover each other** — two ME networks "discover" each other when
   they share at least one Federation Domain. Only such pairs get a link (solid for rules, dashed candidates on
   selection) or appear in a network's connection list. Rules are editable only for pairs inside the workspace's own
   domain (the server already enforces this in `FederationDomainPolicySession.setPolicy`); related-domain pairs stay
   read-only. Before this change the selection drew dashed candidates to every shown network, including related
   networks that share no domain with it.

## How it is implemented (steps 1–3)

- **Authorization** (`ProviderTargetAuthorization`, `EndpointClaimAuthority`): the Provider's network must be a member
  of `federationDomainOf(endpoint node)`. A cut Federation face reports `FEDERATION_DOMAIN_DISCONNECTED`.
- **PROCESSING removed**:
  - `PolicyCapability` is STORAGE / CRAFTING / ME_POWER. `PolicyCapability.persisted(name)` drops a saved
    `"PROCESSING"` rule on load (`PolicyStateCodec`); any other unknown name is still malformed.
  - `PolicyOperation.EXECUTE`, `ProviderTargetState.POLICY_DENIED`, `DropHint.NO_RULE`, the wire's "rule" fact and
    the Endpoint's "Processing policy" button are gone.
- **Device entrance** (`FederationDomainPolicySession.forDevice`): an Endpoint opens the domain its Federation face
  node is in, provided the domain has at least one member. Its subnet's memberships don't count, so an Endpoint is
  never ambiguous. Other devices still match by their network.
- **Topology Endpoint nodes**:
  - Server: each `endpoint` choice carries `ownerNetwork`, the network of the owning Provider. `pairFlowText()` adds
    rows `{endpoint, events, amount, returnedEvents, returned}` summed from the owner's lane flows (`laneTotals`).
  - Client: `FederationTopologyView` draws `.graph-node-endpoint` buttons (`#graph_endpoint_<id>`, label
    "Endpoint · x, y, z", a click opens diagnostics). Placement is `client/policy/EndpointNodeLayout` (pure,
    unit-tested): on the outer side of the owner card, with unmapped Endpoints in a free row below and unlinked.
  - Pulses run along the card→node link; sends and returns also count in the throughput line.
  - `focusObject(endpoint)` selects the owner network. "Devices" counts the Endpoints its Providers map.
- **Rule 9**: every `networks` / `relatedNetworks` row carries `domains`, the related domains it is in.
  `FederationTopologyView.discovers(a, b)` is true for two members, or when the domain sets intersect. Dashed
  candidates and the connection list use it.
- **Test fixtures**:
  - T33 Endpoints face UP and reach the Router domain through real Federation Cable on the layer above
    (`TaskThirtyThreeWorldFixture.ENDPOINT_CABLES`, showcase `state.cables`).
  - Scale routes use TEST-ONLY `SyntheticEndpointDomain`.

## Pitfalls found while wiring the real Endpoint into the domain (2026-09-30)

- **A claim change republished the whole domain.**
  - Cause: `EndpointRuntime` calls `level.invalidateCapabilities(endpointPos)` whenever its item handlers change: Claim,
    activate, release, mode. The Federation Cable's `BlockCapabilityCache` listener used
    `CableFacePort.invalidate()`, which drops the cable's domain node at once. The node came back on the next tick, so
    the domain got a new generation. Every open workspace then went stale: "No live Endpoint", "Providers 0".
  - Fix: the cache listener calls `recheck()`. It only marks the port dirty, and `tick()` reports a change only when
    the peer differs. A removed or unloaded peer removes its own node.
  - Superseded later on 09-30: `neighborChanged` now calls `revalidate()`, which resolves the peer in place and
    republishes only a change; the hard `invalidate()` is gone (see `domain-placement-power-blackout-2026-09-30.md`).
  - Regression test: GameTest `endpointfederationfaceclaimkeepsdomain`.
- **A new domain republishes a few times before it settles.** T33 `ready()` now waits until the Router domain's
  generation holds for 20 ticks (log line `TASK33_DOMAIN`) before any workspace opens.
- **Mapping no longer depends on rule revisions** (step 2). The T33 step that expected "policy changed elsewhere"
  after an external Storage edit now expects the mapping to be accepted and the Endpoint to be claimed by the host.
- **The Endpoint's own entrance never reports "ambiguous".** Its Federation face is in exactly one domain, even when
  its subnet is a member of several.
- **Not in the manifest:** the Federation-face GameTests (`endpointfederationface*`) are `manualOnly` and not in
  `tests/scenarios/manifest.json`. Run them explicitly:
  `python3 tools/dev_gametests.py endpointfederationfacecable endpointfederationfacerouter endpointfederationfaceprovider endpointfederationfacerotate endpointfederationfacelocal endpointfederationfacesubnetmember endpointfederationfaceclaimkeepsdomain`
