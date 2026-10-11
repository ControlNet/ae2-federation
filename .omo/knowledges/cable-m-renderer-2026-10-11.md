# M optical beam production integration (2026-10-11)

**Visual status correction:** the user rejected the initial preview described below. Compilation and geometry
tests did not constitute visual acceptance. See the correction section at the end for the current implementation.

The user approved the Blender M load variant and requested implementation in the mod. External cable shell geometry
and textures are fixed. Capacity mechanics remain out of scope. Runtime rendering stays always enabled without a
command, config or JVM switch, as required by the earlier default-enabled decision.

Production sources: `CableBeamStyle`, `CableBeamMesh`, `CableFlowRenderer`. The default uses explicit visual load 0.6,
not inferred traffic. Load increases carrier width, pulse envelope, density and emission; strictly above 1 changes to
red with a 32-tick alarm breath. All 64 masks have cached centerlines and three distance detail levels. Axial phase
is world anchored, bounded and continuous across negative-coordinate seams. Elbows crossfade adjacent axis phases;
this is decorative animation, not packet routing. No capacity counters or synchronization were added.

The new draw method uses vanilla white texture, full-bright normal-alpha core and additive gradient shoulders.
Complete each RenderType pass before requesting the next VertexConsumer: the shared buffer can end when switching.
The emissive shader discards on texture alpha before multiplying vertex alpha, so soft vertex-alpha gradients work.
Blender bloom is approximated rather than reproduced exactly. Shell builder/assets were not edited.

Native preview lives in testmod package `space.controlnet.ae2federation.test.client`. Never put it in the production
client package: Java modules reject the split package. The test-only harness reflects into the package-private shell
builder without expanding production API visibility. It uses real atlas textures and culling, with a fully lit
preview pass, and calls production beam drawing. It is an offscreen GPU preview, not an in-world/FPS test.

Verification completed:

```bash
./gradlew :neoforge-1.21.1:build :neoforge-1.21.1:verifySharedJarContent --offline --no-configuration-cache
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s '-screen 0 1280x720x24 +extension GLX' \
  ./gradlew :neoforge-1.21.1:runBeamPreviewClient --offline --no-configuration-cache
git diff --check
```

Build successful: 552 tests, zero failures/errors/skips, including five new beam tests. Native client emitted
`AE2F_BEAM_PREVIEW_OK`, six loads at two clock positions, isolated/elbow/T/six-way previews and shell-only baseline.
Outputs are Git-ignored under `neoforge-1.21.1/build/beam-preview/`; the assembled `m-native-loads.png` adds labels
and crops empty margins only. Production JAR excludes the native preview class.

Local artifact: `neoforge-1.21.1/build/libs/ae2federation-0.0.6.jar`.
SHA-256 at verification: `070390a0946055b1084ae06c41578589858f7bcd3e400ea21f7d2286a45c7995`.
No commit, PR, push, capacity implementation, world stress profiling or shader-pack compatibility claim.
See `docs/art/cable-m-renderer.md` for parameter mappings and reproduction details.

## Corrected native optical material

Root cause confirmed from the locally installed vanilla shader source: `rendertype_entity_translucent_emissive.vsh`
still calls `minecraft_mix_light`. Its directional shading dims the core and glow even with `FULL_BRIGHT` UV light.
Rendering a beam-only diagnostic showed the loss before shell blending. Use the vanilla beacon beam shader with
BLOCK vertex format for both core and additive glow; it preserves unlit vertex color, world fog and depth testing.
The shell builder and textures remain unchanged. Do not solve brightness by disabling depth or painting over screenshots.

The M intermediate shoulder envelope was restored with radius `halo * .62 * breath + .35 * amplitude * wave`.
Native gradient weights and cyan saturation were calibrated against the actual approved Blender `c_medium-close.png`
in the sibling worktree's `tools/blender/build/m-load-beams/`. That is the actual reference location (not root build).
Close sampling is 64, medium 24, distant 4 segments. Near straight cost 3,584 vertices, six-way 10,752; no FPS claim.

Native testmod now asserts visible cyan brightness (`AE2F_BEAM_BRIGHTNESS_OK pixels=4338` for the closer preview),
captures an explicit opaque test plane occlusion fixture, and exports 64 unique frames each for 60% and 125%.
The rejected v1 image had zero bright-cyan pixels under the same threshold. This metric catches lost emission only;
it is not aesthetic acceptance. Same-camera before/after pixel changes are confined to (176,190)-(1024,230), the beam
band; shell pixels outside that band are unchanged. Renderer changes, not postprocessing, produce the new glow.

