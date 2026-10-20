# Bundle render performance (2026-10-20)

The user saw FPS fall from about 120 to 55 with many 2x2 and 3x3 bundles on screen; looking away brought it back.
Investigated on `dev` at `faf6071b`; the fix landed on `dev` right after it (see "Shipped fix"). The investigation's
prototype, with its switches, counters and harness, is kept outside git in `build/bundle-perf-check/prototype.patch`.

## What is static and what is not

- Shells (translucent) and the artist's rings (solid) are chunk-mesh quads from `CableBakedModel` (`ModelData` carries
  the `BundleLook`; quads cached per look). Rebuilt only when a section re-meshes. Rings are 120-280 quads per ring.
  Not the cost.
- The middle beam (`CableFlowRenderer` BER -> `CableBeamFrame` -> `BundleBeamMesh`) is rebuilt on the CPU every frame,
  because its lobes and pulses move with `tick + partialTick`. Only the low-corner cell of each layer draws
  (`BundleBeam.length > 0`); the other 3 or 8 cells of the layer return early.

## Vertex counts per anchor (glow + core)

| Beam | near (<24) | medium (24-48) | far |
|---|---|---|---|
| 2x2 or 3x3 straight layer | 3104 + 32 | 1184 + 32 | 32 + 32 |
| 2x2 T joint (2 layers) | 9280 + 80 | 3520 + 80 | 64 + 80 |
| 3x3 T joint (3 layers) | 13888 + 80 | 5248 + 80 | 64 + 80 |
| 1x1 cell (R / LITE), for scale | 1056 + 32 | 544 + 32 | 32 + 32 |

A 3x3 layer costs the same as a 2x2 layer (one beam per layer), so per block of bundle it is cheaper than ordinary
cables; the cost is the number of visible layers.

## Measured causes (shipped code)

Unit probe (JUnit, CPU only, real `BufferBuilder` writes) and in-game counters (testmod scene below):

1. **Per-frame replay of the cached mesh** (`CableFlowRenderer.emitPlasma`): 40.7 ns per vertex, 128 us per near 2x2
   layer. Of that ~11 ns is the `addVertex` write; the rest is the per-vertex fade: a 4x4 transform into eye space,
   a 3x3 normal transform, two `normalize()` calls, then the position transform. 100 near layers: ~13 ms per frame.
2. **Cache misses while a load eases** (`CableBeamFrame` keys on the whole `CableBeamStyle`, including the eased
   `load` double). Each cable eases its own `beamLoad` with its own `Util.getNanos()` stamps, so after any capacity
   count change every anchor has a different double and misses the cache: 200 us per 2x2 layer mesh, 870 us per 3x3
   joint mesh. Distinct values last ~7 s (rising), ~23 s (falling) and ~590 s (falling to 0) at 120 FPS (jitter
   simulation, 20 cables). `CableBeamStyle.smooth` also never reaches its target exactly: it stalls 15 ulps (rising) or
   48 ulps (falling) away, because `diff * exp(-dt/tau)` rounds back to `diff`; so cables only share a key again once
   they stall on the same ulp. In game (303 anchors): compute went from 3.2 ms (76 meshes) to 30 ms (606 meshes) per
   frame, BER from 22 to 55 ms.
3. **Per-tick `refreshFlow`** (`FederationCableBlockEntity`): every visible cable cell runs `BundleLook.of` (plus a
   second one for joint anchors) once per game tick: ~2 us per cell in game, 3.7-4.1 ms per tick for 2028 cells. At
   120 FPS this lands as a spike on one frame in six. Non-anchor cells compute it only to learn they draw nothing.
4. Mesh compute per distinct key per frame: 76 meshes = 3.2 ms in the scene. `along()` evaluated the pulse wave
   (`style.wave`, a `cos`) once per side instead of once per sample, and `around()` took `cos`/`sin` per vertex.

