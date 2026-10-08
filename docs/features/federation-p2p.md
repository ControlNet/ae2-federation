# Federation P2P

Status: implemented on 2026-10-08 (`1d47855`, `abda118`, `dfb3545`).

A Federation P2P tunnel is a mode of AE2's native P2P tunnel that carries Federation Cable connectivity over an ME
network, within one dimension or across dimensions. The former idea is preserved as an
[archived discussion](../archive/ideas/federation-p2p.md). Its survey proposals (inspection view, names, freshness
display and so on) are not current requirements.

## Behavior

- **Getting one:** there is no recipe. A player attunes any P2P tunnel with Federation Cable, through AE2's
  attunement tag `ae2federation:p2p_attunements/federation_p2p_tunnel`. The tunnel keeps its frequency and its role.
  It is also in the creative tab.
- **Pairing:** with a memory card, as for AE2's tunnels.
- **Cable-equivalent links:** tunnels of one frequency act as if Federation Cable ran between their fronts.
  - A tunnel's front is a Federation port, so a Federation Cable, Router, Pattern Provider or Endpoint in front of it
    joins the domain.
  - Links are bidirectional. One input with several outputs joins every end, and outputs reach each other too.
- **When a link holds:** only while both ends and the frequency's input are active (powered, with a channel).
  Outputs reach each other only through a working input.
- **Carrier:** the ME network carrying the tunnels never becomes a Federation member through them.
- **Power and channels:** as AE2's native tunnels: 1 AE/t idle and one channel each. There is no transfer tax,
  because a tunnel carries only topology.
- **Across dimensions:** a carrier that a Quantum Network Bridge joins across dimensions links its tunnels the same
  way.
  - Storage, ME power, pattern projection and Provider-to-Endpoint processing all work across the link, including
    returns and the FE relay.
  - Nothing forces chunks to load. As with AE2's Quantum Bridge, an unloaded end drops the link, and the link returns
    when the end loads again.
- **Appearance:** AE2's P2P base model with a teal type panel. The panel is drawn from scratch by
  `tools/visual/p2p_panel_texture.py`. Federation Cable draws an arm toward a tunnel's front.

## Runtime groundwork

Federation's runtime services (domain registry, policies, storage mounts, crafting projection, energy sharing and
observability) serve the whole server instead of one level each. Provider Lanes remember their Endpoint's dimension.
A block can hold several Federation nodes, one per part. See
[the P2P part notes](../../.omo/knowledges/federation-p2p-part-2026-10-08.md) and
[the cross-dimension test notes](../../.omo/knowledges/cross-dimension-gametests-2026-10-08.md).

## In-game guide

The [English page](../../common/src/main/resources/assets/ae2federation/ae2guide/items/federation_p2p_tunnel.md) and
[Simplified Chinese page](../../common/src/main/resources/assets/ae2federation/ae2guide/_zh_cn/items/federation_p2p_tunnel.md)
cover getting, pairing and running tunnels.

## Verification boundary

The following checks cover this feature:

- `TunnelLinksTest` and `FederationPortTest` (JUnit).
- `GuidePagesContractTest`, which covers the guide page.
- `FederationP2PGameTests`, the `p2p.*` manifest cases: attunement and sharing, outputs linking through their input,
  isolation, power loss and reload, and across dimensions over a real Quantum Network Bridge.
- The non-required `p2pacrossdimensionschunkunload`, which releases real chunk tickets.
- The `guidescenecables` compatibility check.

Not yet checked:

- The cross-dimension highlights in the policy screens have not been inspected in a real client.
- A Federation Pattern Provider in another dimension on a network that spans dimensions has no GameTest.

Use the project's [test instructions](../testing/commands.md) for execution.
