# 2x2 bundled cable: artist rings and one middle beam (2026-10-17)

Artist PR #4 ("添加2*2截面线缆模型") brought `large_cable_straight.bbmodel`, `large_cable_corner.bbmodel` and the
32-pixel texture `textures/block/large_cable/large_cable.png`. PR #4 is merged into dev as itself (merge `d05df6a2`,
second parent the artist's `370256b3`), with the wiring commits on top. The PR is based 193 commits behind dev and
deletes `tools/blockbench/docs/cable_design.md`; the merge keeps dev's file. The PR's doc names a `large_cable_display` project that the PR does not contain.

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

- Beam look, superseded: first an octagonal volume tube (`CableBeamMesh` `volume` flag, removed 2026-10-18); since
  branch `beam-render` the R plasma beam in `client/BundleBeamMesh` (see "Bigger middle beam" below). Ordinary cables keep the crossed
  ribbons.

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
- `tools/visual/validate_assets.py` is green again (2026-10-17): it counts textures the Java code binds by
  `"textures/....png"` path and the Router/Switch connected-face tiles as drawn, skips the 16 px rule for `misc/`
  overlays, and reads multipart blockstates (Router, Switch).

## Verification

- Unit: `BundleLookTest`, `BundleBeamTest`, `LargeCableRingsTest`; full `:neoforge-1.21.1:test` 698/699 (the one
  failure needs a built production jar, absent in a fresh worktree).
- In game: `runGuideClient -PguidePage=ae2federation:bundled-cables.md` under a private Xvfb; the page's scene is a 2x2
  run with a branch and a turn. A loaded variant was shot by hot-editing `CapacityUnits` in `bundled_cables.snbt`
  (reverted); the "before" ran from a detached worktree of dev.

## Bigger middle beam: Blender concepts (2026-10-17, branch `beam-render`)

- Owner found the 2x2 middle beam too thin ("太小家子气"). Concepts BASE (shipped tube) and Q-V live in
  `tools/blender/bundle_beams.py` (stdlib geometry + shell/ring proxy), rendered by `render_bundle_beams.py`, sheets by
  `compose_bundle_beams.py`; README section "2x2 bundle middle-beam concepts" has the commands and sentinels
  (`BUNDLE_BEAMS_OK`, `BUNDLE_MEDIA_OK`). Output: repo `build/bundle-beam-check/` (ignored).
- The proxy is rebuilt from real assets: shell voxels from `part/cable/dense/core.png` by BundleShellLayout's texel rule,
  rings from `large_cable_straight.bbmodel` with Minecraft face-UV rotation (vertex i takes UV corner (i + r/90) % 4,
  i.e. the image turns clockwise on the face). Glow renders additively (Add Transparent + Emission), like the glow pass.
- The ring bore is not 12/32 voxels: 1-voxel flanges (model x 12-13 and 19-20, and their turned copies) narrow it to
  10/32, radius .3125 block. Anything that writes depth must fit it: `SOLID_LIMIT = .30`. Additive glow may pass it;
  rings occlude it.
- Consequence: fattening the solid core alone (Q, R, T) stays tube-like; V (bulge between rings, pinched through each)
  and U (glow fills the glass) are the two routes that read as a big beam. Porting V needs the pinch to follow
  `BundleLook.ringBefore/ringAfter` (rings skip next to branches), not world-x parity; U's squircle (.84) nearly touches
  the .875 interior and needs clipping at joints; S adds 8 tubes per cell (vertex budget).
- Banding (owner noticed in `sheet-iso-full.png`): nested additive tubes of even alpha add a fixed step of light wherever
  a ray crosses one more shell, so each shell's outline is a hard edge; larger alphas at full load make it worst. The
  shipped game tube (3 nested additive tubes) has the same property. `render_bundle_beams.py --soft` fades each layer
  toward its outline and removes it; the game needs view-dependent alpha for that.
- Owner chose R (2026-10-17). `plasma_beam.py` / `render_plasma_beam.py` check it at game precision: 16 sides, 16
  samples per block, 3 soft sheaths = 4096 quads per 2x2 layer (budget argument: one beam per layer where four
  ordinary cells would each spend up to 1700). Looks the same as the 24-side approved render. Junction paths copy
  `CableBeamMesh.build` (T = straight arms; turn = elbow radius .1875 of the joint, phase faded by `mix`).
- Port notes: sheath radius depends on the angle round the tube, so `tube()` needs a per-vertex radius and `reach()`
  cannot merge sheath segments; soft edges can keep `CableBeamFrame`'s shared cache if the cache stores normals and
  the facing fade is applied per vertex at replay time. Sheaths cap their brightness at strength 1.15 and the inner
  sheath turns red when overloaded, else full load and the alarm clip to white.
- Java port (branch `beam-render`): `BundleBeamMesh` emits R in blocks from the anchor cell over the `BundleBeam` box,
  reusing `CableBeamMesh.build` centerlines scaled by the box (no PoseStack stretch any more, so pulses and lobes keep
  their world phase through 2-layer joints). Core 8 sides (straight arms merged to one segment, capped inside the
  box), 3 sheaths 16/12/0 sides at 16/8/2 samples per block (near/medium/distant), halo 8 sides. `CableBeamFrame`
  caches bundle layers with normals (`BUNDLE_STRIDE` 10); `CableFlowRenderer.emitBundle` fades glow alpha by
  `BundleBeamMesh.fade` = 2 (n.v)^2 in eye space (modelView x pose; orthographic projections, m23 == 0, look down -Z).
  In-shell fog is not drawn for the 2x2 beam (R has its halo instead). Beam Former pairs wider than 1x1 draw R too
  (owner, 2026-10-18): `BeamFormerRenderer` passes `BundleBeam(axis, through, 1)` per block step and scales the
  two-block box by side/2, so a 3x3 beam is R at 1.5x across; the `volume` tube was then unused and removed.
  In-game check: `build/beam-former-plasma-check/before-after.png`.
- Verified in a real world (dedicated server, perspective camera): the glow fades evenly above and below the beam
  from level, above and below views. In a GuideME scene the run looks paler above than below; that is the shell's
  glass faces tinting the halves differently (the old beam shows the same split), not the fade: GuideME's camera is
  orthographic (`CameraSettings.getProjectionMatrix` uses `setOrtho`), and a numeric check of the faded mesh at 30 and
  60 degrees pitch gives equal weight on both sides.
- In-world capture needs power for the cyan look: a domain without a paying Beam Converter is "unpowered" and its
  beams alarm red (`FederationDomainRegistry.unpowered`); an energy cell does not help. For screenshots, set
  `requireOperatingPower = false` in the server's `ae2federation-server.toml` (hot-reloaded) and restore it after.
