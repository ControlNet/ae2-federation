---
navigation:
  parent: index.md
  title: ME Federation Bridge
  icon: ae2federation:bridge
  position: 130
categories:
- network infrastructure
item_ids:
- ae2federation:bridge
---

# ME Federation Bridge

<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/bridge_part.snbt" />
</GameScene>

The Bridge joins two networks that sit side by side, with no Router needed. Place it on a cable of one network, so
that its outer side touches a cable or device of the other network. The two sides stay separate networks; the Bridge
uses no channel and no idle power.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/bridge.snbt" />
  <BoxAnnotation color="#915dcd" min="3.375 0 0" max="6 2 1">
    Network A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 2 1">
    Network B
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    Bridge: on network A's cable, its outer side touching network B's cable
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen shows the two networks side by side, and links them once you switch a rule on. Here network A
uses network B's storage:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Drive|Energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Drive|Energy cell" />
  <Rule user="a" source="b" capability="storage" />
</FederationTopology>

A Bridge forms a small [Federation domain](../mechanics.md) with just those two networks. It does not connect to
Federation Cable, so Pattern Providers and Endpoints cannot reach other networks through it.

Right-click it to open the Federation screen for the two networks. If it is not connected correctly, right-clicking
shows what is wrong instead; see [Troubleshooting](../troubleshooting.md). Like other AE2 cable parts, sneak and
use a wrench on it to pick it up.

<RecipeFor id="ae2federation:bridge" />
