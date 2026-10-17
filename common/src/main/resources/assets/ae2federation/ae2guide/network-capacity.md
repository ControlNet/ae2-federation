---
navigation:
  parent: index.md
  title: Network Capacity
  icon: ae2federation:cable
  position: 22
---

# Network Capacity

Every piece of <ItemLink id="ae2federation:cable" /> counts how many networks and devices it carries, and carries at
most 8. When any cable of a Federation domain carries more, the **Federation domain stops**: its networks keep working on
their own, but share no storage, crafting, processing or power until the count fits again.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/network_capacity.snbt" />
  <BoxAnnotation color="#915dcd" min="9 0 0" max="11 1 1">
    Network A: counts 1
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="8 1 0" max="9 2 1">
    Network B, on the same Switch: counts 1 more
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 0 0" max="8 1 1">
    Left segment: 2 (subdomain), networks A and B
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="6 0 0" max="7 1 1">
    Router: ends a segment
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="6 1 1">
    Middle segment, through the Switch: 4 (Federation domain), as it runs between two Routers
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="4 1 0" max="5 2 1">
    Network C: counts 1
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Router: ends a segment
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0 0" max="2 1 1">
    Right segment: 1 (subdomain), the Endpoint
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
    Processing Endpoint: counts 1
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## What counts

* **Each ME network on a Switch counts 1.** A network that touches several Switch faces still counts once.
* **Each Processing Endpoint and each Federation Pattern Provider counts 1.** A Provider counts as a device of its
  own, even when its network is also on a Switch.

Neither rules nor power play a part: a network counts as soon as it joins the domain.

## Segments

Federation Cables joined to each other form a segment. A <ItemLink id="ae2federation:switch" /> does not end a segment,
and neither does a pair of <ItemLink id="ae2federation:federation_p2p_tunnel" />s: the cable on both sides belongs to
the same segment. Only a <ItemLink id="ae2federation:router" /> ends one, and a Router built from several blocks counts
as one Router.

* A segment that touches **no Router or one Router**, together with the networks on its Switches and the Endpoints
  and Providers on it, is a **subdomain**: the part of the domain that counts on its own. The segment counts only its subdomain.
* A segment that touches **two or more Routers** carries traffic between them, so it counts everything in the
  [Federation domain](mechanics.md).
* A network whose Switches sit on two segments counts in each of them.

In the scene, the left subdomain counts networks A and B, and the right one the Endpoint. The middle segment runs from
one Router to the other, so it counts all four, network C on its own Switch included.

## When a cable is full

A stopped domain changes nothing in the world: every block stays where it is, and the domain starts again by itself
as soon as no cable carries more than 8. To get there, take a network or device off the full segment, or end the
segment with a Router: what stands behind a Router no longer counts on a segment that touches only that one Router.
A segment between two Routers counts everything in the Federation domain, so a long trunk between Routers fills first.
Build such a trunk as a [bundled cable](bundled-cables.md): a 2×2 bundle carries 48, and a 3×3 has no limit. A
domain's [Beam Converter](items/beam_converter.md) must match its largest cable, so a new bundle needs a converter of
its size.

The light inside each cable shows how full it is: thin and dim when it carries little, fuller and brighter near the
limit. A cable over the limit pulses red, so the full segment is easy to find. The domain's topology screen turns dark
red, and every rule in it reads "over capacity". With connected domains
shown, only the stopped domain's plate turns red.

The server can change the capacity of a cable, or turn the limit off, in `config/ae2federation-server.toml`;
a file of the same name in a world's `serverconfig` folder sets it for that world alone.

## Seeing the count

With the Jade mod installed on both the server and your client, look at a Federation Cable:
Jade adds a line such as "Capacity count: 4/8 (Federation domain)" or "Capacity count: 2/8 (subdomain)", the count in red
once it is over the limit. Without Jade, the light inside the cable still shows how full it is.
