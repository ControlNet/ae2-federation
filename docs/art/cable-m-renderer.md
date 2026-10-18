# M optical cable renderer

> Historical. Since 2026-10-18 ordinary cables draw R / LITE (`BundleBeamMesh.emitCell`), and M's carrier, shoulders,
> fog and their tests have been removed from the code. The visual input, alarm and distance tiers below still apply
> (`CableBeamStyle`, `CableBeamMesh.detail`); the geometry and budget sections record M as it shipped.

The approved M design is a continuous pale cyan carrier, moving axial pulse envelopes and soft cyan shoulders.
It replaces the previous `CableFlowRenderer` interior without changing cable shells, textures, items, connectivity,
capacity accounting or network packets. It is always enabled in world rendering. Inventory items retain their
existing baked model.

## Visual input

`CableBeamStyle.fromLoad(double)` maps a nonnegative finite utilization ratio to the visual parameters below.
In the world the ratio is the cable's capacity count over the cable capacity
([cable capacity counting](../architecture/cable-capacity-counting.md)), `CableBeamStyle.loadOf(units, capacity,
enforced)`. It is not measured traffic. The server counts each cable when its domain's evidence or the limit changes,
at most every 10 ticks per cable, and sends units, capacity and enforcement in the block entity's update packet; the
values are derived and never saved. Until a count arrives, and for a cable in no domain, the load is 0.

The alarm (`load > 1`) means the cable stops its domain, so it shows only while the limit is enforced; without
enforcement the load clamps to 1. The beam is per cable: in a stopped domain only the cables over the limit turn red,
and the topology screen shows the domain as a whole. The renderer eases the drawn load with `smooth`, but takes the
alarm from the synced count itself. The count changes only with the domain, so it needs no hysteresis.
`DEFAULT_LOAD = 0.6` remains the native preview's medium setting.

For `u = min(load, 1)`:

| Parameter | Mapping |
| --- | --- |
| Carrier radius, blocks | `0.018 + 0.027 * u` |
| Halo radius, blocks | `0.06 + 0.09 * u` |
| Pulse radius amplitude, blocks | `0.028 * u` |
| Pulse density | `1 + 2 * u` |
| Emission factor | `0.45 + 1.55 * u` |
| Normal palette | Pale cyan core, cyan shoulders |
| Overload | Strictly `load > 1`: warm core, red shoulders |

Idle retains a carrier. At exactly 100% the beam stays cyan. Above 100%, dimensions clamp to full load and red alarm
intensity breathes over 32 game ticks (1.6 seconds at 20 TPS), without extinguishing the carrier.
`smooth(previous, target, seconds)` supplies optional frame-rate-independent interpolation with a 0.25-second rise
and 0.8-second fall; future telemetry integration must explicitly adopt it and decide its overload hysteresis.

## Minecraft implementation

`CableBeamMesh` caches centerlines and cross-sections for all 64 connection masks. Two crossed planes form the
carrier, pale at its centre and saturated cyan at its rim, with three vertex-alpha gradient layers: narrow shoulders,
a broader envelope following the pulse, and an outer halo.

The shell shows its interior only through a 6-voxel glass window in the middle of each 12-voxel face; a 3-voxel
opaque frame surrounds it. The beam sits 6 voxels behind the surface, so at the common 30-60 degree downward view
the frame between the top and side windows hides a thin centre line. The carrier is therefore several times its
original width, the halo stays inside the window even at the overload breath (half-width 3/16), and the fog below
carries the colour to the angles where the frame still hides the beam.

