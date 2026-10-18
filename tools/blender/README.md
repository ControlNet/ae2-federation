# Federation Blender asset preview

This workspace builds editable Blender scenes directly from the current mod assets and
production cable geometry. It does not launch Minecraft. All source code lives here;
generated Java, extracted dependency textures, reports, `.blend` files, and renders live
in the git-ignored `build/` directory.

Verified with Blender 5.2.1 LTS and OpenJDK 21. Python scripts outside Blender use only
the standard library. Blender scripts use Blender's own bundled Python / `bpy`; do not
install `bpy` or packages into system Python. The visual Pillow environment is not needed.

## Build and open

Run from the repository root:

```sh
python3 tools/blender/export_cable.py
blender --background --factory-startup --python-exit-code 1 \
  --python tools/blender/build_scene.py -- --render
./tools/blender/launch.sh
```

Expected: `FEDERATION_IMPORT_OK`, `tools/blender/build/federation.blend`, and
`showcase.png`, `cable-detail.png`, `flow-study.png`. The launcher opens the saved file
and registers a session-local **Federation** tab in the 3D viewport sidebar (`N`).
No global Blender add-on installation or startup-file change is made.

The first build refuses to overwrite an existing `.blend`. For a deliberate rebuild,
first save any manual work under another filename, then run:

```sh
python3 tools/blender/export_cable.py
blender --background --factory-startup --python-exit-code 1 \
  --python tools/blender/build_scene.py -- --render --replace
```

`--replace` overwrites generated `build/federation.blend` and rendered PNGs.
Alternatively, choose a separate output directory for both export and scene generation:

```sh
python3 tools/blender/export_cable.py --output tools/blender/build/study/cable.json
blender --background --factory-startup --python-exit-code 1 \
  --python tools/blender/build_scene.py -- --output tools/blender/build/study --render
```

## Scenes and workflow

Use Blender's scene selector to switch among:

| Scene | Contents |
| --- | --- |
| Federation Showcase | All five block types; Bridge and Federation P2P parts; four generated items; a connected cable run between Router and Provider with a branch |
| Cable Beam Inspection | Low-angle view through the shell's transparent middle, using the actual connected network and unchanged flow emission |
| Cable Connection Atlas | All 64 direction masks, using full-width cable connections |
| Cable Flow Study | Isolated, straight, elbow, branch, cross, and six-way internal flow without the shell |
| Block Orientation Atlas | All 14 non-cable blockstate variants, including six Provider and six Endpoint facings |

The Showcase also contains a **Cable Detail** camera. Select it and use Blender's
**View > Cameras > Set Active Object as Camera** to inspect the main run closely.
For the clearest beam view, select **Cable Beam Inspection**. Its lower camera avoids
the opaque upper rail hiding the center beam, and its restrained studio lighting keeps
the half-transparent flow legible. The geometry, texture, alpha and emission strength
are unchanged; this is a presentation correction, not a brighter replacement beam.
Press Space to play the 240-frame, 30 fps timeline (8 seconds at 20 game ticks/second).
Animation is baked into ordinary node keyframes; opening the `.blend` does not require
trusted Python or a frame-change handler. The animation restarts at the end of the
preview range; that playback loop is not a claim of seamless texture-period looping.

Edit the actual PNGs, model JSONs, or Java geometry in the repository. Then use
**N > Federation > Reload Assets and Cable Geometry**. The button confirms that it will
replace generated scenes and save the generated `.blend`. It rereads resource bytes,
recompiles cable calculations, and replaces generated scenes; custom scenes are retained.
Manual edits inside generated scenes are replaced, so use **Full Copy** for a scene and clear its
`ae2f_generated` custom property before keeping independent art direction work.
This is explicit on-demand reload, not a background filesystem watcher.

## Import contract

