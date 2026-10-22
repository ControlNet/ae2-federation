---
navigation:
  parent: index.md
  title: Power
  icon: ae2federation:beam_converter
  position: 230
---

# Power

Federation deals with two kinds of energy. **ME power** is a rule: it lets networks share their AE energy. **FE** is
what a Federation domain itself runs on, supplied by a [Beam Converter](../items/beam_converter.md). The two never
mix: AE energy cannot run a domain, and a converter's FE never reaches an ME network.

## ME power: sharing AE energy

Switch on the ME power rule for two networks, and their energy joins one pool that flows both ways, as over a
<ItemLink id="ae2:quartz_fiber" />. A network without an energy source of its own can then run on the other's. Pools
join up: if A shares with B and B with C, all three share one pool. Each network needs a Switch face or a Bridge for
this; a network that reaches the domain only through a Pattern Provider's front cannot join the pool. See
[Rules](rules.md).

## FE: what a domain runs on

Every Federation block in a domain uses FE every tick, and the domain draws it from its Beam Converters.

| Block | FE per tick, each block |
|---|---|
| <ItemLink id="ae2federation:cable" /> | 0.5 |
| <ItemLink id="ae2federation:switch" />, <ItemLink id="ae2federation:router" /> | 2 |
| <ItemLink id="ae2federation:pattern_provider" />, <ItemLink id="ae2federation:processing_endpoint" /> | 2 |
| <ItemLink id="ae2federation:quantum_cube" />, formed | 44 |
| <ItemLink id="ae2federation:bridge" />, <ItemLink id="ae2federation:federation_p2p_tunnel" />, <ItemLink id="ae2federation:beam_converter" />, <ItemLink id="ae2federation:beam_former" /> | 0 |

* A bigger Switch or Router uses 2 for each of its blocks, and each cable in a [bundle](bundled-cables.md) uses 0.5.
* A linked pair of Beam Formers adds 2 × (blocks in its square) × L^1.05 FE, L being the blocks between the fronts
  (at least 2): a single pair 16 blocks apart uses 37 FE per tick.
* A domain of Bridges alone uses nothing, so it runs without a converter.

The Federation screen shows the domain's use and how full its converters are, and its
[Statistics](statistics.md) tab charts both over time.

## When the FE runs out

A domain whose converters run dry **stops**. Its networks keep working on their own, but share no storage, crafting,
processing or ME power. Its cables glow red and thin, and its topology screen turns dark red. The domain keeps drawing
whatever FE comes in, and runs again once it has had enough for more than 30 ticks in a row, as an AE2 network
restarts after a power loss.

## Where the FE comes from

Vanilla Minecraft and AE2 produce no FE. A domain of Switches, Routers or Federation Cable therefore needs one of:

* **Another mod's generator**, piped into a Beam Converter. Any mod's FE cables or generators work.
* A **Creative ME Federation Beam Converter**, which never runs out. It is a creative-mode block.
* The server option `requireOperatingPower = false`, in the `[power]` section of `config/ae2federation-server.toml`
  (or of a file of that name in a world's `serverconfig` folder, for that world alone). Every domain then runs
  without converters. `operatingCostMultiplier` in the same section scales every cost in the table above.

The converter's size has to match the largest cable in its domain; its page has the sizes, the buffer and the recipe.
