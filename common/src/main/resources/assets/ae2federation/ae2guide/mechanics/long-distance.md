---
navigation:
  parent: mechanics/index.md
  title: Long-Distance Links
  icon: ae2federation:beam_former
  position: 250
---

# Long-Distance Links

Four things carry a Federation domain from one place to another. Each acts as if Federation Cable ran between its
ends, so the blocks on both sides share one domain, its rules and its [capacity](capacity.md).

| Link | Reach | Carries | FE per tick |
|---|---|---|---|
| <ItemLink id="ae2federation:cable" /> | Any length, laid block by block | One cable's limit; a [bundle](bundled-cables.md) more | 0.5 per cable |
| <ItemLink id="ae2federation:federation_p2p_tunnel" /> | Anywhere an ME network reaches, other dimensions included over AE2's Quantum Network Bridge | One cable's limit | 0 |
| <ItemLink id="ae2federation:beam_former" /> pair | A straight, clear line of up to 256 blocks; the server can allow 1024 | By its square, like a bundle | 2 × square × L^1.05 |
| <ItemLink id="ae2federation:quantum_cube" /> pair | Any distance, any dimension | By its face centre, like a bundle | 44 per block of both cubes |

The FE column is the domain's own [operating power](power.md). A linked Beam Former or Quantum Cube pair also counts
as cable of its square when the domain's Beam Converters are sized.

## Choosing one

* **Federation Cable** is the cheapest and the simplest. Lay it wherever you can walk.
* **P2P tunnels** reuse an ME network that already spans the distance. That network pays AE2's usual power and a
  channel for each tunnel, and only carries the link: it does not join the domain. See
  [An Outpost in the Nether](../examples/nether-outpost.md).
* **Beam Formers** cross open ground, ravines and water with nothing laid in between. The beam passes glass and water
  and stops at solid blocks. Its cost grows with its length: a single pair 64 blocks apart uses 158 FE per tick.
* **Quantum Cubes** link any two places, in one dimension or two. They cost the most: a 3×3×3 pair uses 2,376 FE per
  tick, linked or not.

A Beam Former or Quantum Cube link holds while both of its ends are loaded, whatever lies between them. A cable run,
and the ME network under P2P tunnels, must be loaded along their whole length.
