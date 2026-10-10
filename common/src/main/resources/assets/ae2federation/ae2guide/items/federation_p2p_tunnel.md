---
navigation:
  parent: index.md
  title: Federation P2P Tunnel
  icon: ae2federation:federation_p2p_tunnel
  position: 135
categories:
- network infrastructure
item_ids:
- ae2federation:federation_p2p_tunnel
---

# Federation P2P Tunnel

<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/p2p_tunnel_part.snbt" />
</GameScene>

A Federation P2P tunnel carries <ItemLink id="ae2federation:cable" /> over an ME network. Tunnels on one frequency act as
if Federation Cable ran between their fronts, so the Switches, Routers, Pattern Providers and Endpoints in front of them share
one [Federation domain](../mechanics.md). It is a [P2P tunnel](ae2:items-blocks-machines/p2p_tunnels.md) like AE2's
own: it crosses a base without a cable run, and goes into another dimension through a Quantum Network Bridge, as in
[An Outpost in the Nether](../examples/nether-outpost.md).

## Getting one

There is no recipe. Right-click any P2P tunnel, such as an <ItemLink id="ae2:me_p2p_tunnel" />, with Federation Cable
in your hand: it turns into a Federation P2P tunnel and keeps its frequency.

## Linking tunnels

Pair them as you pair AE2's tunnels: sneak and right-click the input with a <ItemLink id="ae2:memory_card" />, then
right-click each output with it. One input can have many outputs. The link works both ways, and the outputs reach each
other too, as long as the input works.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/federation_p2p_tunnel.snbt" />
  <BoxAnnotation color="#915dcd" min="10 0 0" max="12 2 1">
    Network A: its energy cell powers network B too
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    Network B: a drive and no power of its own
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7.75 0.125 0.125" max="8 0.875 0.875">
    Tunnel input: on network A's cable, its front on the Federation Cable from network A's Switch
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0.125 0.125" max="4.25 0.875 0.875">
    Tunnel output: its front on the Federation Cable to network B's Switch
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="5 0.3 0.3" max="7 0.7 0.7">
    Network A's cable carries the tunnel across the base; to go through a Quantum Network Bridge, give the tunnels a network of their own
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Here network A uses network B's storage, and both run on network A's energy cell through ME power:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Drive|Energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Drive" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

Like AE2's tunnels, each tunnel needs power and a channel from the network it sits on. That network only carries the
link: it does not join the domain through the tunnels. When an end loses power or its channel, or its chunk unloads,
the link drops; it comes back on its own.
