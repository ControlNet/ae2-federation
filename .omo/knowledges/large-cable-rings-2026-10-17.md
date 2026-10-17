# 2x2 bundled cable: artist rings and one middle beam (2026-10-17)

Artist PR #4 ("添加2*2截面线缆模型") brought `large_cable_straight.bbmodel`, `large_cable_corner.bbmodel` and the
32-pixel texture `textures/block/large_cable/large_cable.png`. Wired up on branch `artist-pr4-large-cable` (worktree
only, not on dev). The PR itself is based 193 commits behind dev and deletes `tools/blockbench/docs/cable_design.md`;
the cherry-pick keeps dev's file. The PR's doc names a `large_cable_display` project that the PR does not contain.

## What draws what

- Only 2x2 segments (`BundleLook.large`). Other cross-sections keep a beam and collars per cell.
- Shell: unchanged (artist: "线缆外壳暂时照旧"). Collars are dropped inside 2x2 cells; dense arms stay.
- Straight ring: at layer boundaries `floorMod(layer, BundleLook.RING_SPACING) == 0` (world coordinates, so rings stay
  put when a bundle grows), skipped next to any layer a branch leaves. Spacing 2 is a guess until the artist's display
  file arrives ("直线环的距离不可过近").
- Corner rings: on both layers a branch (or a turn, which is a branch at the end) covers, in all 8 cells of that
  2x2x2 joint. `jointsBefore/jointsAfter` carry this to the far row too.
- Beam: `BundleBeam` - the low-corner cell of each layer draws a normal cable beam scaled by the pose stack: 2 across,
  1 along; at a joint the low layer draws a 2x2x2 box (uniform scale) with the branch side in its mask, so the
  junction sits on the branch's beam. `CableFlowRenderer.getRenderBoundingBox` widens culling to that box.

- Volume beam (owner disliked the two crossed ribbons, 2026-10-17): the 2x2 middle beam draws as an octagonal
  full-bright tube that writes depth, capped where it stops inside a block, with three nested additive tubes for glow
  (`CableBeamMesh.emitLayer(..., volume = true, ...)`). Ordinary cables keep the crossed ribbons.

## Pitfalls found

- Guide scenes drew every beam over everything (pre-existing on dev): GuideME ends our fixed GLOW/CORE buffers only
  in its final `endBatch()`, after its translucent pass and after restoring the GUI matrices, so the beam had no
  valid depth. `CableFlowRenderer.render` now ends both buffers right away when the level is not a `ClientLevel`,
  before GuideME flushes its terrain; beams then sit behind rings, rails and glass as in the world. Idle beams in
  ordinary-cable guide scenes (e.g. `items/cable.md`) therefore look much fainter than before.

- Opaque ring geometry in the translucent layer was mostly invisible: the shell panels, drawn first in the same
  layer and writing depth, hid it. Rings go in `RenderType.solid()` (`CableBakedModel.LARGE_LAYERS`).
- The bbmodels use face UV rotation heavily; clipping boxes to cells would need rotated UV clipping. Instead each box
  is baked whole in the cell holding its centre (lower cell on a tie) and may reach into the next cell; vanilla
  `FaceBakery` + `BlockModelRotation` about the cell centre gives the same result as rotating the 2-block frame.
  `LargeCableRingsTest` checks every box is drawn exactly once for every orientation.
- Ring tables are generated: `python3 -I tools/blockbench/export_large_cable_rings.py` (`--check` to verify) writes
  `client/LargeCableRingModels.java`. Re-run when the artist edits the projects.
- The corner model's two rings sit at y 5..9 and 22..26 of 32: not mirror-symmetric (7 vs 24 centres). Rendered as
  modelled; ask the artist if unintended.
- `tools/visual/validate_assets.py` already fails on dev (unregistered `misc/bundle_selection_hatch`,
  `block/router/router_side_ver`, ...); the ring texture is registered (32 px allowed) but the script is not green.

## Verification

- Unit: `BundleLookTest`, `BundleBeamTest`, `LargeCableRingsTest`; full `:neoforge-1.21.1:test` 698/699 (the one
  failure needs a built production jar, absent in a fresh worktree).
- In game: `runGuideClient -PguidePage=ae2federation:bundled-cables.md` under a private Xvfb; the page's scene is a 2x2
  run with a branch and a turn. A loaded variant was shot by hot-editing `CapacityUnits` in `bundled_cables.snbt`
  (reverted); the "before" ran from a detached worktree of dev.