In-shell fog scatters the beam colour through the whole interior: three nested soft-edged volumes (half-widths
0.29, 0.20 and 0.11 blocks, inside the 10-voxel neck; alpha 0.05, 0.07 and 0.10) follow the connected arms. The fog
is deliberately faint: a light haze, with pulses (`0.55 + 0.9 * wave`) lifting it locally. Each face is transparent at its long
edges and densest along its middle, so the stacked volumes brighten towards the beam. Fog brightness follows load
and the overload alarm, and pulses brighten it locally using the same world-anchored phase. It is drawn in the
additive glow pass; the shell's glass, drawn later in the translucent terrain pass, blends over it. `CableFlowRenderer` uses the vanilla unlit beacon shader and the vanilla white texture:
an additive glow pass followed by a normal-alpha carrier pass. Both depth-test; only the carrier writes depth.
The shell is translucent terrain, which Minecraft draws after block entities: without carrier depth, any shell face
behind the beam that faces the camera (another arm of an elbow or junction, a neighbouring cable) painted its frame
over the beam. The glow goes first so the carrier's depth does not fight its coplanar shoulders. The soft glow and
fog still write no depth, so a frame behind them can still dim them.

In the world each pass has a buffer of its own (`RegisterRenderBuffersEvent`), flushed glow first at
`RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES`, so the carrier's depth is in place before the shells in every
graphics mode. In vanilla's shared buffer every cable's switch between the two passes ended a batch: two draw calls
per cable per frame. Elsewhere (previews, guide scenes), `draw` still fills each pass before switching render types.

Straight runs share a world-coordinate phase with a bounded 64-tick clock, including negative coordinates.
Fractional densities crossfade integer pulse trains to keep seams and clock wraps continuous. Elbows use curved
centerlines and crossfade the adjacent axis phases. Junctions meet at the center. This is decorative optical motion,
not simulated packet routing or a claim about actual transfer direction.

At distances above 24 blocks, sampling falls from 64 to 24 segments per full block, and fog from four to two
segments per arm; above 48 blocks, four segments retain the carrier plus the two outer fog volumes, unanimated, so
distant cables still glow. `CableFlowGeometry` remains as a legacy reference for existing export/test tooling.

### Cost per frame

The beam is rebuilt every frame, so three things keep it cheap (2026-10-13; the user saw 100+ FPS fall to about 30
with a few hundred cables):

- **Flat stretches merge.** Along a straight stretch only the pulses change the beam's widths, and the fog's
  brightness. Samples are kept wherever the centerline bends; between them, a segment reaches as far as every width
  stays within 0.001 block of the full sampling (under half a pixel up close) and the fog within 1/512 alpha. Near
  straight cables emit about 450 vertices idle, 1,240 at 60% and 1,640 full (4,960 before); six-way junctions 1,060,
  3,440 and 4,590 (14,592 before). `CableBeamTest` checks the shape against the full sampling and these budgets.
- **Alike cables share one beam per frame.** The pulses depend on a cable's position only through whether each
  coordinate is odd or even, so `CableBeamFrame` computes each (connections, load, parity, detail) once per frame and
  replays it; `CableBeamFrameTest` checks the replay is identical.
- **One batch per pass** (above), and each vertex is written in one call, which a block-format `BufferBuilder` writes
  straight to memory.

Measured on the development host (400 near cables, CPU only, real `BufferBuilder` writes): straight cables at 60%
fell from 74 ms to 10 ms per frame, junctions from 170 ms to 23 ms. In-game numbers are in
`.omo/knowledges/cable-beam-performance-2026-10-13.md`.

Blender emission and compositor bloom are approximated with native geometry and blending; this is not pixel-identical
to Blender, and it does not require a shader pack or add actual world lighting.

## Bundled cables

A bundled cable keeps one beam per cell: each cell's block entity draws the ordinary beam, with its connection mask
from `BundleLook.flowMask()`, so a 2x3 bundle runs six beams. Its load is the bundle's count over the bundle's limit:
the cable capacity times the cross-section's multiple (3, 4, 6 or 9). A 3x3 bundle is unlimited, so its beam scales
against `CableBeamStyle.UNLIMITED_SCALE` = 9 cables and never alarms.

