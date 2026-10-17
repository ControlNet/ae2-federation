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

The Router ends cable segments, so that a [Federation domain](../mechanics.md) can grow without filling its cables
(see [Network Capacity](../network-capacity.md)). Each of its faces links onwards to
<ItemLink id="ae2federation:cable" />, a <ItemLink id="ae2federation:switch" />, a Pattern Provider front or an
Endpoint front. You do not need a Router to build a domain: Federation Cable connects and branches on its own.

It does not attach ME networks: an ME cable or device touching a face stays unconnected. Bring networks in with a
Switch. Right-click a Router to open the Federation screen for its domain.

## Ending segments

Federation Cables joined to each other form one segment, and a segment counts the networks and devices around it.
A Router ends every segment it touches. A segment that touches only one Router counts just its own **subdomain**:
the networks on its Switches and the Endpoints and Providers on it.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/router_segments.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Router: ends all three segments
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="0 0 0" max="1 2 1">
    Network A: its energy cell is the only power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="4 1 1">
    Segment: 1 (subdomain), network A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="8 0 0" max="9 1 1">
    Network B
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="7 1 0" max="8 2 1">
    Network C
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="7 1 1">
    Segment: 2 (subdomain), networks B and C
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 1" max="5 1 3">
    Segment: 1 (subdomain), the Endpoint
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Put a Federation Cable where the Router is, and the three arms become one segment that carries all four. A segment
that runs between two Routers carries traffic for the whole domain and counts everything in it, so keep such trunks
short, or build them as a [bundled cable](../bundled-cables.md).

## Bigger Routers

Routers placed against each other form one Router when together they fill a solid box. Each edge may be at most 8
blocks long, and 1×1×1 is a Router too. A Router built from several blocks counts as one Router: a segment touching
two of its blocks still touches only one Router. Every outer face links like a single Router's.

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/router_multiblock.snbt" />
  <BoxAnnotation color="#dddddd" min="3 0 0" max="6 2 1">
    Formed: a solid 3×2×1 box, one Router
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="2 2 1">
    Not formed: an L is not a solid box, so none of its blocks link anything
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

* A hollow, L- or T-shaped or protruding group does not form, and no part of it works on its own: all its faces
  disconnect until you complete the box or take the extra blocks away.
* A Router block that touches a finished box from the outside spoils it the same way; blocks that meet only at an
  edge or a corner stay separate Routers.
* Routers and Switches never form one block, even when they touch; they just link to each other.
* Right-click a block that is not formed with an empty hand, and the action bar says why; with a block in hand you
  build on it as usual. With Jade installed, looking at a Router shows "Structure: 3×2×1" or the reason it is not
  formed.

A formed Router is how a [bundled cable](../bundled-cables.md) meets ordinary cables: a bundle connects only to a
Router or Switch face that covers its whole end or side, and the Router's other faces take ordinary Federation Cables.

<RecipeFor id="ae2federation:router" />
