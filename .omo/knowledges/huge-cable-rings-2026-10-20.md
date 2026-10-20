# 3x3 bundled cable: artist rings and one middle beam (2026-10-20)

Artist PR #5 ("添加3*3线缆模型") brought `huge_cable_straight.bbmodel`, `huge_cable_corner.bbmodel`, new regions in the
shared 32-pixel `textures/block/large_cable/large_cable.png`, and a fix to `large_cable_corner.bbmodel` (upper ring one
voxel down, so both 2x2 corner rings now sit at y 5..9 of their own cell). Merged into dev as itself (the PR head is
the merge's second parent), like PR #4 (see `large-cable-rings-2026-10-17.md`), with the wiring on top.

## How the art becomes geometry

- Same pipeline as the 2x2: `python3 -I tools/blockbench/export_large_cable_rings.py` (`--check` to verify) writes
  `client/LargeCableRingModels.java` with `STRAIGHT`/`CORNER` (frame 2 cells) and `HUGE_STRAIGHT`/`HUGE_CORNER` (frame
  3 cells). Nothing runs `--check` automatically: re-run it whenever the artist edits a project.
- The 3x3 projects are drawn about Blockbench's origin (8, 0, 8), which is the ring's axis, not inside 0..48. The
  exporter moves them by `HUGE_OFFSET` (16, 24, 16) so that point is the centre of the frame's middle cell. Checked by
  symmetry: the straight ring's unturned boxes span 10..38 on every axis (sum 48), the corner's two rings centre on
  y 16 and 32, the middle layer's boundaries (`LargeCableRingsTest`).
- Unlike the 2x2 projects, the 3x3 ones use element rotations (connectors at +-22.5 degrees, outer bars at 45, about
  the ring axis through (8, 0, 8)). The exporter keeps them as `Turn(axis, angle, origin)`; `LargeCableRings` bakes
  them with vanilla `BlockElementRotation` (origin in blocks, `rescale` false) plus the `BlockModelRotation` that turns
  the model onto the bundle's axis. A box is drawn by the cell holding its centre after the turn
  (`LargeCableRings.home`, lower cell on a tie), shifted with its turn origin into that cell's coordinates.
- The 3x3 bore is 8 voxels (radius 1/4 block) round the middle cell's axis; the 2x2 bore is 10/32 block.

## Where rings and the beam go (`BundleLook.side` 2 or 3, `thick()`)

- 3x3 straight ring: inside the layer (model z 5..11), every `BundleLook.HUGE_RING_SPACING` = 3 layers in world
  coordinates (`floorMod(layer, 3) == 0`), not in a layer a branch leaves (`ringInside`, rings bit 2). The spacing is a
  guess, like the 2x2's 2.
- 3x3 corner rings: a 3x3 branch covers three layers of the run; `jointBefore/After` already say which one (after only
  = first, both = middle, before only = last), which picks the cell along the run in the corner frame.
- One middle beam, as for 2x2 (`BundleBeam.side`): box `side` wide, 3 layers long at a joint (the far layer, looked up
  by `IntFunction` offset, decides whether the run goes on). Collars are dropped in 3x3 cells as in 2x2. Before this, a
  3x3 bundle drew nine per-cell beams with collars, which would cut through the ring's outer bars.
- Beam radii (owner found the first 3x3 beam, with the 2x2's radii, too thin): `BundleBeamMesh` scales a 3x3 beam's
  glow (all three sheaths and the halo; sheath 0 is 1.6 x the unscaled pulse x the glow scale) by `HUGE_GLOW_SCALE`
  1.5, as its box is 1.5 x the 2x2 one, and its depth-writing core only by `HUGE_CORE_SCALE` 4/3: the core's widest is
  (.10 + .05 u) x breath, .165 at full load in an alarm (breath 1.1), so .22 block, inside the 1/4-block bore. Halo
  .44 -> .66 x breath; sheaths 1 and 2 .22-.26 -> .33-.39 and .31-.33 -> .465-.495 before lobes. Same vertex counts.
  2x2 and 1x1 output is bit-identical (`BundleBeamMeshTest` fingerprints every 2x2 vertex as of 3d2ff190).
- In a guide scene the beam animates (the scene's level ticks), so an animated preview can be grabbed with
  `ffmpeg -f x11grab -framerate 10 -t 3.2` (32 frames = one 64-tick cycle). A loaded look needs `CapacityUnits` below
  `Capacity` in the scene's block entities (above it is an overload alarm).

## Not taken from the PR

- `textures/block/router/switch.png` (new path, nothing references it; differs from the live `block/switch/switch`
  frame). It fails `validate_assets.py` as an unused texture. Left out pending the owner's decision; it is still in the
  artist's commit `e22257e2`.

## Verification recipe

- Unit: `LargeCableRingsTest`, `BundleLookTest`, `BundleBeamTest`, `BundleBeamMeshTest`.
- In game: scratch guide page + snbt scenes (3x3 run, branch, turn; `BundleMin/Max` packed as in
  `bundled-cable-rendering-2026-10-13.md`), `runGuideClient -PguidePage=...` on a private Xvfb, hot-editing the page's
  `ImportStructure`/camera between shots and `import -window root -crop 1284x880+300+40`. "Before" ran from a detached
  worktree of the pre-merge commit. Images: `build/cable-3x3-art-check/`. Delete the scratch page afterwards.
