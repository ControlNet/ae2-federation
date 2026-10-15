# Cable capacity

Status: counting, the limit, the Jade line, the beam's load light and bundled cables are implemented on `dev`
(2026-10-13). The counting and limit contract is [Cable capacity counting](../architecture/cable-capacity-counting.md).
The Jade display idea is preserved as an [archived discussion](../archive/ideas/jade-cable-capacity.md); the open
balancing questions stay in the [multiblock and cable capacity idea](../ideas/multiblock-routing-and-cable-capacity.md).

## Behavior

- **Counting.** Every Federation Cable counts the ME networks on Switches, Processing Endpoints and Federation Pattern
  Providers around it. Cable joined to cable, through Switches and Federation P2P tunnel pairs, forms one segment;
  only a Router (a formed body counts once) ends one. A segment touching at most one Router counts its own
  **subdomain**; a segment touching two or more counts the whole Federation domain.
- **Limit.** One cable carries 8 by default (`capacity.cableCapacity` in `config/ae2federation-server.toml`, 1 to
  1024; enforcement is `capacity.enforceCableCapacity`, on by default). When any cable is over, the whole domain
  stops sharing until the count fits again; the world is not changed.
- **Display.** The beam inside each cable grows with its load and turns red only while it is over an enforced limit
  ([the M renderer](../art/cable-m-renderer.md)). The topology screen turns the stopped domain dark red. With Jade on
  both sides, a cable reads "Capacity count: N/C (subdomain)" or "(Federation domain)", N red when over.
- **Bundled cables.** A wrench turns a box of ordinary cables (cross-section 1x2 up to 3x3, length up to 64) into one
  bundled cable with one shell and one beam per cell. Its limit is a multiple of one cable's: 1x2 3C, 1x3 4C, 2x2 6C,
  2x3 9C, 3x3 unlimited. A bundle connects only to a Router or Switch face covering its end or the full width of its
  side, never to ordinary cable. It is extended, branched and turned with cables, and split by breaking a block.

## Evidence

- Unit and GameTests named in the [counting contract](../architecture/cable-capacity-counting.md), `CableBeamTest`
  and `CableBeamFrameTest`.
- In-game comparisons: `.omo/knowledges/cable-capacity-counting-2026-10-13.md`,
  `cable-capacity-limit-2026-10-13.md`, `bundled-cable-rendering-2026-10-13.md` and
  `cable-beam-performance-2026-10-13.md`.
- Dedicated server with a remote client (2026-10-14): the bundle read "4/48 (Federation domain)" and an ordinary
  segment "3/8 (subdomain)", live, after a chunk reload and after a server restart
  ([notes](../../.omo/knowledges/dedicated-server-sync-2026-10-14.md)).
- Shader packs (2026-10-14): under Iris with Complementary Reimagined, BSL and MakeUp the beams keep their shape,
  colour and load, with the packs' bloom on top ([notes](../../.omo/knowledges/iris-shader-compatibility-2026-10-14.md)).
- Guide: Network Capacity and Bundled Cables pages.