- Resolve model parents and texture aliases recursively, with cycle detection.
- Read every actual blockstate JSON; apply X/Y rotations around the block center.
- Convert element bounds, element rotation/rescale, face UV rectangles and quarter turns.
- Convert Minecraft `(x, y, z)` to Blender `(x, -z, y)` at one block per Blender unit.
- Use original PNGs, nearest sampling, cutout alpha or translucent materials as appropriate.
- Read `.png.mcmeta` frame sequences, per-frame durations, frame dimensions, and RGB
  interpolation. Minecraft keeps the current frame's alpha during interpolation.
- Resolve AE2 parent models and textures from the exact `ae2_version` in
  `gradle.properties`, using its local Gradle cache. If absent, supply a resource jar:

```sh
blender --background --factory-startup --python-exit-code 1 \
  --python tools/blender/build_scene.py -- \
  --resource-pack /absolute/path/to/appliedenergistics2-19.2.17.jar
```

Additional resource-pack directories or jars can be repeated. Repository resources have
priority; explicit packs precede auto-discovered dependency jars. Missing visible
textures and unsupported custom loaders fail instead of substituting artwork. The
vanilla geometry-free `block/block` and generated-item roots have explicit semantics;
their GUI display transforms are not applied to this world-space gallery.

Bridge's current item and part JSONs each contain two unresolved `#missing` references.
They are on faces completely buried by another unrotated element. Only those provably
occluded faces are omitted, and every omission is recorded in `manifest.json`; no
replacement texture or production asset edit is made.

## Cable source of truth

`export_cable.py` compiles current production `FederationCableBuilder`, `CableCoreFaces`
and the pure connection methods from `CableVisualConnections`. The interior flow ribbons come from
`java/CableFlowGeometry.java`: the cable's first beam, which the mod no longer ships (it draws R / PLASMA), kept
here only for the older beam studies built on it.
It removes world-query methods and redirects the rendering types to the functional
`CaptureBackend.java` quad recorder. This is an offline rendering backend, not dummy
Minecraft classes or a second handwritten cable design. Production files are unchanged.
The generated copies retain their upstream licensing comments.

The recorder implements the subset of AE2 19.2.17 `CubeBuilder` used by the production
builder: face selection, per-face textures, standard/custom UVs and UV quarter turns.
Its face/UV conventions follow AE2's CubeBuilder and Fabric QuadEmitter; see its LGPL
notice. The artist's collars are baked as vanilla `FaceBakery` bakes an unrotated element
under a block model rotation without UV lock (`FaceInfo` corner order, `BlockFaceUV`
rotation, `rotateYXZ(-y, -x, 0)` about the block centre). New unsupported backend calls
fail compilation so the adapter must be extended explicitly. No OpenGL context, game world, or Minecraft startup is needed.

Every export evaluates all 4096 two-bit connection combinations. It writes 72 useful
shell states (64 cable masks plus dense/covered examples), the inventory shell, all 64
flow masks, and phase samples from the actual Java phase function. `manifest.json`
records production-source and asset SHA-256 digests for provenance. The main scene
uses narrower dense arms at machine endpoints and full-width connections between cables.

The Blender flow adapter mirrors `CableFlowRenderer`'s UV/layer/8-bit-alpha mapping.
If that renderer's vertex submission contract changes, update `flow_quads()` and the
material adapter too. Changes inside the geometry and phase functions are picked up by
re-export automatically. Baked phase samples support the supplied 30 fps / 240-frame
range; changing the preview timing requires changing/exporting that sampling schedule.

## Verify

```sh
python3 tools/blender/export_cable.py
python3 -m unittest discover -s tools/blender -p 'test_*.py' -v
blender --background tools/blender/build/federation.blend --python-exit-code 1 \
  --python tools/blender/verify_blend.py
blender --background tools/blender/build/federation.blend --python-exit-code 1 \
  --python tools/blender/verify_beam_render.py
git diff --check
```

