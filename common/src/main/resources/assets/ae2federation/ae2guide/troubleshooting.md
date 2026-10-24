---
navigation:
  parent: index.md
  title: Troubleshooting
  icon: ae2:network_tool
  position: 600
---

# Troubleshooting

Every warning appears in the Federation screen, next to the rule, network or device it concerns. A rule's state is
green when active, yellow while it is not active yet, and red when something blocks it; the line under it gives the
reason.

## The whole domain stopped

A stopped domain's cables glow red and thin, and its topology screen turns dark red. Its networks keep working on
their own and share nothing until the cause is gone; then the domain starts again by itself.

* **"A Federation Cable in this domain carries more than its capacity, so this Federation domain has stopped."**
  Every rule reads "over capacity, stopped", and the full cable pulses red. Move a network or device off that
  segment, end it with a Router, or build it as a bundle; see [Network Capacity](mechanics/capacity.md).

## Connecting

* **"Connect a network to the bridge outer side."** Nothing with an ME node touches the Bridge's outer side. Place
  a cable or device of the second network there.
* **"Both sides connect to the same ME network."** The Bridge's two sides are already one network, for example
  through another ME cable. Federation only connects separate networks.
* **"The outer attachment does not support a Federation connection."** The Bridge's outer side touches a block with
  no ME node, such as Federation Cable. Bridges join ME networks directly; use a Switch for Federation Cable.
* **A Switch face does nothing.** A face counts as an ME face when an ME cable or device touches it, and as a
  Federation face when Federation Cable, a Router, another Switch, a Pattern Provider front or an Endpoint front
  touches it. Any other block leaves the face unused.
* **A Router does not join my network.** Routers attach no ME networks; put a Switch there instead.
* **"This ME Federation Router is not formed and links nothing: ..."** Router (or Switch) blocks that touch each other must fill a
  solid box with no edge longer than 8 blocks, and no block of the same type may touch the box from outside. Until
  then all of their faces stay disconnected. Complete the box or take the extra blocks away; see
  <ItemLink id="ae2federation:router" />.
* **"No domain with two networks."** The block you opened does not reach two networks yet. Connect a Switch or
  Bridge, then open it again.

## Rules

* **"Not in effect yet: the server has not started using this rule."** Wait a moment; if it stays, check that both
  networks are loaded and powered, by power of their own or through ME power.
* **"One of the two networks is not loaded, or its identity is not settled yet."** Load the other network's chunks,
  or wait until it has finished starting.
* **"The other network has no storage it can share."** Add storage to the other network.
* **"No Federation node on one of the two networks can join the shared energy pool."** Shared ME power needs a
  Switch face or a Bridge on each network.
* **"Too many storage links in this world to work out."** Remove some storage rules or links.
* **"Merge pending": two networks with different settings were joined.** Names and cross-network rules pause,
  because neither side is picked automatically. Select one of them in the topology and press **Keep this one**: the
  other's name and rules are deleted. Or disconnect them. A network with no name, no rules and no cross-network
  crafting results still to come back is simply taken into the other one when joined. So are networks with exactly
  the same name and rules, unless either still has such results to come back.
* **"Split pending": a network was cut in two.** Both halves pause cross-network rules. Reconnect them, or remove one
  half, to recover.

## Processing

* **"This Provider's front is not on a Federation Cable, Switch or Router."** Turn the Provider (wrench) or replace it so its
  front touches the Federation side.
* **"... belongs to Provider ... Release it there first."** Another Provider owns this Endpoint. Release it there.
* **"... is in local mode and cannot take federated patterns."** A native pattern provider sits against the
  Endpoint's front. Remove it to use the Endpoint for Federation.
* **"Target is on the source network."** The Endpoint's subnet is the Provider's own network. Give the machines their
  own subnet.
* **"Target networks overlap."** Two Endpoints of one Provider share a subnet. Give each its own subnet.
* **"Nothing is connected behind it."** The Endpoint's other five faces touch no ME device, so its Provider has
  nowhere to put inputs and its node on the topology turns yellow. Connect them to the machines' ME network.
* **"Cannot release yet."** Results are still in the Endpoint's return buffer; they enter the Provider's network as
  soon as it has room.
