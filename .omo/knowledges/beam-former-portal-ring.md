# Beam Former portal ring (J2, 2026-10-23)

The owner chose concept J2 from the Blender study (`tools/blender/beam_halo.py`,
`.omo/knowledges/beam-halo-study-2026-10-23.md`). `BeamPortalMesh` (geometry, palette, animation, six facing
transforms) and `BeamPortalRenderer` (render types, world draw, guide queue) implement it; `BeamFormerRenderer`
calls it before its positive-facing-only beam guard.

- **Where.** Each linked body's lowest block already holds a `BeamLook` on both ends (`syncLook`), so both
  ends draw one portal each with no new sync; other members have no look. Sides 2 and 3 draw (`BeamPortalMesh.drawn`).
- **Geometry.** Local coordinates centred on the square; main bands at 19/16 from the back,
  spill 18.999/16, streaks 14.02/16 (the disc at 14.01/16 is gone, see below). The study's thin turning line (8.94-9.06) and 16 dashes
  (9.35-9.5) were removed on 2026-10-24 at the artist's request: only the thick ring stays. The pair's render box already covers the ring.
- **Render types.** Vanilla beacon shader (unlit vertex colour), white texture, no cull, LEQUAL depth test,
  colour-only writes, SRC_ALPHA/ONE glow only (one pass, one buffer since the disc went).
- **LOD.** `CableBeamMesh.detail`: 3096 / 1560 / 600 vertices per 2x2 end, 6168 / 3096 / 1176 per 3x3 end
  (96/48/24 segments, 6/3/1 spill bands); spill caps at 48 segments (`soft`). Cut-off 256 blocks. Animation left: the streaks and a 4% pulse.
- **Alarm.** Glow uses the beam's red.

## GuideME scenes (the non-obvious part)

GuideME 21.1.1 `GuidebookLevelRenderer.renderContent` (inside the `runAsFancy` lambda
`lambda$renderContent$0`): blocks, block entities, flush of opaque layers, then `renderBlocks(..., true)`.
That translucent pass tests `renderType != translucent() || translucent`, so it queues **every** block's solid
and cutout quads again; they flush later. A portal drawn from the block entity renderer, or even after the
first opaque flush, is covered by the former's own terrain because the portal writes no depth (observed in
game: the ring only survived where the depth-writing beam core sat in front of the plate).

`GuidebookLevelRendererMixin`: `@WrapMethod renderContent` scopes `BeamPortalSceneQueue` (cleared in `finally`,
also for `SceneExporter`); `@Inject` at `TAIL` of the lambda (`require = 0`) flushes the non-translucent chunk
layers (the duplicate quads, same depth, no visible change) and then draws the queue while the scene camera is
still set. If a GuideME build renames the lambda, scenes just show no portal. Recheck on GuideME updates.

Known world limitation: translucent terrain drawn later (water, stained glass) can blend over the glow even when
behind it, as with the beam's own glow.

In-game check recipe: `build/beam-portal-check/tools/` (shoot.sh world + guide, guide.sh guide only, scenes.py
writes a scratch `portal_trial` page with three linked pairs from NBT; never commit that page). For a blue
in-world pair set `requireOperatingPower = false` in the run dir's `config/ae2federation-server.toml`.

## Portal performance pass (2026-10-23)

Three look-identical changes (owner-approved):

- **Batching.** DISC and GLOW are fixed buffers (registered in `CableFlowRenderer.registerBuffers`); the one
  `AFTER_BLOCK_ENTITIES` listener flushes portal DISC, portal GLOW, beam GLOW, beam CORE in that order. All world
  portals cost two draw calls per frame instead of two per end, and asking for them no longer ends another mod's
  shared batch. GuideME keeps its queue and flushes DISC then GLOW per queued portal.
- **Per-end culling.** `RenderLevelStageEvent#getFrustum` (public in NeoForge 21.1) is captured at `AFTER_ENTITIES`,
  which the patched `LevelRenderer` fires right before the block-entity loops with the same frustum, and cleared by
  the flush. Each end tests `BeamPortalMesh.bounds` (radius 9.8/16 about the square centre, depth 14.01/16 to 19/16).
  No frustum (event not fired): draw. Guide scenes never cull.
- **One matrix per end.** Static orientation matrices built from `oriented`; `transform` multiplies the pose once per
  end. One matrix rounds differently from two: up to one float step at ~200 blocks camera-relative (1.5e-5 block);
  `BeamPortalTransformTest` allows max(1e-5, 2 ulp).

Measured (Xvfb + llvmpipe on a loaded 60-core host, so "flush" includes software rasterisation and numbers move
~10% run to run). Scene: 24 linked 2x2 pairs, `build/beam-portal-perf/scene.txt`; two complete runs per build,
medians of 5 s windows; ms per frame of portal emit (CPU) + portal flush:

