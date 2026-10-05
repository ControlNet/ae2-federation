---
navigation:
  parent: index.md
  title: ME Federation Router
  icon: ae2federation:router
  position: 120
categories:
- network infrastructure
item_ids:
- ae2federation:router
---

# ME Federation Router

<BlockImage id="ae2federation:router" scale="6" />

The Router brings ME networks into a [Federation domain](../mechanics.md). Each of its six faces works on its own:

* touched by an ME cable or device, the face joins that network, without using a channel or idle power;
* touched by <ItemLink id="ae2federation:cable" />, another Router, a Pattern Provider front or an Endpoint front,
  the face links the domain onwards.

Up to six different networks can meet at one Router, and its faces never join them into one network. Two faces on the
same network are fine and count once. Two Routers placed face to face link directly, just as if Federation Cable
joined them.

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/router_hub.snbt" />
  <BoxAnnotation color="#915dcd" min="3 0 0" max="5 2 1">
    Network A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 2 1">
    Network B
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="2 1 0" max="3 3 1">
    Network C
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    One Router, three networks: each face joins the network it touches, and the networks stay separate
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen shows each network as its own card. Here networks A and C both use network B's storage:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="1" details="Drive|Energy cell" />
  <Network key="c" label="Network C" color="#5dcd70" column="1" row="0" details="Energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="2" row="1" details="Drive|Energy cell" />
  <Rule user="a" source="b" capability="storage" />
  <Rule user="c" source="b" capability="storage" />
</FederationTopology>

Right-click the Router to open the Federation screen for its domain, where you switch sharing on. See
[Getting Started](../getting-started.md).

<RecipeFor id="ae2federation:router" />

One craft makes four Routers.
