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
