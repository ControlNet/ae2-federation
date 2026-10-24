# Beam Former portal ring (J2, 2026-10-23)

The owner chose concept J2 from the Blender study (`tools/blender/beam_halo.py`,
`.omo/knowledges/beam-halo-study-2026-10-23.md`). `BeamPortalMesh` (geometry, palette, animation, six facing
transforms) and `BeamPortalRenderer` (render types, world draw, guide queue) implement it; `BeamFormerRenderer`
calls it before its positive-facing-only beam guard.

- **Where.** Each linked body's lowest block already holds a `BeamLook` on both ends (`syncLook`), so both
  ends draw one portal each with no new sync; other members have no look. Only side 2 draws.
- **Geometry.** Local coordinates centred on the square; main bands, line and dashes at 19/16 from the back,
  spill 18.999/16, disc 14.01/16, streaks 14.02/16. The pair's render box already covers the ring.
- **Render types.** Vanilla beacon shader (unlit vertex colour), white texture, no cull, LEQUAL depth test,
  colour-only writes. Disc alpha-blended and flushed first, then the SRC_ALPHA/ONE glow.
- **LOD.** `CableBeamMesh.detail`: 3928 / 2264 / 1144 vertices per end (96/48/24 segments, 6/3/1 spill bands,
  4/2/1 per dash); disc and spill cap at 48 segments (`soft`), the outer line keeps 96 segments so its
  +3.75 deg/64-tick turn loops. Cut-off 256 blocks.
- **Alarm.** Glow uses the beam's red; the disc stays dark red (.028, .006, .004) instead of the study's red tint
  (no red J2 render was ever shown, and a bright red disc read as a solid blob in game).

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
the line and the dashes keep their segments, since the bright ring's facets would show.

- **CPU probe** (same worktree, only `BeamPortalMesh.java` swapped, three alternating runs, medians): near end
  75.7 -> 56.7 us (-25%); detail 1/2 controls unchanged within noise. Output in
  `build/beam-portal-perf/option4-probe.txt`. No in-game FPS run: the expected gain (a few hundred us per frame
  with ten near ends) is below this host's frame-time noise.
- **Look.** `closeup.sh` (one linked Z pair, day/night, close/oblique/very close) now freezes ticks and steps to
  a multiple of 64 game ticks, so before/after share one phase of every portal and beam animation (all loop in
  64 or 32 ticks) and pixel diffs isolate the change. Very close (pair ~1.5 blocks away, 1600x960): 75 pixels
  differ by more than 16/255, all on the dark disc's one-pixel rim; mean difference 0.02/255. Images in
  `build/beam-portal-lod-check/`.
