# Federation P2P tunnel part (2026-10-08)

`p2p/FederationP2PTunnelPart` extends AE2's `P2PTunnelPart`. Each tunnel is its own Federation Domain node
(`FederationDomainNodeId` with `part` = its side), with:

- a front port named after its side, resolved by a `CableFacePort` built with the part name and a change callback;
- one port `tunnel:<peer node id>` per linked tunnel of its frequency, chosen by `TunnelLinks.linked`. Links hold while
  the tunnel itself, the frequency's input and the peer are all active. Outputs link to one another only through a
  working input.

It publishes no Native evidence, so the carrier ME network is never a member.

## AE2 behaviour it relies on

- `P2PService.getOutputs(freq, cls)` returns nothing unless the frequency's input is an instance of `cls`.
  `P2PTunnelPart.getOutputs()` also needs the caller online, so the part reads the service directly.
- AE2 notifies only some tunnels of a frequency (`updateTunnel`), and a channel change reaches only the tunnel itself.
  So every trigger republishes the whole group, deferred one server tick and skipped while the server stops, as the
  Bridge does. The triggers are `onMainNodeStateChanged`, `onTunnelNetworkChange`, `onTunnelConfigChange`, the
  front neighbour changing, and the front capability being invalidated.
- Attunement: `P2PTunnelAttunement.registerAttunementTag(item)` in common setup.
  - Tag `data/ae2federation/tags/item/p2p_attunements/federation_p2p_tunnel.json` lists `ae2federation:cable`.
  - Using a listed item on any P2P tunnel replaces it through `host.replacePart`, keeping its frequency and role.
- Models: `P2PModels(front)`. The front model's parent is `ae2:part/p2p/p2p_tunnel_base`, with `type` set to our panel,
  which shows only the centre 8x8 of the image. The item's parent is `ae2:item/p2p_tunnel_base`. Only the front model
  needs `PartModels.registerModels`; the status and frequency models are AE2's.
- `FederationPortCapability.BLOCK` is registered on the part class through AE2's `RegisterPartCapabilitiesEvent` on
  the mod bus.

## Pitfall: the front port must be stateless

A neighbour's `BlockCapabilityCache` keeps what it read, including null. A cable bus that loads adds its parts to the
world only on its first tick, after a neighbouring Federation cable may already have read the capability.

When the port depended on the part being in the world, the cable cached null across a save/reload. The tunnels still
linked to each other, but neither front reached its cable. The port is now returned whenever the side matches, like
the Federation cable's. The registry's reciprocity check keeps a port that is not yet published harmless.

## Tests

- `FederationP2PGameTests`, ids `p2p.*`. It is test-only scaffolding.
  - `test/p2p/FederationP2PScene` reuses `RouterStorageMountFixture`'s networks, and its `placeRouters()` places the
    Routers without the cable run.
  - Tunnels are made as a player makes them: an ME P2P tunnel attuned with a cable stack (`onUseItemOn`), then paired
    with a memory card through a mock player (sneak on the input, plain use on each output).
- `TunnelLinksTest` (JUnit) pins the link rule. Mutation checked: dropping the input-active condition fails
  `anInactiveInputLinksNothing`.
- Making only the input inactive inside one AE2 Grid needs channel starvation on one branch, which is fragile, so the
  GameTest cuts the input's cable instead.
- Panel art: `tools/visual/p2p_panel_texture.py` draws the teal panel from scratch. It is not derived from AE2 pixels.

## Across dimensions (`p2p.across-dimensions-*`)

`test/p2p/QuantumP2PCarrier` (test-only) builds one carrier network over a real Quantum Network Bridge:

- The overworld half has its bus and tunnel at (4, 2, 3), facing west.
- The nether half has its bus at (1, 0, 3) in the site, with the tunnel facing south.
- Each half has its own creative energy cell, and the frequency is random.
- Pairing waits until the bridge has joined both halves into one Grid.

The tests:

- `shares-networks`: an overworld consumer with no energy of its own and a nether provider.
  - The consumer sees the provider's storage, runs on its power under an ME power rule, and gets its pattern.
  - `site.reloadBlockEntities()` then unloads and reloads the nether side; the nether nodes must leave the domain in
    that same tick, and the link must return.
  - Mutation checked: a tunnel that keeps its node on unload fails it.
- `drives-endpoint`: an overworld Federation Pattern Provider maps, claims and drives a nether Endpoint, and the
  products come back.
  - FE sent into the relay (the cable in front of the Provider, asked from the Provider's side) lands in the machine
    beside the nether Endpoint.
  - Mutation checked: resolving the Endpoint in the Provider's level fails it.
- `chunk-unload` (not required): the site's tickets are released so its chunks unload for real, then it is forced
  again.

Pitfalls:

- An ME chest is a powered AE2 block with an internal buffer and takes FE itself. FE relayed to an Endpoint whose
  neighbour is an ME chest lands in the chest's buffer, not in the Grid's stored power, so the test measures the
  machine's own buffer (1000 FE = 500 AE).
- A `succeedWhen` step that mutates the world and then asserts repeats the mutation every tick while the assertion
  fails. Record the result when the step runs, and assert it later.
