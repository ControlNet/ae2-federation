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

The Router carries a [Federation domain](../mechanics.md) onwards. Each of its six faces links to whatever touches it:
<ItemLink id="ae2federation:cable" />, a <ItemLink id="ae2federation:switch" />, another Router, a Pattern Provider
front or an Endpoint front. Use it to join and branch stretches of Federation Cable.

It does not attach ME networks: an ME cable or device touching a face stays unconnected. Bring networks in with a
Switch.

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/cable_connections.snbt" />
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Router: links the cables on both sides into one domain
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Right-click the Router to open the Federation screen for its domain.

<RecipeFor id="ae2federation:router" />