Expected: export validates all 4096 connection sets; 26 tests pass; reopened-file check
prints `BLEND_VERIFY_OK` for scenes, UVs, packed images and evaluated flow animation;
the render regression prints `BEAM_VISIBILITY_OK` after comparing actual flow-on/off
renders at four projected centerline positions and checking animation at frame 49.
It writes `cable-without-flow.png`, `cable-detail.png`, `cable-detail-frame49.png` and
`beam-visibility.json` under `build/`.
`git diff --check` is silent. Tests use actual repository assets and exported production
geometry, not dummy model or texture fixtures. Rendered screenshots are actual Blender
output. Runtime Minecraft behavior is not tested by these commands.

## Fidelity boundaries

Blender lighting, transparency sorting, coplanar blending, emission and color management
differ from Minecraft. This tool supports visual design and geometric/UV inspection;
it does not validate the game renderer's GPU behavior or performance. Flow layers are
preserved at their actual coordinates, including coplanar BASE/FLOW layers.
The flow material discards texture alpha below 0.1 before multiplying vertex alpha,
matching Minecraft 1.21.1's `rendertype_entity_translucent_emissive.fsh`; alpha above
the cutoff remains fractional, rather than being converted to an opaque cutout.

Weighted model variants currently select the first entry. A multipart blockstate of one
unconditional part (the formable devices, whose formed look is a baked model) shows that
part's model, unformed; any other multipart blockstate and `uvlock` explicitly fail. Tint
providers, item GUI transforms, and renderer-only AE2 status overlays are not emulated.
Generated items use a pixel-edge extrusion of their first animation frame; animated
colors update, but silhouette changes across frames require a dedicated importer.

## Beam-only candidate comparison

The shell design is fixed. `beam_variants.py` creates three implemented texture profiles
for the current Java renderer, alongside the unchanged production baseline. Every profile
shares the original shell objects, materials, camera, lighting, flow geometry, phase speed
and emission strength. Only the internal flow texture changes. Production assets are
not overwritten. These are actual renderer-compatible resource candidates, not image edits.

| Profile | Design |
| --- | --- |
| A / CURRENT | Current 16x32 production flow texture |
| B / FOCUSED CORE | Pale cyan continuous core with a narrow halo and a short pulse tail |
| C / TWIN CHANNELS | Two thin channels per ribbon, with staggered pulses; crossed ribbons can show multiple strands |
| D / SPARSE PULSES | Dim continuous guide with sparse, short bright packets |

Candidates use a 64x128 two-tile PNG with the same UV layout as the current texture.
They retain the existing static BEND/HUB UV behavior; this comparison does not implement
new turn-through animation. Each candidate directory is also a Minecraft 1.21.1 folder
resource pack, changing only `assets/ae2federation/textures/entity/cable_flow.png`.

```sh
blender --background tools/blender/build/federation.blend --python-exit-code 1 \
  --python tools/blender/beam_variants.py -- --animate
python3 tools/blender/compose_beam_comparison.py
blender --background tools/blender/build/beam-variants/beam-comparison.blend \
  --python-exit-code 1 --python tools/blender/verify_beam_variants.py
```

Expected: `BEAM_VARIANTS_OK`, `COMPARISON_MEDIA_OK`, and `BEAM_VARIANTS_VERIFY_OK`.
The commands overwrite only the generated study outputs under ignored `build/beam-variants/`.
They require the existing Blender/JDK workflow plus local `ffmpeg`, with no extra Python packages.
The first command records source-asset hashes before/after and verifies they are unchanged.
The saved-file verifier checks shared shell identity, unchanged flow geometry/UVs and
emission, candidate PNG hashes, and differing animation frames.

Outputs include `comparison.png`, `junction-comparison.png`, `comparison.mp4`,
`comparison.gif`, `beam-comparison.blend`, three folder resource packs, and a JSON report.
Static comparisons use frame 25. Animation samples frames 1..94 in steps of 3 at 10 fps,
covering the production 3.2-second texture period. All rows use identical time samples.
Junction images show isolated real connection-state models in order: straight, elbow,
T-junction and six-way. Their open connection faces are the actual runtime geometry;
they are not alternate shell designs.

