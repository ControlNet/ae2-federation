---
navigation:
  parent: index.md
  title: How Federation Works
  icon: ae2federation:cable
  position: 20
---

# How Federation Works

Federation connects ME networks and leaves each one whole. Every network keeps its own
[channels](ae2:ae2-mechanics/channels.md), controllers, storage and crafting CPUs, and shares only what a
[rule](rules.md) allows. A Switch face or a Bridge joins the network it touches as a node that uses no channel and
draws no idle power. Federation Cable belongs to the Federation alone and carries no ME channels.

## Federation domains

Federation blocks joined by Federation Cable form one **Federation domain**, together with every ME network they touch.
Networks next to the same Switch share its domain without any cable. A Bridge forms a small domain of its own, with
exactly the two networks it joins.

A domain keeps one set of settings. Right-clicking any of its Switches, Routers or Bridges opens the same
[Federation screen](federation-screen.md).

## What a domain needs

* **FE** for its Switches, Routers, cables and devices, from a Beam Converter. A domain of Bridges alone uses none.
  See [Power](power.md).
* **Room on its cables.** Each Federation Cable carries a limited number of networks and devices; Routers and
  [bundles](bundled-cables.md) make room. See [Capacity](capacity.md).

A domain short of either **stops**: its networks keep working on their own and share nothing until it recovers.

## Limits

* Federation blocks link within one dimension. A pair of Quantum Cubes, or Federation P2P tunnels over AE2's Quantum
  Network Bridge, reach into another; see [Long-Distance Links](long-distance.md).
* Every part of a domain must be in loaded chunks. Federation loads no chunks itself.
* Federation Cable has no length limit, and one domain holds up to 4096 Federation blocks.
* Rules carry no player or team permissions: anyone who can open a Switch, Router or Bridge can change its domain's
  rules.

## In this section

<SubPages icons={true} />
