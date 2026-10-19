# Cable beam performance (2026-10-13)

The user saw FPS fall from 100+ to about 30 with many ordinary Federation Cables in view. Cause: the beam block
entity renderer (`CableFlowRenderer` / `CableBeamMesh`) rebuilds every beam on the CPU every frame.

## Measured (temporary JUnit probe, deleted afterwards; CPU math only, no buffer writes)

| Detail (distance) | 400 cables: ms per frame | Vertices per frame |
|---|---|---|
| 0 (< 24 blocks, 64 segments/block) | 25.1 | 2,369,280 |
| 1 (24-48, 24 segments) | 12.4 | 956,160 |
| 2 (> 48, 4 segments) | 0.2 | 115,200 |

The math alone caps 400 near cables at about 40 FPS. The probe leaves out `addVertex(pose)`/`setNormal(pose)` matrix
work, buffer encoding, and the GPU upload: 2.4M BLOCK vertices are about 76 MB per frame.

## Other costs found in code

- GLOW and CORE are not fixed buffers, so they share `BufferSource.sharedBuffer`; every `getBuffer` switch ends the
  other batch (`MultiBufferSource.BufferSource.getBuffer`). That is 2 draw calls per cable per frame, and CORE has
  `sortOnUpload = true`, so each of its batches is also sorted.
- `wave()` is computed twice per sample (segment i's end is segment i+1's start); fog computes it per vertex.
- `getViewDistance()` is 256.

## What was done (same look, 2026-10-13)

1. **Flat stretches merge** (`CableBeamMesh`): along a straight stretch only the pulses change the widths and the
   fog's brightness. Kept samples: ends and bends. A slope window (O(n)) extends a segment while every width stays
   within 0.001 block and the fog within 1/512 alpha of the full sampling. Pulses are computed once per sample and once
   per fog place (fog vertices share ~20 places). An O(n^2) merge check cost more than it saved; avoid it.
2. **`CableBeamFrame`**: the pulses depend on position only through coordinate parity (`phase` uses
   `floorMod(c, 2)`), so (mask, parity, detail, style, pass) is computed once per frame and replayed. Identical output.
3. **Batching**: GLOW/CORE registered with `RegisterRenderBuffersEvent`, flushed at `AFTER_BLOCK_ENTITIES` (glow
   first). Vertices written with the 11-argument `addVertex`, the block-format fast path; normal transformed once.
   CORE keeps `sortOnUpload`: sorting a frame's ~15k carrier quads costs about 1 ms.

The preview harness (`CableBeamNativePreview`) had a stale reflective constructor (2 args; the builder now takes line
and collar sprites) and is fixed.

## Results

CPU-only, 400 near cables, real `BufferBuilder` writes (probe kept in the session scratchpad, not in the tree):

| Scene | Before | After |
|---|---|---|
| Straight, idle | 74 ms, 1.98M vertices | 4.1 ms, 0.18M |
| Straight, 60% | 74 ms | 10.3 ms, 0.50M |
| Straight, full | 75 ms | 13.3 ms, 0.65M |
| Junctions, 60% | 170 ms, 4.63M | 22.9 ms, 1.11M |

In game (`build/perf-check`, Xvfb + llvmpipe, so absolute FPS is low and rasterization runs on the CPU; the
"blockentities" profiler section includes the beam flush):

| Scene | Before FPS / block entities | After |
|---|---|---|
| 400 straight, idle | 3.65 / 161 ms | 7.60 / 18.6 ms |
| 400 straight, 5 of 8 | 3.54 / 170 ms | 6.51 / 51 ms (11 ms building, 40 ms flush) |
| 8x8x6 solid block | 1.66 / 462 ms | 6.14 / 40 ms |

Look: native preview at the same frame differs in 1-2% of pixels, nearly all by under 8/255, along beam edges.

## Method

- In-game: the capture world (`beam_world.sh start <jar>`), vsync off and maxFps 260 in the client options, a scripted
  scene, F3 shown, then F3+L for a 10 s profile (`debug/profiling/*.zip`, `client/profiling.txt`; "Tick span" there
  counts frames). Five Processing Endpoints on a row make it count 5 of 8 (Jade confirms) without any network.
- Same-frame look: `runBeamPreviewClient` in a `git worktree` of the old commit and in the tree, then a pixel diff.

## Not done (bigger)

Static beam meshes animated by a shader with the `GameTime` uniform would make the per-frame CPU cost near zero,
but it is a bigger project with shader-pack (Iris) risk.