## Eight futuristic geometric concepts

The second study changes the internal beam geometry while keeping the shell fixed.
It implements eight deterministic mesh generators in the standard-library module
`future_beams.py`, rendered by `render_future_beams.py`:

| ID | Concept | Motion / structure |
| --- | --- | --- |
| E | Phase Helix | Cyan and violet helices rotating around a fine axial filament |
| F | Photon Gates | Traveling polygonal light gates with bright cardinal notches |
| G | Crystal Train | Faceted cyan/violet crystals traveling along a faint guide |
| H | Ion Arc | Angular white-blue arcs with violet discharge branches |
| I | Quantum Lattice | Rotating diamond wire cage with bright lattice nodes |
| J | Rift Ribbon | Translucent violet wave sheet with pink/cyan luminous edges |
| K | Orbital Nodes | Warm cores and satellites inside inclined cool-color rings |
| L | Vector Blades | Moving chevron fins with amber tip markers |

These are implemented **Blender geometry prototypes**, not texture-only resource packs
and not production Java renderer implementations. Porting requires new Java vertex
creation, per-vertex color/alpha, and animation logic. No shader parity or in-game
performance acceptance is claimed. The pure geometry module and exported sample meshes
make the designs concrete and inspectable for a later port.

```sh
blender --background tools/blender/build/federation.blend --python-exit-code 1 \
  --python tools/blender/render_future_beams.py -- --animate
python3 tools/blender/compose_future_beams.py
blender --background tools/blender/build/future-beams/future-beams.blend \
  --python-exit-code 1 --python tools/blender/verify_future_beams.py
```

Expected: `FUTURE_BEAMS_OK`, `FUTURE_MEDIA_OK`, `FUTURE_VERIFY_OK`. These commands replace
only generated study outputs under ignored `build/future-beams/`. Existing production
assets and earlier candidate studies are retained. The renderer hashes production
assets before/after and verifies the exact original shell objects, meshes and materials
are shared by every concept.

Outputs: eight wide renders, eight closeups, two four-design sheets (`designs-e-h.png`
and `designs-i-l.png`), combined contact sheets, synchronized MP4/GIF, sample mesh JSONs,
a provenance report, and `future-beams.blend`. The blend opens on a closeup and contains
ordinary shape-key animation at 10 sampled poses/second, so playback does not require
trusted scripts or a handler. All concepts use matching cameras, lights, stage and
emission strength 1. Their structural/motion/color differences are part of the design.
Geometry and palette are actual generated primitives, not stand-in assets.

The existing main run and T branch are demonstrated. Arbitrary graph routing,
production elbow/hub transitions, distance LOD, and packet/traffic semantics are not
implemented by this concept study. The external shell and machine assets stay unchanged.
`test_future_beams.py` additionally checks eight distinct geometries, stable animation
topology, cycle closure and a cross-section that fits inside the fixed dense shell.

## Optical data-transmission study

The M–P study in `optical_beams.py` follows the user requirement that the cable
transmits data through light. Every concept retains a continuous axial carrier:

- M / Coherent Stream: a pale carrier, cyan shoulder and smooth traveling packets.
- N / Phase Interference: fine cyan/violet optical helices close to the carrier.
- O / Optical Multiplex: four straight optical lanes with staggered packet phases.
- P / Burst Encoding: groups of short axial light pulses over a fine carrier.

The names describe artistic optical motifs, not physical optics simulation or a
new network protocol. Animation is decorative and does not claim measured traffic.
There are no crystals, orbiting solids, mechanical gates, or electrical discharges
in these four designs. Only the internal renderer geometry is replaced.

