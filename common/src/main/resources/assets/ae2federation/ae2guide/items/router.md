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

<ItemImage id="ae2federation:router" scale="4" />

The Router brings ME networks into a [Federation domain](../mechanics.md). Each of its six faces works on its own:

* touched by an ME cable or device, the face joins that network, without using a channel or idle power;
* touched by <ItemLink id="ae2federation:cable" />, a Pattern Provider front or an Endpoint front, the face links
  the domain onwards.

Up to six different networks can meet at one Router, and its faces never join them into one network. Two faces on the
same network are fine and count once. Two Routers placed face to face do not connect; join them with Federation
Cable.

Right-click the Router to open the Federation screen for its domain, where you switch sharing on. See
[Getting Started](../getting-started.md).

<RecipeFor id="ae2federation:router" />

One craft makes four Routers.
