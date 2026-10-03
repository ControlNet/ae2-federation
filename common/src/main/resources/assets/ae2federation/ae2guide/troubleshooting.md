---
navigation:
  parent: index.md
  title: Troubleshooting
  icon: ae2:network_tool
  position: 40
---

# Troubleshooting

Every warning appears in the Federation screen, next to the rule, network or device it concerns. A rule's state is
green when active, yellow while it is not active yet, and red when something blocks it; the line under it gives the
reason.

## Connecting

* **"Connect a network to the bridge outer side."** Nothing with an ME node touches the Bridge's outer side. Place
  a cable or device of the second network there.
* **"Both sides connect to the same ME network."** The Bridge's two sides are already one network, for example
  through another ME cable. Federation only connects separate networks.
* **"The outer attachment does not support a Federation connection."** The Bridge's outer side touches a block with
  no ME node, such as Federation Cable. Bridges join ME networks directly; use a Router for Federation Cable.
* **A Router face does nothing.** A face counts as an ME face when an ME cable or device touches it, and as a
  Federation face when Federation Cable, a Pattern Provider front or an Endpoint front touches it. Two Routers placed
  face to face do not connect; put Federation Cable between them.
* **"No domain with two networks."** The block you opened does not reach two networks yet. Connect a Router or
  Bridge, then open it again.

## Rules

* **"Not in effect yet: the server has not started using this rule."** Wait a moment; if it stays, check that both
  networks are loaded and powered.
* **"One of the two networks is not loaded, or its identity is not settled yet."** Load the other network's chunks,
  or wait until it has finished starting.
* **"This direction's Storage rule is off."** Crafting takes the other network's materials through storage; switch
  that direction's Storage on.
* **"The other network has no storage it can share."** Add storage to the other network.
* **"No Federation node on one of the two networks can join the shared energy pool."** Shared ME power needs a
  Router face or a Bridge on each network.
* **"Too many storage links in this world to work out."** Remove some storage rules or links.
* **"Merge pending": two established networks were joined.** Names and cross-network rules pause, because neither
  side is picked automatically. Disconnect them to recover.
* **"Split pending": a network was cut in two.** Both halves pause cross-network rules. Reconnect them, or remove one
  half, to recover.

## Processing

* **"This Provider's front is not on a Federation Cable or Router."** Turn the Provider (wrench) or replace it so its
  front touches the Federation side.
* **"... belongs to Provider ... Release it there first."** Another Provider owns this Endpoint. Release it there.
* **"... is in local mode and cannot take federated patterns."** A native pattern provider sits against the
  Endpoint's front. Remove it to use the Endpoint for Federation.
* **"Target is on the source network."** The Endpoint's subnet is the Provider's own network. Give the machines their
  own subnet.
* **"Target networks overlap."** Two Endpoints of one Provider share a subnet. Give each its own subnet.
* **"Cannot release yet."** Results are still in the Endpoint's return buffer; they enter the Provider's network as
  soon as it has room.