```sh
python3 -m unittest discover -s tools/blender -p 'test_*.py' -v
blender --background tools/blender/build/federation.blend --python-exit-code 1 \
  --python tools/blender/render_future_beams.py -- --study optical \
  --output tools/blender/build/optical-beams --animate
python3 tools/blender/compose_future_beams.py --directory tools/blender/build/optical-beams
blender --background tools/blender/build/optical-beams/optical-beams.blend \
  --python-exit-code 1 --python tools/blender/verify_future_beams.py
```

Expected: 26 tests pass; `FUTURE_BEAMS_OK: 4 optical profiles`, `FUTURE_MEDIA_OK`
and `FUTURE_VERIFY_OK: 4 distinct mesh concepts`. The generated contact sheet is
`build/optical-beams/all-designs.png`; the synchronized movie/GIF use the same stem.
The common renderer keeps its `future-report.json` report name for both studies.

Open swept optical surfaces have no internal end caps. Wave envelopes use world
coordinates, and tests verify identical geometry at adjacent straight-block seams,
including negative coordinates. Secondary lanes converge inside the demonstrated
T junction. This is not a validated arbitrary-graph phase/routing solution. Geometry
stays within a radius of 0.086 blocks on straight sections; full finite-width joins
and all elbow states still need production integration and Minecraft checks.

The study uses emission strength 1 and nested translucent optical envelopes, with
no compositor bloom. It approximates layered light in Blender; it does not claim
equivalence to Minecraft additive blending. All four use identical cameras, original
shells/materials, lights and exposure. Shape-key meshes are a review artifact; a Java
port should generate simpler geometry or use a suitable texture/shader representation.

## Parameterized capacity visualization study

`beam_load.py` implements only an artist-controlled visual response. No capacity,
traffic accounting, overload mechanic, Java change or network synchronization is
introduced. Ratios 0, .25, .60, .90, 1 and 1.25 are explicit preview inputs.

For any finite nonnegative ratio, `response(ratio)` returns bounded radii, packet
density, packet amplitude and emission. `generate_for_load`, `palette_for_load`,
and `emission_for_load` accept arbitrary ratios directly. Named scene profiles are
only a comparison harness. Editing a saved scene's `preview_load_ratio` metadata
alone does not regenerate its baked geometry; regenerate from Python instead.

Normal load retains cyan/violet colors. At full load the beam remains normal;
strictly greater than 1 switches optical shoulders to red with a warm bright core.
Load is visually clamped to 1 for geometry so overcapacity never grows outside the
shell. A persistent carrier distinguishes idle from disconnected (the latter is
not a state in this study). The design combines N's fine helical optical strands
with axial moving packets.

| Parameter | Idle | Full load |
| --- | --- | --- |
| Core radius, blocks | .005 | .012 |
| Halo radius, blocks | .027 | .072 |
| Packet density multiplier | 1 (zero amplitude) | 3 |
| Blender emission | .45 | 2.0 |

Packet trains crossfade as density changes and retain the same .625 block/s phase
speed over the 3.2-second loop. The overloaded state breathes twice per loop, with
emission 1.4–2.2; the carrier never turns off. This is optical warning modulation,
not electrical arcing. Emission is an intentional varying parameter in this study;
lighting, camera, exposure and the original shell remain fixed. Minecraft brightness
and blending need their own mapping; these are not engine-independent photometric units.

`smooth_load(previous, target, delta_seconds)` provides a frame-rate-independent
display filter, with .25 s rise and .8 s fall time constants, for future interactive
transitions. Fixed-state comparison scenes do not run a changing telemetry input.
Future overload classification should be authoritative and may need debounce;
do not classify gameplay overload from a smoothed visual ratio.

```sh
python3 -m unittest discover -s tools/blender -p 'test_*.py' -v
blender --background tools/blender/build/federation.blend --python-exit-code 1 \
  --python tools/blender/render_future_beams.py -- --study load --animate
python3 tools/blender/compose_future_beams.py --directory tools/blender/build/load-beams
blender --background tools/blender/build/load-beams/load-beams.blend \
  --python-exit-code 1 --python tools/blender/verify_future_beams.py
```

