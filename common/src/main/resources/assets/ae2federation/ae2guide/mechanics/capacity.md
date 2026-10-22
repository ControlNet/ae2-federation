---
navigation:
  parent: index.md
  title: Network Capacity
  icon: ae2federation:cable
  position: 240
---

# Network Capacity

Each piece of <ItemLink id="ae2federation:cable" /> carries at most 8 networks and devices. When any cable of a
Federation domain carries more, the **domain stops**: its networks keep working on their own and share nothing until
the count fits again.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/network_capacity.snbt" />
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

* **Each ME network on a Switch counts 1**, once, however many Switch faces it touches.
* **Each Processing Endpoint and each Federation Pattern Provider counts 1.** A Provider counts as a device of its
  own, even when its network is also on a Switch.

A network counts as soon as it joins the domain, whatever its rules and power.

## Segments and subdomains

Federation Cables joined to each other form a **segment**. Only a <ItemLink id="ae2federation:router" /> ends a
segment, and a Router built from several blocks counts as one Router. A <ItemLink id="ae2federation:switch" /> sits
inside its segment, and so does a [long-distance link](long-distance.md): a pair of P2P tunnels, Beam Formers or
Quantum Cubes joins the cable on both sides into one segment. Each link counts as cable of its size, so a pair of P2P
tunnels holds one cable's limit even between two Routers.

* A segment that touches **no Router or one Router** forms a **subdomain** with the networks on its Switches and the
  devices on it. It counts its subdomain only.
* A segment that touches **two or more Routers** carries traffic between them, so it counts the whole
  [Federation domain](index.md).
* A network whose Switches sit on two segments counts in each.

In the scene, the left subdomain counts networks A and B, and the right one the Endpoint. The middle segment runs from
one Router to the other, so it counts all four, network C on its own Switch included.

## Making room

A stopped domain leaves every block in place and starts again by itself once no cable carries more than its limit.
To get there:

* **Move a network or device** off the full segment.
* **End the segment with a Router.** What stands behind a Router stops counting on a segment that touches only that
  Router.
* **Bundle the trunk.** A segment between Routers counts the whole domain, so it fills first. Build it as a
  [bundled cable](bundled-cables.md): a 2×2 bundle carries 48, and a 3×3 has no limit. A bundle needs a
  [Beam Converter](../items/beam_converter.md) of its size.

## Seeing the count

The light inside each cable shows how full it is: thin and dim when it carries little, fuller and brighter near the
limit. A cable over the limit pulses red. The stopped domain's topology screen turns dark red, and every rule in it
reads "over capacity"; with connected domains shown, only the stopped domain's plate turns red.

With the Jade mod on both the server and your client, looking at a Federation Cable adds a line such as "Capacity
count: 4/8 (Federation domain)" or "Capacity count: 2/8 (subdomain)", red once it is over the limit.

## Server settings

The `[capacity]` section of `config/ae2federation-server.toml` sets `cableCapacity`, the limit of one cable, and
`enforceCableCapacity`, which turns the limit off when false. A file of the same name in a world's `serverconfig`
folder sets them for that world alone. Every bundle's limit follows `cableCapacity`.
