# Federation cable rendering feasibility

Research date: 2026-09-27. Scope: analysis only; no production code, assets, or
rendering behavior changed. No new client experiment or benchmark was run.

## Evidence and current implementation

- Pinned stack: Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17.
- Design authority: `design/visual/AE2-Federation-Visual-Design-Spec-v0.2.md`,
  especially sections 5, 12, and 13. Preserve orthogonal Minecraft forms,
  transparent enclosure, one dominant cyan flow, quiet white collars, and a
  readable appearance without animation.
- `common/src/main/java/space/controlnet/ae2federation/client/CableBakedModel.java`
  registers 64 models, caches 64 immutable model-data objects, and selects by
  neighbor connection mask during chunk rebuild. It is already custom model
  behavior, but not a per-frame block entity renderer (BER).
- `tools/visual/build_assets.py:122` generates each mask as a composite:
  solid collars, translucent glass, and cutout streams. Glass spans 5..11,
  stream spans 6..10, and collar cross-sections span 4..12 model units.
  Internal faces and connected boundary caps are removed.
- `stream_u.png` and `stream_v.png` contain 16 frames of 16x16 pixels;
  metadata uses two ticks per frame with interpolation disabled. The stream
  pixels are fully opaque, including the low-brightness background, as required
  by the present validator. This explains the solid cyan prism appearance even
  though the outer enclosure is translucent. Streams already use full light
  metadata and disabled shading/AO; adding brightness alone will not fix this.
- Viewed the existing native screenshots `docs/art/screenshots-v07/07-connected-cable.png`
  and `14-six-way-detail.png`; these corroborate the strong prism silhouette.
- `CableVisualConnections` projects neighboring blockstates. It accepts Cable,
  Router, and the facing side of Provider; Endpoint and Bridge are excluded.
- `FederationCableBlockEntity` already exists. It maintains server-side topology
  ports, but supplies no per-cable client traffic/direction telemetry. A BER
  would not require introducing a new block entity type or a client ticker just
  to animate from client time.
- The design's collar interval is 16 MODEL UNITS, or one world block, not
  sixteen world blocks. Adjacent one-unit halves form one two-unit collar.
  `docs/art/README.md` explicitly resolves this wording.

## Feasible approaches

1. Improve textures and existing JSON geometry. Lowest implementation cost;
   animated textures still work with chunk-cached meshes. Useful for quieter
   glass highlights, less opaque-looking flow, and improved pulse patterns.
   Cutout cannot represent smooth partial transparency; changing alpha alone
   is insufficient. Shared atlas sprites do not provide independently timed
   per-cable animations. Deliberate UV phase offsets can still create variation.
2. Generate custom baked quads or use a custom geometry loader. Recommended
   foundation if geometry freedom is needed: shallow bevels, fractional model
   coordinates, carefully joined junctions, and a small number of internal
   ribbon surfaces. Keep the dominant single-volume visual instead of several
   equally bright wires. Cache by mask and render layer. Model geometry is not
   restricted to integer voxel cuboids, and collision/selection shapes are
   independent; check silhouette/selection agreement after shape changes.
   Static geometry alone cannot produce view-dependent reflection or continuously
   changing vertex positions in a cached chunk mesh.
3. Hybrid baked structure plus BER effect. Keep collars and base structural
   geometry baked; prototype only moving interior pulses in a BER. Use cached
   mask geometry and client time plus partial tick. Share render types/buffers;
   avoid per-frame topology traversal, allocations, and per-cable flushes.
   BER submissions are not necessarily one GPU draw call per cable, but their
   CPU work still grows with the number of rendered block entities. Add bounded
   effect distances and a persistent static baseline. Transparent BER content
   and chunk glass do not automatically receive one correct global sort order;
   test this before committing to the split. Moving the glass into the same
   effect pass may help local ordering but increases dynamic work and does not
   solve world transparency universally.
4. Dedicated shader/effect pipeline. Feasible for continuous UV motion, smooth
   density fields, and view-dependent edge reflection (a Fresnel approximation).
   Greater complexity: shader lifecycle/reload, vertex attributes or per-segment
   parameters, transparency passes, batching, and renderer compatibility.
   This is independent of geometry authoring: OBJ/custom quads alone do not add
   realistic glass or procedural flow. Real refraction, volumetric ray marching,
   bloom, and dynamic world lighting are separate features and not implied by
   a custom shader or emissive vertex lighting. Defer them initially.

NeoForge 21.1.250 source inspection confirms `RegisterShadersEvent` supports
custom shader registration and requires retaining the instance provided by its
load callback. `ChunkRenderTypeSet.of` rejects render types without a valid chunk
layer ID. Thus an arbitrary custom shader cannot simply be assigned to this
baked model through a JSON render-type name; use a supported separate rendering
path or explicitly integrate with the chunk renderer. The latter is a larger
compatibility commitment.

The pinned AE2 sources also contain `appeng/client/render/cablebus/CableBuilder`
and `CableBusModel`, useful implementation references for cable quad assembly.

## Proposed visual prototype and decision gates

- Start with a straight cable, one elbow, one T junction, and the isolated item.
  Preserve a square overall cross-section, white boundary collars and cyan
  identity. Test small bevels, understated glass edges, and limited internal
  surfaces with a continuous dim base and sparse brighter pulses. This is an
  artistic approximation of volume, not physical volumetric rendering.
- First compare an improved baked version against a hybrid dynamic version.
  Choose the simpler version if it achieves the desired appearance. Consider
  a dedicated shader only if smooth flow or view-dependent glass materially
  improves the comparison.
