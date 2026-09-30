# Federation Pattern Provider: Federation face and entrance (2026-09-30)

Follows the Endpoint redesign in `endpoint-federation-face-design-2026-09-29.md`. The user confirmed on 2026-09-30:

1. **Domain access goes through the Provider's own Federation face.** The five other faces join the source ME
   network; the FRONT joins a Federation Domain. The Provider may use only Endpoints of the domain its FRONT is a node
   of. The source network being a member of the Endpoint's domain by another route (a Router face, a Bridge) is not
   enough.
2. Manual mapping (pattern slot → Endpoints, many-to-many) stays.
3. Claim, retention and release stay as they are.
4. **Right-click always opens the domain workspace.** No sneak-use. AE2's own Pattern Provider screen is reached from
   the workspace's button (formerly "Back to provider").
5. Nine pattern slots stay.
6. The Provider is not drawn on the topology graph.
7. Texture later.

## How it is implemented

- `ProviderRuntime` takes the domain node of the Provider's Federation face (`federationFace`). The block entity passes
  `FederationDomainRegistryAccess.nodeId(level, worldPosition)`, the node its front publishes.
- `ProviderTargetAuthorization`: the Endpoint's domain must contain that node, and the source network must be a member.
  Otherwise the result is `FEDERATION_DOMAIN_DISCONNECTED`. A Lane that loses the face keeps its Claim, like an
  Endpoint whose cable is cut.
- `FederationPatternProviderBlockEntity.toggleEndpoint` refuses a new mapping to an Endpoint outside
  `federationDomain()` with `rejected-domain-disconnected`, before claiming. Unmapping is always allowed.
- The workspace:
  - `FederationDomainGraphProjection.providerEntries` lists a Provider in a domain only when its face node is a node of
    that domain (it used to list every Provider whose network was a member).
  - A Provider entrance (`forDevice`) picks candidates like an Endpoint entrance: domains containing its node.
- `FederationPatternProviderBlock.useWithoutItem` no longer looks at sneaking. The `#return_provider` button (lang key
  `workspace.back_provider`, text now "Edit patterns" / "编辑样板") opens AE2's screen.
- TEST-ONLY fixtures without real blocks use `SyntheticEndpointDomain.providerFace(sourceNetwork)`. It is one synthetic
  node per source network with that network's native membership, linked directly to each Endpoint. Pass it to
  `ProviderRuntime`.

## Tests

- `ProviderFederationFaceGameTests.providerFederationFaceRequired` (manualOnly):
  - Setup: the Provider's network is a member through the Router's west face, but its face points away.
  - Face away: the mapping is refused.
  - Face turned onto the cable: the mapping is accepted and the Lane is ACTIVE.
  - Face turned away again: `FEDERATION_DOMAIN_DISCONNECTED`, and the Claim is kept.
- `python3 tools/dev_gametests.py providerfederationfacerequired`

## Pitfall: capability-cache listeners must not invalidate the domain node

- Claiming or activating an Endpoint calls `invalidateCapabilities`. That fires the `BlockCapabilityCache` listener of
  every Federation port facing it.
- Three places own such a listener: `CableFacePort` (fixed on 09-29), `RouterFacePort`, and the Provider front
  (`FederationPatternProviderBlockEntity.rebuildFederationCache`).
- When the listener calls `invalidate()`, the node is dropped and **the whole domain is withdrawn in the same tick**
  (`federationDomainOf` is empty). It is republished one tick later with a new generation.
- Effects:
  - Open workspaces go stale.
  - Since mapping checks the Provider's domain, mapping a second Endpoint in the same tick fails with
    `rejected-domain-disconnected` (`productionproviderthreeway`).
- The rule: a listener only marks the port dirty (`recheck()` / `recheckFederationPeer()`). A changed neighbour block
  still reaches `invalidate()` through `neighborChanged`.
- Regression tests:
  - `endpointFederationFaceClaimKeepsDomain` (cable)
  - `...ClaimKeepsRouterDomain`
  - `...ClaimKeepsProviderDomain`