Evidence in ignored `neoforge-1.21.1/build/beam-preview/`:
- `m-before-after.png`: same camera, shell, tick and exposure; rejected image archived in `rejected-v1/`.
- `m-reference-native.png`: approved Blender medium and native medium, explicitly different scene/camera lighting.
- `m-native-loads-v2.png` and current `m-native-loads.png`: corrected six-load sheet.
- `m-native-motion.mp4`: 60% above, 125% below, 3.2-second native animation cycle.
- `opaque-occlusion.png`: test-only plane hides both core and halo.

`tools/visual/compose_cable_beam.py` uses the approved pixi environment and only crops empty margins, labels and
assembles native renders; it adds no glow or color adjustment. Reference image scaling is labeled separately.
Full build and shared-JAR verification passed again: 552 tests, zero failures/errors/skips. Corrected JAR SHA-256:
`8e5fd496ee7209d3407dcd7f1dc7c127c1f636ec015c229916c9b4042b2ae416`.
These remain offscreen native client previews; no in-world shader-pack acceptance or performance claim was made.

## Main repository integration

The user subsequently requested merging the renderer into the main repository. The renderer changes already lived
in the main `ae2-federation` worktree on `dev`, so integration is a scoped commit there rather
than a merge from `design/blender-cable-preview`. That design worktree has untracked Blender tooling and no commits
ahead of `dev`; it is preserved separately. Unrelated statistics/design notes in the main worktree are excluded.
The full build and shared-JAR check passed again on the current main branch before committing. Staged secret scan
and whitespace checks passed. No remote push or PR is part of this integration.

## Thicker beam + in-shell fog (2026-10-11, user request)

User feedback: the beam was too thin to tell whether it existed; wanted it thicker everywhere and a fog inside the
shell that spreads the beam colour through the cable. Standing rule from the same request: every change ends with a
before/after sheet of real in-game renders.

- Root cause of invisibility is geometric, not shading: each 12-voxel shell face shows the interior only through a
  6-voxel glass window (texture alpha 75, the rest of the face a 3-voxel opaque frame). The beam is 6 voxels deep,
  so at 30-60 degree downward views a thin centre line hides behind the frame between top and side windows
  (`probe-west.png`: only a sliver at the window's top edge). Thickening alone cannot fix all angles; the fog can.
- Render order checked in 1.21.1 `LevelRenderer`: BER buffers end (`endLastBatch`) before `renderSectionLayer
  (translucent)`, so the beam/fog draw first and the glass blends over them (about 29% attenuation). No depth issue.
- New mapping: core `.03+.035u`, halo `.08+.085u`, pulse amplitude `.035u`. Core is a two-colour gradient strip.
  Fog: three nested soft-edged volumes per arm (half-widths .29/.20/.11, alpha .15/.19/.24, modulation
  `.65+.7*pulse`), additive in the glow pass; distant LOD keeps the two outer volumes unanimated.
  First fog trial (alpha .05/.07/.10) was barely visible in daylight; ~2x was needed.
- In-world A/B rig: `function hero_scene:beam` in `tools/visual/build_hero_scene.py` (origin x+45 from the base),
  `tools/visual/compose_before_after.py <before> <after> <out>`. Views used: overview `649.5 72.5 907.5 facing
  641.5 68.0 899.5`, close `638.2 70.4 902.4 facing 637.0 68.5 900.5`, side `638.0 69.6 903.5 facing 638.0 67.9
  900.5`, junction `645.5 71.6 903.8 facing 643.0 69.8 900.5`; night = same at `time set 18000`.
- Capture pitfalls: wait ~45 s after join and ~9 s per teleport or every shot lags one view and the HUD may show;
  keep `sendCommandFeedback false`. Never `echo stop > cmd` unless the server is running: the FIFO keeps the line
  and stops the NEXT server at start. `pkill -f` patterns must use `[x]yz` so they don't match the invoking shell.
- Evidence (ignored): `build/beam-compare/` with `before-full/`, `after-v3/`, `cable-beam-before-after.png`,
  jars `before.jar` (HEAD 54cf2ba7) and `after-v*.jar`. Pulse phase differs between shots (decorative animation).