- For straight runs, establish a consistent axis, UV orientation, and phase
  continuity across block boundaries. World-axis phase is useful for straight
  runs but does not define path distance through elbows, branches, or loops.
  Treat junctions as a low-brightness common volume with bounded decorative
  pulses initially. Exact path-following effects need a separate path policy.
- Do not label decorative movement as actual transfer direction or throughput.
  Real activity would require defining measurable semantics and bounded server
  telemetry, particularly because these cables represent federation topology.
- Test 64 masks, three straight axes, attached device faces, chunk boundaries,
  neighbor changes, all item contexts, day/night, animation disabled, resource
  reload, world reload, and dedicated-server loading. Test translucent ordering
  against water, stained glass, intersecting cables, and several camera angles.
- Compare equivalent scenes with vanilla graphics modes and separately with
  intended optimization/shader mods. Compatibility remains unverified.
- Existing V07 software-GL evidence reports 17.67 ms mean / 20.22 ms P95 in the
  gallery and 49.76 ms mean / 53.14 ms P95 with 2,048 cables plus 128 devices.
  These are historical results for different scenes, not isolated cable cost
  or a forecast for new geometry. Run A/B profiles on identical hardware,
  camera, world, settings, and frame cap; include representative hardware GPUs.

## Implementation boundary and verification

Create a new art version rather than altering frozen V07. Update the generator
and version selection rather than hand-editing generated production JSON.
Current validation assumes integer cuboids, exact voxel union surfaces and
fully opaque animated textures. Custom meshes require replacing those specific
assertions with appropriate mesh/UV/bounds/seam checks while retaining mask,
connection, reproducibility, and resource integrity checks.

Future implementation verification commands, from the repository root:

```sh
pixi run --manifest-path tools/visual/pixi.toml validate
./gradlew :neoforge-1.21.1:build :neoforge-1.21.1:verifySharedJarContent --dependency-verification=strict --no-configuration-cache --no-daemon
python3 tools/blockbench/verify_art_versions.py
git diff --check
```

Expected: asset validation passes with all 64 masks and reproducible output,
Gradle reports BUILD SUCCESSFUL, art archive hashes remain intact, and no
whitespace errors. These checks do not establish visual or performance quality.
The existing validator can pass only after its geometry-specific expectations
are properly adapted to any chosen new representation.

## Primary references

- https://docs.neoforged.net/docs/1.21.1/resources/client/models/bakedmodel/
- https://docs.neoforged.net/docs/1.21.1/resources/client/models/modelloaders/
- https://docs.neoforged.net/docs/1.21.1/blockentities/ber/
- https://docs.neoforged.net/docs/1.21.1/resources/client/textures/
- Local pinned NeoForge 21.1.250 sources: `ChunkRenderTypeSet.java`,
  `RegisterShadersEvent.java`; AE2 19.2.17 sources: cablebus model classes.
- Repository records: `docs/art/README.md`, `docs/art/acceptance-v07.md`,
  `.omo/knowledges/visual-assets-v07.md`.

## Follow-up: dynamic rendering versus custom shaders

The user considers texture-only improvement primarily a fallback, is concerned
that a redesigned model could clash with Minecraft's style, and is interested
in localized dynamic rendering. No implementation has been requested yet.

Clarification: BER and a dedicated shader are independent choices. A BER can
submit moving quads, changing UV coordinates, colors and alpha through standard
Minecraft render types and shaders. It can alternatively use a custom shader.
Thus a moving pulse does not require custom GLSL, nor a redesigned exterior.
Keep square glass/collars and prototype confined internal effects first.
Retaining a bright opaque core could occlude those effects; the interior still
needs deliberate composition. Standard rendering does not provide volumetric
scattering or real refraction automatically.

Primary-source compatibility findings:

- Iris's **1.21.1 branch** explicitly documents conflicts between custom mod
  shaders and active shader packs, and recommends paths without custom shaders.
  https://github.com/IrisShaders/Iris/blob/1.21.1/docs/development/compatibility/core-shaders.md
- Sodium documents unsupported resource-pack replacements of internal shaders.
  This must not be generalized into a claim that every separate mod shader or
  standard BER is unsupported.
  https://github.com/CaffeineMC/sodium/wiki/Resource-Packs
- Embeddium describes both a rewritten terrain renderer and optimizations of
  entity/block-entity immediate rendering. Standard BER is a lower-coupling
  choice, not a compatibility guarantee.
  https://modrinth.com/mod/embeddium
- Modrinth API queried with game_versions=[1.21.1], loaders=[neoforge] confirms
  releases for Sodium, Iris and Embeddium. Returned examples: Sodium
  mc1.21.1-0.8.13-neoforge (uMOpc5uV), Iris 1.8.12+1.21.1-neoforge (t3ruzodq),
  Embeddium 1.0.15+mc1.21.1 (J7b96IEd). These are availability evidence, not a
  mutually compatible version set; honor each release's declared dependencies.
- Original Oculus listing does not list 1.21.1; prioritize Sodium plus Iris for
  this project's shader-loader test branch. Test Embeddium separately, not
  combined with Sodium/Iris.
  https://modrinth.com/mod/oculus

Recommended next direction, subject to user choice: cached existing exterior
plus internal BER using standard shaders; texture-only mode as an inexpensive
fallback; optional dedicated-shader enhancement only if justified later.
Evaluate vanilla NeoForge, Sodium alone, Embeddium alone, and a dependency-matched
Sodium/Iris installation with packs both disabled and enabled. Inspect moving
pulses, transparency/depth, resource reload and frame times. Test representative
packs individually; no runtime compatibility has been established by research.