Expected: 26 tests pass; `FUTURE_BEAMS_OK: 6 load profiles`, `FUTURE_MEDIA_OK`,
`FUTURE_VERIFY_OK: 6 distinct mesh concepts`. Outputs are ignored under
`build/load-beams/`, including close/wide sheets, MP4/GIF and `load-beams.blend`.

## M coherent-stream load alternative

`beam_load_m.py` retains the original M design's straight axial carrier and smooth
traveling wave, using the same six preview ratios, brightness mapping, core/halo
limits and overload alarm as the previous helical load study. It adds no helical
strands or violet side channels. M's wave sharpness is 12, matching the original
optical M, while the helical load study uses 32. This intentional shape difference
preserves the two designs' identities at otherwise matching load-response settings.

`generate_for_load(ratio, mask, world, origin, cycle)` accepts continuous load input.
The same limitations apply: ratios are design inputs, not telemetry; these are baked
Blender prototypes, not a Minecraft renderer port. The user requested this alternative
before continuing the Java port.

```sh
python3 -m unittest discover -s tools/blender -p 'test_*.py' -v
blender --background tools/blender/build/federation.blend --python-exit-code 1 \
  --python tools/blender/render_future_beams.py -- --study m-load --animate
python3 tools/blender/compose_future_beams.py --directory tools/blender/build/m-load-beams
blender --background tools/blender/build/m-load-beams/m-load-beams.blend \
  --python-exit-code 1 --python tools/blender/verify_future_beams.py
python3 tools/blender/compose_load_alternatives.py
```

Expected: 26 tests pass, `FUTURE_BEAMS_OK: 6 m-load profiles`, `FUTURE_MEDIA_OK`,
`FUTURE_VERIFY_OK: 6 distinct mesh concepts`, and `LOAD_ALTERNATIVES_OK`.
The last command requires the earlier `build/load-beams` study. It checks matching
shell digests, placements, static frame and animation length before composing the
two styles at 100% and 125% into `m-vs-helix.png` and `m-vs-helix.mp4`.
M-only contact sheets and MP4/GIF are under ignored `build/m-load-beams/`.

## 2x2 bundle middle-beam concepts

`bundle_beams.py` (stdlib) builds concepts for the one beam in the middle of a 2x2 bundled cable, and the shell and
rings they are judged in. The shell is a straight 6-layer run whose voxels take their texels from
`textures/part/cable/dense/core.png` by `BundleShellLayout`'s rule (rails, frame corners, glass at 6,6 with its real
alpha). The rings are the artist's `large_cable_straight.bbmodel` on `textures/block/large_cable/large_cable.png`,
face UV rotations included, on every second layer boundary as in game. BASE is the shipped volume tube; Q to V are
new. Glow layers render additively, as the game's glow pass does; layers that write depth in game must fit the ring
bore, which the ring's 1-voxel flanges narrow to 10/32 voxels (radius .3125).

```sh
python3 -m unittest test_bundle_beams    # from tools/blender
blender -b --factory-startup --python tools/blender/render_bundle_beams.py -- --soft --animate
python3 tools/blender/compose_bundle_beams.py
```

Expected: 7 tests pass, `BUNDLE_BEAMS_OK: 7 concepts rendered; assets unchanged`, `BUNDLE_MEDIA_OK`. Renders, the
labelled sheets (`sheet-<view>-<load>.png`: iso and eye level at 0/60/100%, an end-on cutaway at 60% with the ring
bore in orange) and `bundle-beams.blend` go to the repo's ignored `build/bundle-beam-check/`. `--views` limits the stills.
`--animate` adds one 64-tick loop (32 frames, 3.2 s) per concept at 60% load, seen at eye level: `<key>-loop.gif`, `all-concepts.mp4` (and `-x4`, looped) and
`all-concepts.gif`. Loads are preview inputs; these are Blender prototypes, not a port.

