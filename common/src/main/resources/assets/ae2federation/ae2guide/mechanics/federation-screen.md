---
navigation:
  parent: mechanics/index.md
  title: The Federation Screen
  icon: ae2federation:router
  position: 60
---

# The Federation Screen

Right-click any Switch, Router or Bridge from within eight blocks to open its domain's Federation screen. Every block of
a domain opens the same screen, and it closes when you move more than eight blocks away. Its tabs on the left are
**Topology**, **Processing** and **Statistics**.

## Topology

The graph draws each network as a card with its name, energy, storage types, crafting CPUs and channels. A network
without a name is shown by a six-digit tag, such as "Network 082A5C"; click **Rename** on its card to give it one.

* **Click the line** between two cards to list that pair's rules on the right, with the ME power switch below them.
  Each rule shows its state: green when active, yellow while it starts, red when something blocks it, with the reason
  underneath. [Troubleshooting](../troubleshooting.md) explains each reason.
* **Scope.** The screen opens on **this domain**. The other scope button shows **with connected domains
  (read-only)**: every domain that shares a network with this one, each on a plate of its own. Edit their rules from
  their own blocks. See [Rules](rules.md).
* **Highlight network** outlines a network's blocks in the world for ten seconds, through walls and blinking. Only you
  see it, in your current dimension, and several highlights can run at once.
* **Show live flow** draws what really moved over each link in the last five seconds as moving dots. The pulses inside
  Federation Cable are decoration.
* **Federation power.** A corner gauge shows how full the domain's Beam Converters are: green while the domain runs,
  yellow while it uses more FE than comes in, red once it has stopped. A stopped domain's screen turns dark red. See
  [Power](power.md).

## Processing

The Processing tab lists the domain's Federation Pattern Providers and Processing Endpoints, and which patterns each
provider sends to which Endpoint. See [Crafting Across Networks](../crafting/index.md).

## Statistics

The Statistics tab charts the domain's networks over the last 24 hours: ME power, stored items, what moved between
them, crafting, CPUs, channels and Federation power. Its own page explains each chart.

<SubPages icons={true} />
