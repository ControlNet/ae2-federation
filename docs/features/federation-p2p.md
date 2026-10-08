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
  - Internally the input links to each active output and each output to the input, a star: N outputs publish 2N
    links instead of N(N+1). The Domains are the same, because outputs reach one another through the input. The
    Policy screens and the topology view still show every network of a frequency related to every other, as before.
- **When a link holds:** only while both ends and the frequency's input are active (powered, with a channel).
  Outputs reach each other only through a working input. A tunnel without a channel (for example the ninth on an
  ad-hoc network) gets no link until it has one.
- **Refreshes:** AE2 tells every tunnel of a frequency at once when the frequency changes. Each tunnel is published
  once per burst of such refreshes instead of republishing the whole frequency for every tunnel told.
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
  isolation, power loss and reload, across dimensions over a real Quantum Network Bridge, a moved output, an input
  taken off and replaced, and a channel shortage.
- The `cross-dimension.*` manifest cases: a nether Federation Pattern Provider projected to an overworld consumer, a
  Provider and an Endpoint in different dimensions at the same coordinates (restart, broken link, replaced Endpoint,
  real chunk unload of either side) and a nether level close. See the
  [crafting contract](../architecture/native-crafting-contract.md#cross-network-crafting).
- The non-required `p2pacrossdimensionschunkunload`, which releases real chunk tickets. The required unload cases are
  `cross-dimension.endpoint-unloaded` and `cross-dimension.nether-provider-unloaded`.
- The manual `p2prefreshscale2` to `p2prefreshscale31` GameTests, which pair 2 to 31 outputs on a carrier with real channels and counts refresh
  tasks, node publications and links. With 31 outputs, pairing published 1024 nodes and 992 links before the
  per-burst refresh and the star, and 32 nodes and 62 links after; the refresh tasks of the pairing took about 34 ms
  before and 1.3 to 2.2 ms after on the development machine.
- The `guidescenecables` compatibility check.
- A production client against the release JAR, mapping an overworld Provider to a nether Endpoint over P2P tunnels; see
  [the production JAR notes](../testing/production-jar.md#cross-dimension-production-run).

Not yet checked:

- A Pattern Provider on a carrier network that itself spans dimensions through the tunnels it carries.

Use the project's [test instructions](../testing/commands.md) for execution.
