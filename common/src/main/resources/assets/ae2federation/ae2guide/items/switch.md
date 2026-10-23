---
navigation:
  parent: items/index.md
  title: ME Federation Switch
  icon: ae2federation:switch
  position: 420
categories:
- network infrastructure
item_ids:
- ae2federation:switch
---

# ME Federation Switch

<BlockImage id="ae2federation:switch" scale="6" />

The Switch brings ME networks into a [Federation domain](../mechanics/index.md). Each of its six faces works on its own:

* touched by an ME cable or device, the face joins that network, without using a channel or idle power;
* touched by <ItemLink id="ae2federation:cable" />, a <ItemLink id="ae2federation:router" />, another Switch, a
  Pattern Provider front or an Endpoint front, the face links the domain onwards.

Up to six different networks can meet at one Switch block, and they stay separate networks. Two faces on the same
network are fine and count once. Each Switch block uses 2 FE per tick ([Power](../mechanics/power.md)).

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/switch_hub.snbt" />
  <BoxAnnotation color="#915dcd" min="3 0 0" max="5 2 1">
    Network A: its energy cell powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    Network B: a drive and no power of its own
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="2 1 0" max="3 3 1">
    Network C: a drive and no power of its own
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    One Switch, three networks: each face joins the network it touches, and the networks stay separate
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen shows each network as its own card. Here networks A and C both use network B's storage, and all three run on network A's energy cell
through ME power:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="1" details="Drive|Energy cell" />
  <Network key="c" label="Network C" color="#5dcd70" column="1" row="0" details="Drive" />
  <Network key="b" label="Network B" color="#5CA7CD" column="2" row="1" details="Drive" />
  <Rule user="a" source="b" capability="storage" />
  <Rule user="c" source="b" capability="storage" />
  <Energy first="a" second="b" />
  <Energy first="c" second="b" />
</FederationTopology>

Right-click the Switch to open the Federation screen for its domain, where you switch sharing on. See
[Getting Started](../getting-started.md).

## Bigger Switches

Switches placed against each other form one Switch when together they fill a solid box, with each edge at most 8
blocks long: the same rules as for a [bigger Router](router.md). A Switch and a Router never form one block. Every outer
face of a formed Switch still works on its own, so a bigger Switch has room for more networks, and they still stay
separate.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/switch_multiblock.snbt" />
  <BoxAnnotation color="#dddddd" min="1 0 0" max="3 2 1">
    One Switch, 2×2×1: each outer face joins the network it touches
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="3 0 0" max="4 2 1">
    Network A: on two faces, counts once; its energy cell is the only power
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">
    Network B
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="1 2 0" max="2 3 1">
    Network C
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

A group that is not a solid box does not form, and none of its faces connect: its networks leave the domain until you
complete the box or take the extra blocks away. Right-click one of its blocks with an empty hand to see why on the action bar.

However large, a Switch does not end a cable segment: the cable on all its sides counts as one segment (see
[Network Capacity](../mechanics/capacity.md)). Only a Router ends one.

<RecipeFor id="ae2federation:switch" />
