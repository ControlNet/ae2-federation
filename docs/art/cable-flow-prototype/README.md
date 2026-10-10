# Standard-shader cable flow rendering

Date: 2026-09-27. Current development builds enable this world renderer by default.
It uses Minecraft's existing `entityTranslucentEmissive` shader, with no custom
GLSL, post-processing, dynamic world lighting, or new runtime dependencies.

Since 2026-10-09 the enclosure around the flow is AE2's dense cable, built in code, not the V07 glass and collars
described below; the flow renderer itself is unchanged. See [Dense cable geometry](../README.md#dense-cable-geometry).

## Current behavior

Place a Federation Cable to see the animated interior, including curved elbows
and shared junction centers. No chat command or JVM option is required, and the
preview command has been removed. Items retain their V07 appearance.

Published version 0.0.1 still has the original opt-in behavior. The captures,
benchmarks, and reviewed artifact below predate the default-on change.

The historically reviewed artifact is
`build/visual-flow-prototype/ae2federation-cable-junction-refined.jar`.
Its identity and client/server match are recorded in [artifact.json](artifact.json).
Use it as the AE2 Federation JAR in a Minecraft 1.21.1 / NeoForge 21.1.250
installation with the project's existing dependencies. Keep only one Federation
JAR in that installation's mods directory.

## Visual evidence

The captures and runtime JSON below are local review artifacts excluded by
`.gitignore`. They remain available in the review workspace but are not included
in a fresh clone. This README, implementation, texture source and production
texture remain version-controlled.

Latest junction refinement (matching close camera positions; animation phases
may differ between still captures):

| Connection | First prototype | Refined prototype |
| --- | --- | --- |
| Elbow | [Before](09-elbow-before-close.png) | [After](18-elbow-final.png), [motion GIF](19-elbow-final.gif), [MP4](19-elbow-final.mp4) |
| T junction | [Before](11-tee-before-close.png) | [After](17-tee-final.png) |
| Six-way junction | [Before](10-junction-before-close.png) | [After](15-junction-final.png), [motion GIF](16-junction-final.gif), [MP4](16-junction-final.mp4) |

Initial prototype evidence retained for comparison:

- [Recorded motion, GIF](02-flow.gif) / [MP4](02-flow.mp4): 6.4 seconds of actual
  client capture; 128 video frames with 128 distinct decoded frame hashes.
- [Prototype daylight](01-standard-shader.png).
- [V07 at the same camera](03-v07-same-view.png).
- [Elbow](04-elbow.png).
- [Six-way junction and surrounding mask gallery](05-six-way.png).
- [Night after F3+T resource reload](06-night-after-reload.png).

PNG files are untouched native F2 screenshots. The video records the X11 game
window, and the GIF is a resized video export. No concept render or generated
image is used as gameplay evidence.

## Implementation

- Existing collars and glass remain chunk-baked, retaining their V07 assets.
  The world model always omits its old opaque cutout core in favor of the BER interior.
  Item models retain their original V07 appearance.
- Straight runs retain their original pair of double-sided interior ribbons.
  L-shaped connections use a four-facet quarter bend joining the two arms.
  T-shaped and higher-degree junctions trim arms at a shared center, with one
  central patch per plane rather than overlapping axis strips. Geometry is
  cached for all 64 masks.
- Near a connection center, moving highlights crossfade into a steady dim base.
  Branch centers and their arm endpoints share a reduced opacity of 0.6; elbow
  bases retain full opacity modulation to keep the bend readable.
- The generated 16x32 RGBA texture retains a dim cyan base, softer lateral
  edges, a dominant bright pulse and a smaller secondary activity patch.
  The top 16x16 tile preserves the original straight-flow pixels; the lower
  tile supplies a shared dim junction patch. Every patch edge matches the
  incoming base texture profile. Both tiles use the same standard render type.
  Its source was `tools/visual/cable_flow_texture.py`; since 2026-10-09 the
  shipped texture is the artist's and that generator is retired. Frozen V01–V07
  archives are untouched.
- UVs move using game time plus partial tick. The repeat is four world blocks,
  with one texture cycle per 64 ticks. Adjacent straight segments share phase,
  including negative and large coordinates; clock wrapping preserves periodicity.
- Rendering uses standard vertex attributes and a shared render type through
  `MultiBufferSource`. There are no direct OpenGL state changes or manual
  per-cable buffer flushes. Local neighbor blockstates are read for the visual
  mask; no federation graph traversal or traffic synchronization is introduced.

## Verification actually performed

```bash
pixi run --manifest-path tools/visual/pixi.toml generate
pixi run --manifest-path tools/visual/pixi.toml validate
./gradlew :neoforge-1.21.1:build :neoforge-1.21.1:verifySharedJarContent --dependency-verification=strict --no-configuration-cache --no-daemon
git diff --check
```

(`verify_art_versions.py` was removed on 2026-10-09.) Latest results: `BUILD SUCCESSFUL`; 253 tests, zero failures/errors/skips. Five
geometry tests cover all mask boundaries, containment inside the connected glass
union, spatial phase continuity, clock-wrap continuity, all twelve perpendicular
elbows and unique shared center planes for branching nodes. Asset validation passed with 267 models,
5,094 baked faces, 64 masks and reproducible bytes. New texture checks require
transparent lateral edges, a continuous partially transparent base and a brighter
pulse. The junction tile's four edges must exactly match the incoming dim base
profile. All archived art hashes passed.

The actual built JAR was run in an isolated production client and dedicated
server copied from the V07 gallery, at `build/visual-flow-prototype`. The final
artifact loaded the saved world and joined successfully. Latest runtime inspection
covered the close elbow, T and six-way junctions and F3+T resource reload.
Initial-prototype checks also covered the straight line, day/night and client
mode switching in both directions. No ERROR/Exception
entries were found in the reviewed client log. Existing LDLib/vanilla warnings,
server startup catch-up warnings and teleport movement warnings were present.
This is not an exhaustive per-mask visual or shader-mod compatibility pass.

## Initial prototype performance sample

These samples predate the junction refinement. They have not been rerun for
the new, more detailed junction geometry.

Same stationary stress-scene camera: player `(130.5, 25, 80.5)`, facing
`(130, 2, 28)`, 2,048 cables plus 128 machines. Xvfb, software OpenGL,
1280x720, render distance 8, graphics mode Fancy, FPS cap 60. F3+L recorded
approximately ten seconds per mode, without video recording during profiling.

| Mode | Frames | Mean frame time | P95 | Max queued uploads/batches |
| --- | ---: | ---: | ---: | ---: |
| V07 | 192 | 52.20 ms | 64.50 ms | 0 / 0 |
| Standard-shader preview | 193 | 51.98 ms | 67.70 ms | 0 / 0 |

The initial sample after teleport is retained as warm-up only: it had 86 pending
uploads and is excluded from the comparison. [profiles.json](profiles.json)
contains all three summaries and raw archive paths. These short software-renderer
samples show similar average times and a slightly higher preview P95. They do
not establish a speedup, general performance equivalence, or hardware GPU cost.

## Prototype limits

- Motion is decorative. It does not represent real transfer direction or load.
- Straight-run phase continuity is implemented. Elbows now have continuous
  curved geometry, while their center is a steady dim base. Highlights soften
  before the bend/junction; this does not route a single identifiable pulse
  around corners or through a graph.
- The interior is a ribbon approximation of volume; apparent thickness varies
  with the camera. Glass/BER transparency has only been checked in the pictured
  scenes. Water, stacked transparent blocks and all graphics modes need further
  coverage; enabling the renderer by default does not expand that evidence.
- Effects have a 256-block BER view limit. Beyond it, world rendering retains only
  the baked enclosure; no distant interior LOD has been implemented.
- No Sodium, Embeddium, Iris or shader pack was installed in this run. Standard
  shaders reduce integration complexity but do not prove compatibility.

## Repeat the isolated preview

From the repository root, use the already prepared test installation with a
current development JAR installed on both client and server while they are stopped:

```bash
(cd build/visual-flow-prototype/server && ./run.sh nogui)
```

In another terminal, start a free display and the client after the server logs
its `Done` marker:

```bash
Xvfb :81 -screen 0 1280x720x24 +extension GLX
```

```bash
DISPLAY=:81 LIBGL_ALWAYS_SOFTWARE=1 pixi run --manifest-path tools/visual/pixi.toml portablemc \
  --main-dir build/ae2f-work/prod-client --work-dir build/visual-flow-prototype/client \
  start --jvm /usr/bin/java --resolution 1280x720 \
  --jvm-args='-Xmx2G' \
  -u VisualProbe -s 127.0.0.1 -p 25581 neoforge:21.1.250
```

The server-console camera command for the main comparison is:

```mcfunction
time set noon
tp VisualProbe 7.5 3.2 11.5 facing 6.5 2.5 8.5
```

Close junction cameras use explicit yaw/pitch so the eye aims at the actual
center (the default teleport-facing calculation used by the earlier gallery
cameras aims from feet):

```mcfunction
tp VisualProbe 30.8 3.0 12.5 146.976 25.152
tp VisualProbe 40.8 3.0 12.5 146.976 25.152
tp VisualProbe 60.8 3.0 37.5 146.976 25.152
```

Real keyboard input can capture F2 screenshots:

```bash
DISPLAY=:81 pixi run --manifest-path tools/visual/pixi.toml python tools/visual/game_input.py shot build/visual-flow-prototype/client/screenshots build/visual-flow-prototype/review.png
```