The shell is not the ordinary cable's. `BundleShellLayout` draws one shell the size of the cross-section, inset
2 voxels at its outer edges; each cell draws only its share of the outer faces, so nothing is drawn between cells.
Rails are cut per voxel from the artist's core frame (`core.png`): three voxels along each closed edge, the frame's
corner where two meet, glass elsewhere. A branch or turn opens the face it meets, and the rails turn into the branch's
walls instead of crossing the joint, as an ordinary cable's connected core faces do. Collars ring each cell where its
beam runs on, and dense arms reach each Router or Switch the bundle wholly connects to.

## Verification

From the repository root:

```bash
./gradlew :neoforge-1.21.1:build :neoforge-1.21.1:verifySharedJarContent --offline --no-configuration-cache
git diff --check
```

Expected: `BUILD SUCCESSFUL`, no whitespace errors. `CableBeamTest` covers parameter boundaries, all 64 masks at
three detail levels, glass-envelope bounds, negative-coordinate seams, clock wraps, separate draw passes and
frame-rate-independent smoothing. Testmod code is not packaged in the production JAR.

Generate native OpenGL previews using Minecraft itself (requires Xvfb on a headless Linux host):

```bash
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s '-screen 0 1280x720x24 +extension GLX' \
  ./gradlew :neoforge-1.21.1:runBeamPreviewClient --offline --no-configuration-cache
```

Expected: `AE2F_BEAM_PREVIEW_OK`, automatic client exit, and PNGs in `neoforge-1.21.1/build/beam-preview/`.
The test-only client invokes the production draw method at 0%, 25%, 60%, 90%, 100% and 125%, at two clock positions;
also inspect `mask-0.png`, `mask-5.png`, `mask-19.png`, `mask-63.png` and `shell-only.png`.
Those load values are explicit visual test inputs. The shell uses the real builder and texture atlas, with a fully
lit preview pass. These are offscreen native renderer previews, not in-world screenshots or a shader-pack/FPS test.
Build outputs and the development client directory are already ignored by Git.

## Visual correction after the first native preview

The initial native implementation was rejected for being dark and visually unlike the approved Blender M.
The key material error was using `rendertype_entity_translucent_emissive`: despite its name, its vertex shader
calls `minecraft_mix_light`. Full-bright UV light does not disable that directional shading. The corrected carrier
and glow use `rendertype_beacon_beam`, which preserves vertex color without directional shading. The missing
intermediate envelope was restored, cyan saturation and gradient weights were calibrated against the approved
reference, and close-range sampling was doubled to smooth the pulse outline.

The native harness now requires at least 1,000 bright cyan pixels in the medium-load preview and logs
`AE2F_BEAM_BRIGHTNESS_OK`; the rejected image has zero pixels meeting that criterion. This is a narrow regression
check for lost emission, not an automated aesthetic acceptance score. `opaque-occlusion.png` includes an explicit
test-only opaque plane to inspect depth occlusion. Each of `animation-60/` and `animation-125/` contains 64 native
frames spanning one complete cycle. `medium-same-view.png` retains the original camera for an honest before/after
comparison; the other straight previews use a closer, more oblique camera to inspect the optical layers.

Compose evidence without adding image effects:

```bash
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/compose_cable_beam.py
ffmpeg -hide_banner -loglevel error -y \
  -framerate 20 -i neoforge-1.21.1/build/beam-preview/animation-60/%03d.png \
  -framerate 20 -i neoforge-1.21.1/build/beam-preview/animation-125/%03d.png \
  -filter_complex '[0:v][1:v]vstack=inputs=2[v]' -map '[v]' \
  -c:v libx264 -crf 16 -pix_fmt yuv420p neoforge-1.21.1/build/beam-preview/m-native-motion.mp4
```

The composer writes `m-native-loads-v2.png` and, when the archived rejected image exists, `m-before-after.png`.
Pass `--reference-medium /path/to/c_medium-close.png` from the Blender study to additionally generate
`m-reference-native.png`; that sheet labels the different reference lighting/camera explicitly.
