# Bundled cable rendering and guide scenes (2026-10-13)

Commits on `dev`: 37501703 (model), ce046942 (in the world), 9bbb1a53 (form/extend/dismantle), 3a5d2cf6 (one shell,
a beam per cell), 1a34a908 (rails open at joints), then the guide page `bundled-cables.md`.

## What a cell knows: `BundleLook`

- Per cell, 6-bit masks indexed by `Direction.get3DDataValue`: `edges` (segment boundary on that side), `joins` (a
  linked segment there, via static `BundleSet.linked(pos, seg, nbr, seg2)`), `dense` (a whole Router/Switch contact),
  and `joinsBefore`/`joinsAfter` (an opening on an across side continues into the previous/next cell along the axis).
- `BundleLook.of(BlockGetter, pos)` reads segments through `BundleView.segmentAt`: a `ServerLevel` asks the
  registry (`BundledCables`), anything else reads the block entity's `bundleSegment` (synced as `BundleMin`,
  `BundleMax` as `BlockPos.asLong`, `BundleAxis`). Linking is pure geometry, so it works in any `BlockGetter`.
- An ordinary cable's kind toward a bundled neighbour is NONE; a bundle never links to an ordinary cable.

## Shell geometry (`client/BundleShellLayout`, `client/BundleCableBuilder`)

- Across axes are inset 2/16 at outer edges; along the axis the shell runs 0..16 where open and 2..14 where closed.
- Panels: end caps, side panels, and at a join the strips outside the hole plus stub walls for the branch's walls in the
  2-voxel gap. Hole range along the axis: `[joinsBefore ? 0 : 2, joinsAfter ? 16 : 14]`.
- Texture: each voxel gets a texel from `core.png` by Chebyshev distance to the closed edges: straight rail k ->
  (2+k, 6); where two rails meet, the frame corner (2+du, 2+dv); glass (6, 6). Openings remove rails where a branch or
  turn joins, so the rail turns into the branch wall (an L) instead of crossing the joint. Voxels are merged greedily
  into rectangles; each quad samples its texel's centre so mipmaps never bleed.
- Quads: NeoForge `QuadBakingVertexConsumer`, counter-clockwise seen from outside (translucent culls), shade and AO on.
- Concave corners use the straight-rail colours, not the artist's `core_connected_4` corner, to avoid shade seams.
- End caps (2026-10-18): a branch carrying the cap on opens that edge only between the branch's walls, i.e. 2..14 at
  the cross-section's ends (`Opening` bounded like `joints`), not the whole edge. The rail end left outside the cap is
  what `texel`'s Chebyshev `beyond` turns into an L; with unbounded openings, two branches leaving one end layer (a
  2x2 cube with arms toward three sides, run = cube + one arm) left the cap's concave corner as glass. Only cap cells
  had it; side panels were right. `BundleShellLayoutTest` checks every concave corner of that shape in all 48 axis
  orientations and all 3 choices of run. In game: `build/bundle-bend-corner-check/`; segments were added offline to
  `hero-world/data/ae2federation_bundled_cables.dat` (gzip NBT `data.segments[{min, max, axis}]`) with the server
  stopped, which forms bundles without wrench clicks.

## Device arms (2026-10-27)

- An ordinary bundle (e.g. 2x3) gives each cell facing a whole device contact the 1x1 cable's dense arm, one per beam.
- A 2x2 or 3x3 bundle is one thick cable, so its cells share one arm across the whole contact
  (`CableShapes.denseArm`): 1 voxel inside the shell's outer edges (3..13 where the arm ends, 0/16 where it runs on into
  the next cell), 2 voxels deep. A side contact is one layer, so there the arm keeps 3..13 along the bundle. Before
  this, a thick end against a Router showed a grid of small 1x1 arms, which looked like thin-cable connectors.
- The arm is drawn as tiles (`BundleShellLayout.armFaces`) that sample single texels of `dense/line.png`: rim columns
  3, 4 (and 11, 12) only where the arm ends, glass column 5 elsewhere; row 14 next to the shell, 15 next to the device.
  Stretching the 1x1 arm's position-based UVs over a wide arm sampled the texture's transparent corners (columns 0, 1,
  14, 15 of rows 14, 15) and left holes.
