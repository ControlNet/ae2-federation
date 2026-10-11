# M optical cable renderer

The approved M design is a continuous pale cyan carrier, moving axial pulse envelopes and soft cyan shoulders.
It replaces the previous `CableFlowRenderer` interior without changing cable shells, textures, items, connectivity,
capacity accounting or network packets. It is always enabled in world rendering. Inventory items retain their
existing baked model.

## Visual input

`CableBeamStyle.fromLoad(double)` maps a nonnegative finite utilization ratio to the visual parameters below.
There is no real capacity telemetry yet: the production renderer explicitly uses `DEFAULT_LOAD = 0.6`.
This is a presentation setting, not measured utilization. The overload state is therefore available to the renderer
and native preview but cannot currently be triggered by actual traffic.

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
fog still write no depth, so a frame behind them can still dim them. Each buffer is filled before switching render
types.

Straight runs share a world-coordinate phase with a bounded 64-tick clock, including negative coordinates.
Fractional densities crossfade integer pulse trains to keep seams and clock wraps continuous. Elbows use curved
centerlines and crossfade the adjacent axis phases. Junctions meet at the center. This is decorative optical motion,
not simulated packet routing or a claim about actual transfer direction.

At distances above 24 blocks, sampling falls from 64 to 24 segments per full block, and fog from four to two
segments per arm; above 48 blocks, four segments retain the carrier plus the two outer fog volumes, unanimated, so
distant cables still glow. Near straight cables emit 4,960 vertices (4,096 beam, 864 fog), with 14,592 for six-way
junctions; distant straight cables emit 256. These are geometry
budgets, not FPS measurements. `CableFlowGeometry` remains as a legacy reference for existing export/test tooling.

Blender emission and compositor bloom are approximated with native geometry and blending; this is not pixel-identical
to Blender, and it does not require a shader pack or add actual world lighting.

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
