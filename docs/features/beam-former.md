# ME Federation Beam Former

Status: implemented on `dev` (2026-10-17). Design discussion:
[line-of-sight beam links](../ideas/line-of-sight-beam-links.md); plan and decisions D1-D12:
`.omo/plans/beam-former.md`. The textures are temporary (the converter's placeholder, recoloured cyan) until the
artist delivers.

## Behavior

- **Block.** `ae2federation:beam_former` 「ME联邦光束成型器」, a solid-cuboid device like the converter
  (`FormableDevice`). A body is a square of 1x1, 2x2 or 3x3, one block thick (`BeamFormerShape`); other shapes do
  not form. Placement faces where the player looks. Forming turns every member along the body's thin axis (a 1x1 has
  all three): the facing most members vote for, else away from the side touching Federation cable, else positive. A
  wrench flips a 2x2 or 3x3 front to back and turns a 1x1 to the clicked face. Recipe `CPC / CWC / CLC` (Federation
  Cable, fluix pearl, wireless receiver, Nexus Core), after ME Beam Former's block.
- **Ports.** Each block is a node of kind `BEAM_FORMER` with the square's capacity factor. It links through its back
  (like a Router face) and toward the rest of its body; the front has no port. A bundle of the same square at the back
  joins it (`BundleContacts`).
- **Pairing.** `BeamLinkService`, one per level, on the server thread, before `OperatingPowerService.tick`. A body's
  lowest block reports `(min, max, facing)`. `BeamPartnerIndex` pairs a former with the nearest body on the same
  line (same axis, same square across it) in front of it; they link only if it faces back and the gap between the
  fronts is within `beam.maxRange` (default 256, 1 to 1024, `config/ae2federation-server.toml`). Fronts touching
  (gap 0) link at once.
- **Obstruction.** `BeamObstruction`: air, `c:glass_blocks`, `c:glass_panes` and the tag `ae2federation:beam_passes`
  (AE2 quartz and vibrant quartz glass) pass; any other block with a non-empty collision shape blocks, so water passes.
  The service sweeps each beam's volume (`BeamVolume`) within 1024 blocks per beam and 16384 per tick, reading only
  loaded chunks (`getChunkNow`) and skipping all-air sections. A `NeighborNotifyEvent` inside a volume is checked
  first, and a known blocker is rechecked before the sweep. An unloaded position keeps its last result.
- **Debounce.** `BeamDebounce`: a pair links after the path has stayed clear for 20 ticks and unlinks after a blocker
  has stayed for 20 ticks; a new pair links on its first clear sweep.
- **Link.** While linked, each anchor (lowest block) names a mirrored remote port, `"beam:" + partner` to
  `(partner, "beam:" + self)`, as a P2P tunnel does, so both ends' domains merge. An end that unloads withdraws its
  nodes and the link drops.
- **Capacity.** A pair is passable for counting and carries its region's demand, so it is judged against its square's
  limit: 1x1 C, 2x2 6C, 3x3 unlimited ([cable capacity](cable-capacity.md)).
- **Power.** The blocks cost 0; the domain pays each linked pair once, on the lower anchor: M·N × max(2, L)^1.05 AE/t
  (×2 FE, × the multiplier), L being the blocks between the fronts (`OperatingPowerCosts.beamLink`). A linked pair's
  square counts with the cables' when the domain's converters are matched; the statistics page groups the cost as
  "Beam Former links" ([operating power](operating-power.md)).
- **Display.** Jade shows one status line (linked and length, blocked at a position, checking, no partner within
  range, another size ahead) and the capacity count. The positive-facing anchor draws the beam one block at a time
  (`BeamFormerRenderer`): a 1x1 pair with the cable's light, a 2x2 or 3x3 pair with the 2x2 bundle's plasma beam
  (`BundleBeamMesh`, Blender study R; owner, 2026-10-18), its two-block box scaled to the square; load, over-capacity
  red and unpowered red follow the cable's rules. The anchor syncs a `BeamLook` (partner, side, count, limit, unpowered) every 10 ticks.

## Evidence

- Unit tests: `BeamFormerShapeTest`, `BeamVolumeTest`, `BeamPartnerIndexTest`, `BeamDebounceTest`,
  `CableCapacityCounterTest.aBeamFormerPairCountsAsCable`, `OperatingPowerCostsTest`, `SurvivalRecipeContractTest`.
- GameTests (manifest `beam.*`): `former-shapes`, `former-links-through-its-back`, `former-takes-its-bundle`,
  `former-turns-with-a-wrench`, `pair-links-and-debounces`, `passes-glass-and-water`,
  `pairs-the-nearest-of-its-size`, `pair-needs-its-size-of-converter`.
- Benchmark `perfbeamscan` (manual): 35.2 ns per glass block, so the per-tick cap costs at most about 0.58 ms
  ([notes](../../.omo/knowledges/beam-scan-cost-2026-10-17.md)).
- Not covered by a GameTest: the range limit (a server-wide rule; `BeamPartnerIndexTest` covers it) and an unloaded
  middle chunk.
- In game (dedicated server, 2026-10-17): a 3x3 and a 1x1 pair ten blocks apart, before and after the renderer,
  unpowered and with the rule off.

## In-game guide

The [English page](../../common/src/main/resources/assets/ae2federation/ae2guide/items/beam_former.md) and
[Simplified Chinese page](../../common/src/main/resources/assets/ae2federation/ae2guide/_zh_cn/items/beam_former.md),
with a hand-written scene; the Beam Converter and Network Capacity pages link to it.