Without `--soft` each glow layer is a shell of even light, as the game draws it: seen through, the light steps up at
every shell's outline, so the nested shells show as hard bands, worst at full load where the steps are largest.
`--soft` fades each layer as (1 - facing)^2, twice as bright face-on, so the layers blend into one soft volume. In game
that needs view-dependent alpha (per-frame vertex alpha from the camera, or camera-facing gradient strips).

## R / PLASMA at game precision

The user chose R for the 2x2 middle beam. `plasma_beam.py` (stdlib) draws it as the game could: `ship_soft` uses 16
sides and 16 samples per block (4096 quads per 2x2 layer against 4 x 1700 for four ordinary cells), `ship_six` six
thinner hard-edged shells at 12 sides (no view-dependent alpha), `approved` the 24-side, 48-sample concept. Lobes
repeat every 2 blocks, as the game's beam clock wraps. Branch and turn paths follow `CableBeamMesh.build` (straight
arms through the joint; a rounded elbow of 3/16 of the 2x2x2 joint with the pulse phase faded across); the artist's
corner rings sit in each joint, whose own shell is left out.

```sh
python3 -m unittest test_plasma_beam    # from tools/blender
blender -b --factory-startup --python tools/blender/render_plasma_beam.py -- --animate
python3 tools/blender/compose_plasma_beam.py
```

Expected: 8 tests pass, `PLASMA_BEAM_OK: 9 stills, 3 loops; assets unchanged`, `PLASMA_MEDIA_OK`. Output in the repo's
ignored `build/bundle-beam-check/plasma/`: `sheet-approved-vs-shippable.png`, `sheet-shippable-loads.png` (0, 60, 100,
125%), `sheet-junctions.png`, and a GIF and 4x MP4 loop of the run, the branch and the turn.

## 1x1 cable beam concepts

Whether ordinary cables take R too is open. `cable_beams.py` (stdlib) draws four concepts in the production 1x1
shells (core, arms and the artist's collars, through `export_cable.py`), on the inspection scene's run with its south
branch: `m_current`, the shipped M as tubes with a faint fog filling the glass (a stand-in for CableBeamMesh's crossed
ribbons and fog volumes); `r_scaled`, the bundle's R at the same share of the glass (3/16 block from the axis against
11/16 in a 2x2); `r_fill`, R scaled until its halo, breathing out under an alarm, meets the glass; `r_lite`, `r_fill`
with two sheaths of 8 sides. Light outside the glass would fall behind the rails, so every concept stays within it.

```sh
python3 -m unittest test_cable_beams    # from tools/blender
python3 tools/blender/export_cable.py --output tools/blender/build/cable-r/cable.json
blender --background --factory-startup --python-exit-code 1 --python tools/blender/build_scene.py -- \
  --output tools/blender/build/cable-r --resource-pack /path/to/minecraft-1.21.1-client.jar
blender --background tools/blender/build/cable-r/federation.blend --python-exit-code 1 \
  --python tools/blender/render_cable_beams.py -- --animate
python3 tools/blender/compose_cable_beams.py
```

The vanilla client jar supplies `minecraft:block/cube` and the like, which some block models now inherit; NeoForm's
cache keeps one as `minecraft_1.21.1_client.jar`. Expected: 5 tests pass, `FEDERATION_IMPORT_OK`,
`CABLE_BEAMS_OK: 32 stills, 4 loops; assets unchanged`, `CABLE_BEAM_MEDIA_OK`. Output in the repo's ignored
`build/cable-beam-concepts/`: `sheet-concepts-{medium,overload}-{level,close}.png`, `sheet-<concept>-loads.png` (0,
60, 100, 125%), a GIF loop of each concept and `all-concepts-loop.gif` / `-x4.mp4` with all four at once.