| Pose | Old | Batch + cull + matrix | Draw calls |
|---|---|---|---|
| overview, 48 ends in view | 8.70 ms (emit 1.63), 18.5 FPS | 7.09 ms (emit 1.53), 19.7 FPS | 96 -> 2 |
| among the pairs, 28 ends, 8 behind the camera | 12.10 ms (emit 2.89), 14.6 FPS | 9.78 ms (emit 1.90), 15.3 FPS | 56 -> 2 |

CPU probe (JUnit, real `BufferBuilder` writes, all facings): near end 192 -> 161 us (-16%) from the single matrix.
A real GPU was not measured (driver cost per draw call differs). Tools (not in git): `build/beam-portal-check/tools/`
`bench.sh` / `bench-all.sh` / `summarise.py`, instrumentation patches and probe in `build/beam-portal-perf/`; the
benchmark types the scene twice because the XTEST typer sometimes drops a command (check the end count per run).

## Soft-band segments (2026-10-24)

Near detail drew the dark disc and the six faint spill bands with 96 segments (5272 vertices per end). They now
use at most 48 (`CIRCLE[max(detail, 1)]`): 3928 vertices, -25.5%; detail 1 and 2 are unchanged. The main bands,
then the line and the dashes, kept their segments, since the bright ring's facets would show.

- **CPU probe** (same worktree, only `BeamPortalMesh.java` swapped, three alternating runs, medians): near end
  75.7 -> 56.7 us (-25%); detail 1/2 controls unchanged within noise. Output in
  `build/beam-portal-perf/option4-probe.txt`. No in-game FPS run: the expected gain (a few hundred us per frame
  with ten near ends) is below this host's frame-time noise.
- **Look.** `closeup.sh` (one linked Z pair, day/night, close/oblique/very close) now freezes ticks and steps to
  a multiple of 64 game ticks, so before/after share one phase of every portal and beam animation (all loop in
  64 or 32 ticks) and pixel diffs isolate the change. Very close (pair ~1.5 blocks away, 1600x960): 75 pixels
  differ by more than 16/255, all on the dark disc's one-pixel rim; mean difference 0.02/255. Images in
  `build/beam-portal-lod-check/`.

## Thick ring only (2026-10-24)

The artist asked to keep only the thick ring. The turning line (384 vertices at every detail) and the dashed ring
(256 / 128 / 64) are gone, with `DASH` and their rotations; the spill glow, disc and streaks stay. Per end:
3928 / 2264 / 1144 -> 3288 / 1752 / 696 vertices. The `bounds` radius stays 9.8/16, the spill's outer edge.
Phase-locked before/after close-ups: `build/beam-portal-rings-check/` (`closeup.sh <worktree> <label> <topic>`).

## 3x3 fronts: two rings (2026-10-24)

The owner asked for two thick rings on the 3x3, cores 11.5-12 and 15.5-16 px from the face centre (16 px per
block), over the artist's PR #7 emitter: inner over the `d` blocks and the corner arms' ends (r 8-13), outer over the
octagonal `ring` + `connector` parts (r 13-17, top z 15). Bands are now offsets from a core's inner/outer edge
(`CORES`, `MAIN`, `SPILL` as {edge, offset, edge, offset, alpha}); the 2x2's core 7-8 reproduces its old radii
bit for bit (a temporary probe compared 370M emitted floats and the bounds against the previous class). Choices
not given by the owner: same z planes as the 2x2 (rings 19/16, four pixels above the ring parts, coplanar with
but radially clear of the 3x3's z-19 prongs at r 6-8); disc and streaks scaled by side/2 (disc 7.2, streaks
6.9 -> 2.7); spills of the two rings meet at 13.8 / 14. The centre is side/2 in `oriented`, the orientation
matrices, `bounds` (radius = outer core + 1.8) and the renderer's distance; the guide queue entry carries the side.
Vertices per 3x3 end: 6360 / 3288 / 1272.

In-game check: `build/beam-portal-check/tools/closeup3.sh <worktree> <label> <topic>`, one 3x3 pair at x 20-22,
z 14 and 24; the beam fills the 3x3 square in front of the face, so cameras stay above or beside it (a camera
inside the beam sees only white haze). Images `build/beam-former-3x3-check/`.

## No dark disc (2026-10-24)

The owner saw the emitter's centre turn black whenever a pair linked: the study's dark plate disc (alpha .68,
r 4.8 on a 2x2, 7.2 on a 3x3, at 14.01/16) covered the artist's cyan centre plate. Linked vs unlinked shots:
`build/beam-portal-centre-check/`. The owner does not want the disc, so it went with its render type and buffer:
`emit` lost its `glow` flag, the portal is one additive GLOW pass, one draw call per frame, and `bounds` starts
at the streaks' 14.02/16. Earlier sections keep their history; their DISC mentions describe the old two-pass setup.