- In game: `build/bundle-device-arm-check/` (scene: 2x2 and 3x3 bundles ending on Routers, rendered through the guide
  client).
- 2026-10-30: the one beam of a thick bundle also turns toward a side contact. `BundleLook.contacts` holds, for every
  cell of a layer, the outer sides whose whole row at that layer lies against one device (a side contact belongs to
  the face cells, not to the corner cell that draws the beam). `BundleBeam` adds them to the beam's mask, over every
  layer of a branch's box too. The gallery's `bundled-cable.webp` was re-rendered with one arm per end
  (`tools/blender/gallery.py#thick_arm_quads`). Checks: `build/design-review-check/`.
- 2026-10-30 (owner chose option B): where one device body takes the same side's rows of several adjacent layers, the
  side arm runs on across those layers as one arm (`BundleLook.armsBefore/armsAfter`, from
  `BundleLook.Contacts#oneBody`, which reads the device's connection bit toward the next layer). It keeps the rim only
  at the stretch's two ends. Two devices side by side still get an arm each. Option A (an arm per layer, 6 voxels apart)
  was rejected.
- 2026-10-30 (owner approved): the beam turns once per such stretch too. `BundleLook.contactsBefore/contactsAfter`
  (layer-wide, from `armRunsOn` on the face cell) link the layers; `BundleBeam.plain` walks the stretch and only its
  middle turns: the middle layer for an odd stretch, or a 2-layer box over the two middle layers for an even one, which
  turns at their boundary (the second of them draws none). Stretches stop at branch layers, whose joint box turns on
  its own. Boxes stay at most 3 layers, so `BundleBeamMesh` is unchanged. Walking the stretch reads up to its length
  in extra looks, only for corner cells of layers with a side contact. Check: `build/design-review-check/round5/`.
- Cost (not measured in game yet): `refreshFlow` rebuilds `BundleLook.of` each tick for a thick bundle's corner cells,
  and the layer contacts add `whole()` row reads there. A face cell whose own outer side has no branch reuses its own
  `dense` bit instead of reading again, which leaves about two extra row reads per corner cell per tick. Compare with
  the 2028-cell refresh figure in `bundle-render-performance-2026-10-20.md` if bundle refresh shows up in a profile.
- Review renders from any camera: `tools/visual/guide_views.py OUT VIEWS.json --extra DIR` (GuideME client on Xvfb,
  extra SNBT structures copied into a scratch copy of the guide).

## Guide scenes with bundles

- A GuideME scene can show a formed bundle: give every cell a block-entity `nbt` with `id: "ae2federation:cable"`,
  `Capacity` (must be present, `loadAdditional` returns early without it), `CapacityUnits`, `CapacityEnforced`, and
  `BundleMin`/`BundleMax` as `BlockPos.asLong` longs (`x<<38 | z<<12 | y`, 26/26/12 bits) of the segment in scene
  coordinates, plus `BundleAxis`. One entry per segment; branches are separate segments.
- A formed Router needs its full state: `ae2federation:router{down,east,north,south,up,west,formed:true}` with each
  connection true toward a neighbour of the same cuboid. Dense arms then appear without a server.
- `assets/bundled_cables.snbt` was generated once by a scratch script; edit it consistently (state + both longs).

## Capture pitfalls

- In-world capture with real wrench clicks: wait ~2.2 s after each `tp` before the click. A click fired before the
  teleport settled formed a wrong 1-layer bundle and every later selection failed in cascade, which looked like a
  rendering bug. Check ground truth with `execute unless block ... run say MISSING` and Jade from directly above.
- Guide shots: `Xvfb :91 -screen 0 1600x960x24 +extension GLX`, `runGuideClient -PguidePage=...`. There is no
  xdotool; scroll with an XTEST script (Xlib `fake_input` of buttons 4/5 after a `MotionNotify`) run via pixi. Pages
  hot-reload, so a "before" shot can be taken by editing the page briefly while the client runs.
- `pixi run ... python -c` mangles quotes; write scripts to files.
