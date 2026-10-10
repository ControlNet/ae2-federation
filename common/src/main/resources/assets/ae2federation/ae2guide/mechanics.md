---
navigation:
  parent: index.md
  title: How Federation Works
  icon: ae2federation:cable
  position: 20
---

# How Federation Works

## Networks stay independent

A Federation connection never merges two ME networks. Each network keeps its own [channels](ae2:ae2-mechanics/channels.md),
controllers, storage and crafting CPUs. Switch faces and Bridges join the network they touch as a node, but they use
no channel, carry no channels and draw no idle power. Federation Cable is not part of any ME network at all.

## Federation domains

All networks that touch the same Switches, or the Switches, Routers, Pattern Providers and Endpoints linked by one stretch of
Federation Cable, form one **Federation domain**. A Bridge forms a small domain of its own with exactly the two
networks it joins. Right-clicking any Switch, Router or Bridge of a domain opens the same screen; no block holds a private
copy of the settings.

## Rules

Rules belong to a pair of networks and to a direction: "A uses B's storage" is a different rule from "B uses A's
storage". A rule set in one domain also applies wherever else the same two networks meet. Nothing is shared until
you switch a rule on.

| Rule | What it allows | States |
|---|---|---|
| Storage | See, insert and extract the other network's items, fluids and other resources | Disabled, Enabled, Enabled with re-export |
| Crafting | Use the other network's pattern providers from your own terminals and automation | Disabled, Enabled, Enabled with re-export |
| ME power | Join both networks' energy into one pool | Disabled, Enabled |

* **Crafting and Storage are separate switches.** Your own crafting CPU runs the job and pays with the materials your
  network can see; the other network needs no CPU, and the result arrives in yours. Crafting alone is enough. Switch
  on the same direction's Storage as well only if the CPU should also use the other network's materials, or you want
  to reach the leftovers of jobs that stay there. See [Remote Crafting](remote-processing.md).
* **Re-export** (the third state) passes access along a chain: if A uses B's storage and B uses C's storage with Re-export, A can reach
  C's storage through B. Crafting works the same way, and two networks may each craft with the other's providers.
  The chain may cross from one domain to the next, for example over two Bridges; see [Across Domains](across-domains.md).
* **ME power** has one switch for the pair, and energy flows both ways, as with a <ItemLink id="ae2:quartz_fiber" />.
  Pools join up: if A shares with B and B with C, all three share one pool. Each network needs a Switch face or a
  Bridge for this; a network that reaches the domain only through a Pattern Provider front cannot join the pool.
* **Processing needs no rule.** Any Federation Pattern Provider can use any Processing Endpoint in its domain; see
  [Remote Crafting](remote-processing.md).

## The Federation screen

* The graph shows each network as a card with its energy, storage types, crafting CPUs, channels and name.
* **Highlight 10 s** outlines a network's blocks in the world for ten seconds, through walls, blinking so it is easy
  to spot. Only you see it, and only in your current dimension. Several highlights can run at once.
* **Live flow** shows what really moved over each link in the last five seconds as moving dots. The pulses inside
  Federation Cable are decoration, not measured traffic.
* The screen closes when you move more than eight blocks away.

## Limits

* Everything in a domain must be in the same dimension and in loaded chunks. Federation does not load chunks.
* Federation Cable has no length limit, but one domain can hold at most 4096 Federation blocks.
* There are no player or team permissions: anyone who can open a Switch, Router or Bridge can change its domain's rules.