GPU side (upload and fill) is unmeasured: only llvmpipe is reachable here (no X server on the NVIDIA driver). The
scene's 485k beam vertices per frame are ~15.5 MB of `BLOCK` vertices uploaded per frame.

## Prototype (same look) and results

- **fastFade** (`CableFlowRenderer.emitPlasmaMoved`): in the world the BER pose only translates and the model-view
  only rotates (bobbing lives in the projection), so `facing . eye` in eye space equals `n . p / (|n| |p|)` with `p`
  the camera-relative position. Per vertex: 3 adds, two dot products, one divide. Ortho: the eye direction in block
  space is the model-view's third row `(m02, m12, m22)`. Guarded by `translationOnly(pose)` and `rotationOnly(modelView)`;
  anything else (GuideME scenes, other mods' poses) keeps the old path. 40.7 -> 22.1 ns per vertex.
- **fastCompute** (`BundleBeamMesh`): cos/sin tables for 8/12/16 sides, and the wave and lobe phase taken once per
  sample. Same expressions, so bit-identical (checked over 3.65M vertices, every beam shape, detail, load and parity,
  and all 64 cell masks). 2x2 layer 196 -> 70 us, 3x3 joint 873 -> 304 us.
- **anchorOnly** (`refreshFlow`): a cell of a square bundle off the low corner of its layer sets
  `BundleBeam(axis, 0, 0, side)` from its own segment, without `BundleLook.of` (same result as `BundleBeam.of`, which
  decides "none" from position alone). Refresh 3.7-4.1 -> 1.06 ms per tick.
- **snap** (not identical during the ease, user decision): `beamLoad = target` once within 1e-4. Fixes the stall and
  shortens the miss window (falling to 0: ~7 s instead of ~10 min). Quantizing the drawn load (e.g. to 1/1024) would
  keep the cache shared during the ease too; width changes by <= 5e-5 block per step.

In game, testmod `BundlePerfScene` (flat world, 6 2x2 + 6 3x3 runs of 24 layers, 6 branches; 2028 cable BEs, 303
anchors; Xvfb + llvmpipe, so FPS is low and "flush" includes software rasterization):

| Window | cable BER ms/frame | compute | refresh (per tick) |
|---|---|---|---|
| shipped | 22.4 / 26.4 (two runs) | 3.2 ms, 76 meshes | 4.1 ms |
| + fastCompute | 20.1 | 1.5 ms | 3.7 ms |
| + fastFade | 11.3 | 1.5 ms | 4.3 ms |
| + anchorOnly | 10.1 | 1.4 ms | 1.06 ms |
| easing, shipped | 55.3 | 30.2 ms, 606 meshes | 3.7 ms |
| easing, all three | 23.8 | 12.8 ms, 606 meshes | 1.15 ms |
| easing, all + snap 1e-4 | 16.5 (10 s window, converges in it) | 6.6 ms, 320 meshes | 1.14 ms |
| looking away | 0 | 0 | 0.4 ms |

Unit probe, 100 near layers (half 2x2, half 3x3): steady 14.6 -> 7.7 ms; every anchor easing 41.3 -> 17.9 ms.

## Look check

- Replay probe: old vs new `emitPlasma` on 2.02M vertices (random translations, rotations, perspective and ortho):
  0 positions differ, 4 colours differ by 1/255 alpha (float rounding).
- In game, time frozen (`tickRateManager().setFrozen(true)`), shipped vs prototype at the same frame: 0 of 1,536,000
  pixels differ, overview and close-up, powered and unpowered. `build/bundle-perf-check/powered/before-after.png`.

## Decision (user, 2026-10-20)

Approved: round the drawn load (1/1024 steps, so cables share the frame cache while a load eases) together with the
snap to target (within 1e-4; also fixes `smooth` stalling 15-48 ulps short). Not bit-identical during an ease (width
<= 5e-5 block per step), accepted as visually identical.

## Shipped fix

- `client/PlasmaFade`: the per-vertex fade, `inEye` (the old eye-space path, kept for guidebook scenes and any pose
  that scales or turns) and `moved` / `movedOrthographic` (the short path), with the guards `movesOnly(pose)` and
  `turnsOnly(modelView)` (no allocation). `CableFlowRenderer.emitPlasma` picks the path once per anchor.
- `client/BundleBeamMesh`: cos/sin tables for every side count up to 16 (k up to sides + 2, for core caps);
  `radii()` takes the wave and the lobes' phase once per sample. `BundleBeamMeshTest`'s fingerprint is unchanged.
- `bundle/BundleBeam.offCorner(pos, segment)`: the "draws nothing" beam from the cell's own segment;
  `BundleBeam.of` uses it too, and `FederationCableBlockEntity.refreshFlow` returns early with it (`flowMask` 0, never
  drawn for a bundled cell). The renderer now reads `bundleBeam()` first and `flowMask()` only for ordinary cells.
- `client/CableBeamStyle`: `smooth` snaps within `SNAP` = 1e-4; `drawn(eased, target)` returns the target once there,
  else the eased load in `DRAWN_STEP` = 1/1024 steps. Both block entities that ease (`FederationCableBlockEntity`,
  `BeamFormerBlockEntity`) return `drawn(...)`.
- Tests: `PlasmaFadeTest` (moved vs eye-space alpha over >500k vertices of real meshes, random poses and views,
  perspective and ortho: within 2e-5 and at most 1/255 on the alpha byte, under 1 in 10,000 bytes differ; guard
  cases), `CableBeamTest` (target reached exactly within 10 s; drawn steps; two cables 3 us apart draw the same load
  in >= 95% of frames), `BundleBeamTest` (`offCorner` equals `BundleBeam.of` for every skipped cell of 2x2, 3x3,
  branch, turn and 1x2 sets).
- Verified: full unit suite 770/770; `python3 tools/dev_gametests.py --manifest` 270/270.

Final in-game numbers (same scene, shipped `faf6071b` vs the fix, each in a detached worktree with counters added only
there; ms per frame of the cable block entity renderer, refresh included):

| Window | shipped | fixed |
|---|---|---|
| steady, looking at the bundles | 23.1 / 22.8 (compute 3.1, 76 meshes; refresh 4.2) | 13.2 / 13.0 (compute 1.3; refresh 1.2) |
| easing 0-10 s after every cable's load jumps | 47.9 (compute 27.7, 606 meshes) | 14.1 (compute 1.5, 78 meshes) |
| easing 10-20 s | 48.7 (606 meshes) | 13.7 (76 meshes) |
| looking away | 0 (refresh 0.42) | 0 (refresh 0.17) |

Same frame (game time fixed at 240000 and frozen), shipped vs fixed: overview 3 of 1,536,000 pixels differ by 1/255,
close-up and 3x3 joint 0. Images and logs: `build/bundle-perf-check/final/` (`before-after.png`).

## Floor and next step

After the exact fixes, ~22 ns per vertex remains (~11 ns of it the buffer write), i.e. ~70 us per near layer. The
structural fix is the one deferred in `cable-beam-performance-2026-10-13.md`: fade (and later the lobes) in a vertex
shader, so each key's mesh is uploaded once per frame and drawn per anchor with an offset, or a static mesh animated
by `GameTime`. Custom shaders carry Iris/shader-pack risk (`iris-shader-compatibility-2026-10-14.md`).

## Harness

- Not in git: `BundlePerfScene` (testmod) + a `bundlePerfClient` run + counters in the renderer, in
  `build/bundle-perf-check/prototype.patch` (with on/off switches). Run it in a detached worktree:
  `xvfb-run -a -s "-screen 0 1600x960x24 +extension GLX" ./gradlew :neoforge-1.21.1:runBundlePerfClient`; results are
  `AE2F_BUNDLE_PERF_RESULT` log lines, shots go to `build/bundle-perf-shots`.
- For a same-frame image across two builds, set the server's game time (`getWorldData().overworldData().setGameTime`)
  and the client's (`ClientLevel.ClientLevelData.setGameTime`) to one value, then `tickRateManager().setFrozen(true)`.
  Take shots before any easing window: a shipped cable easing to 0 is still moving minutes later.
- A flat world without a domain makes every cable `unpowered` (red alarm, style ignores `beamLoad`); the scene sets
  `unpowered=false` reflectively on the client so the easing path is exercised.
- Unit tests cannot load `CableFlowRenderer` (its `RenderType` fields need a bootstrapped registry, and `Bootstrap`
  fails without FML's `LoadingModList`); keep testable math in plain classes such as `PlasmaFade`.

## Second pass (same day, after `1f721f5a`)

The user then saw ~118 FPS looking away and ~90 looking at the bundles (55 before `1f721f5a`): ~2.6 ms per frame left.
Constraint: no custom shaders (Iris), no look change committed.

### Where the time goes (measured here: CPU, Xvfb + llvmpipe, loaded 60-core host)

Same `BundlePerfScene` (2028 cable BEs, 303 anchors, 485k beam vertices per frame), new counter "BE loop" = from
`AFTER_ENTITIES` to the start of our `AFTER_BLOCK_ENTITIES` flush (the whole block entity loop: frustum tests,
dispatch, our renderer, refresh). Shipped `1f721f5a`, looking at the bundles:

| Part | ms per frame |
|---|---|
| BE loop | 16.5 |
| our renderer (`render`) | 14.2, of which anchors 13.9, the 1725 cells that draw nothing 0.3 |
| per-vertex replay inside the anchors | ~11 (about 23 ns per vertex in game, 11.6 ns in a unit probe) |
| mesh compute | 2.0 (76 meshes) |
| refresh (`refreshFlow`, runs in the frustum test's `getRenderBoundingBox`) | ~0.67 per tick |
| dispatch for the 1725 idle cells (frustum, pose push, `getLightColor`, dispatcher) | ~1-1.5 |

- JFR (render thread): 104 of 167 Java samples sit in `BufferBuilder.addVertex` (attributed to `endLastVertex` /
  `beginVertex`), only a handful in our fade math: the replay is bound by writing 32-byte vertices (15.5 MB per frame
  here), not by arithmetic. In game it costs twice the unit probe's ns per vertex because llvmpipe's raster threads
  compete for caches and memory bandwidth; on a real GPU expect nearer the probe figure.
- Allocation: `Arrays.copyOf` of every recomputed mesh each frame (124 KB per near 2x2 glow, 555 KB per near 3x3 joint)
  was the largest real allocation; 1 young GC per 15 s at 10 FPS here, roughly every 1-2 s at 90 FPS.
- Core sort (`CORE` has `sortOnUpload`): unit probe, 26k core quads sorted in 2.1 ms (~80 ns per quad); the
  scene's mostly straight layers have far fewer (8 quads each), so roughly 0.2-0.4 ms per frame. Needed while a core
  is translucent (idle load), so it stays.
- Not measurable here: driver upload of the per-frame buffer and GPU fill. The glow is 4 nested additive tubes with
  `NO_CULL`, so up to 8 layers of overdraw over the beam's screen area.

### Shipped in `cf236f8a` (all bit-identical)

1. `CableFlowRenderer.shouldRender`: false for a bundle cell off its layer's low corner (`bundleBeam().length() == 0`).
   The dispatcher then skips `getLightColor`, the style/ease and the `render` call for them (2028 -> 303 renders).
   Side effect: such a cell no longer eases its own `beamLoad`; if it later becomes an anchor its first drawn load
   comes from `smooth` over the long gap, i.e. at once near its target. Only matters while a bundle is reshaped.
2. Parity key narrowed (`BundleBeamMesh.parityAxes` / `cellParityAxes`): only the sheaths read the drawing cell's
   coordinates, each the parity along its paths' axes (`radii()` reads `world[first]`/`world[second]`). The core,
   the halo and far detail read none. 76 -> 51 meshes per frame in the scene; more in real bases at mixed coordinates.
3. Frame cache keeps arrays across frames (`CableBeamFrame.keep`): last frame's maps are swapped in, a key computed
   again writes into its old array (same key = same vertex count); leftovers are dropped next frame. A steady scene
   allocates no mesh arrays.
4. Packed layout, stride 9: x y z, normal, |n|^2, alpha, colour as ARGB bits with alpha 0 (exponent <= 1, never NaN).
   The per-vertex `(int) (c * 255)` of three colours and the |n|^2 sum are gone; replay moved to `PlasmaReplay`
   (`moved` / `inEye`) so it is unit-testable.
- Tests: `PlasmaReplayTest` writes real `BufferBuilder` bytes from the new replay and from a copy of the old renderer
  reading the mesh's raw vertices: 0 bytes differ over 1.08M vertices (moved and eye-space paths, perspective and
  ortho, bundles and cells). `BundleBeamMeshTest.aBeamReadsTheParityOfOnlyTheAxesItsArmsRunAlong` (every beam, detail,
  pass and 64 cell masks, all 8 parities: equal masked parity => bit-identical; and no masked axis is unused).
  `CableBeamFrameTest` (layout, sharing, reuse with recomputed values).

In game, two alternating runs each (`build/beam-perf-2-check/logs/`), ms per frame:

| Window | before (`1f721f5a`) | after |
|---|---|---|
| toward, BE loop (6 windows) | 16.5 (15.5-17.0) | 12.0 (11.8-12.3) |
| toward, our renderer | 14.2 | 10.1 |
| close | 18.1 / 17.5 | 13.3 / 12.8 |
| 3x3 joint view | 14.6 / 14.4 | 11.0 / 10.6 |
| easing 0-20 s | 15.6-16.6 | 11.3-12.6 |
| away | 0.7-0.8 | 0.6-0.8 |

Same frame (game time 240000, frozen), before vs after: 0 of 1,536,000 pixels differ in overview, close-up and 3x3
joint (`build/beam-perf-2-check/before-after.png`). Between two runs of the same build, clouds (client ticks) and one
small patch can differ; compare shots from one round.

### Evaluated, not shipped

- Skipping glow quads whose four alpha bytes are 0: exact in vanilla (GLOW is `SRC_ALPHA, ONE`, no depth write),
  but only 2.4% of glow quads (silhouette quads cannot be all-zero with 8/16 sides), and under a shader pack such
  quads may still write non-blended buffers. Not worth it.
- Raw `MemoryUtil` writes instead of `addVertex`: slower in the probe (19 vs 11.6 ns); `addVertex`'s fast path is
  already direct puts.
- Look-identical but bigger: replay on worker threads into one reserved buffer, drawn with `GLOW.draw(new
  MeshData(...))` at the flush, only when the BER's buffer source is the vanilla `BufferSource` (Iris's sources fall
  back). Same bytes, same order, so identical; cuts the replay's wall time by the thread count, but adds threads to
  the render path. Needs the user's go-ahead.
- Look changes (for the user to decide): back-face cull on the glow (~half the glow vertices and fill; back halves of
  the tubes vanish, glow dimmer), fewer sheath sides or samples per block (proportional), closer LOD distances
  (pop-in), smaller vertex format / other shader (changes the Iris program mapping), GPU animation (custom shader).

### Counters harness for this pass

`BundlePerfScene` as in the prototype patch, plus: an `AFTER_ENTITIES` stamp for the BE loop; same-frame shots taken
first (freeze, then `getWorldData().overworldData().setGameTime(240000)` and the client's `ClientLevelData`); then
three "toward" windows, close, joint, away, easing. Counters were applied by a script to detached worktrees of both
builds; nothing of it is in git.
