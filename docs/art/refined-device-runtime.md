# Blender device artwork in Minecraft

The open laser converter and open-frame Quantum Cube with a layered teal fragment core are integrated into the
Minecraft 1.21.1 / NeoForge runtime. This implements the selected direction from
[the Blender refinement](refined-device-art.md).

## Shipped behaviour

| Cable section | Converter: front width x height x length | Quantum Cube |
| --- | --- | --- |
| 1x1 | 1x1x2 | 3x3x3 |
| 2x2 | 2x2x4 | 4x4x4 |
| 3x3 | 3x3x6 | 5x5x5 |

- The ordinary converter's 1x1x2 body and its single blocks use the artist's models instead (2026-10-30, see
  [operating power](../features/operating-power.md)); the other converters use the open collimator design. The creative variant has its own purple surface/emission atlas and
  purple internal carrier; ordinary converters and Quantum Cubes retain Federation teal. All six facings rotate both the chassis
  and internal carrier. FE is exposed only by members on the rear exterior plane, on their outward rear side.
  Front, sides, internal faces and unsided queries expose no FE input. Cached old input objects reject insertion after
  rotation or removal. Rear input still fills the whole body's existing shared buffer.
- Quantum Cubes have twelve perimeter rails, textured anchors, 22 outer curved fragments and 12 inner fragments.
  The layers counter-rotate. Connected ordinary and bundled cable carriers continue to the core centre; the actual
  cable beam renderer supplies their geometry and alarm/load style, including the separate 3x3 core/glow scales.
- The palette and pixel artwork come from the reviewed Blender assets. Surface and local emission use separate
  512x512 nearest-sampled atlases. Purple is reserved for the creative converter; the Quantum core stays teal.
- Unformed creative converters, Quantum Cubes and their items use native six-face cube models with dedicated opaque 16x16 pixel textures.
  They render independently of the custom multiblock model wrapper. Creative single blocks also have purple accents
  and an infinity mark. Formed static meshes are clipped
  into each member's block cell with interpolated UVs, then cached and baked into the chunk mesh. The dynamic renderer
  draws only the core and optical carriers, once per structure from its lowest member.
- Formed open devices retain neighbouring faces, transmit skylight and do not cast the old solid cuboid shadow.
  Unformed construction blocks retain normal opaque block lighting. Resource
  reload rebuilds both the baked mesh cache and dynamic mesh snapshot.

Construction, recipes, capacity, binding and collision semantics remain the existing solid-volume multiblock rules.
The open appearance does not make the occupied volume walk-through. Core rotation is visual and does not indicate
whether a remote Quantum partner is bound; Jade remains the source of binding status. Lighting is native Minecraft
lighting, not Blender studio lighting or a bloom shader.

## Source files

- `tools/visual/build_device_block_textures.py`: draws standalone cube faces and derives the creative purple atlases.
- `tools/visual/pack_device_atlas.py`: packs the reviewed diffuse textures and emission masks, with padded edges.
- `tools/blender/export_refined_devices.py`: exports evaluated Blender geometry and explicit UVs, with a strict
  device-material allowlist. Studio floor, cameras and lights are excluded.
- `assets/ae2federation/device_meshes/`: six static size meshes and three core-layer meshes, readable by resource packs.
- `RefinedDeviceMesh`: clips polygons with continuous UVs; coplanar boundaries have one owner.
- `RefinedDeviceBakedModel`, `RefinedDeviceBody`: chunk mesh assembly from synchronized formation state.
- `RefinedDeviceRenderer`: rotating core and cable-to-core carriers, with structure-sized frustum bounds.

The old recoloured converter/cube PNGs, their generator and the obsolete Router-parity test were retired.

## Build and verification

Run from the repository root:

```sh
./gradlew :neoforge-1.21.1:build --dependency-verification=strict --console=plain
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/validate_assets.py
python3 tools/dev_gametests.py \
  beamconvertershapes beamconverterlinksthroughitsfront beamconverterturnswithawrench \
  beamconverterbodysharesonebuffer beamconverterrearportseveryorientation \
  operatingpowerrunsadomain operatingpowerneedsamatchingconverter operatingpowerconvertersaddup \
  operatingpowerfeedsstatistics operatingpowergaugeholdssteady \
  quantumcubeshapes quantumcubelinksinsideitsfacecentres quantumcubepairbindswithamemorycard \
  quantumcubepairlinksacrossdimensions quantumcubepairneedsitssizeofconverter --ci
```

Observed after the standalone/creative correction: build successful, 799 unit tests with no failures or skips, and
asset validation passed. The two resource regression tests first failed against the previous assets, then passed
with native cube faces and distinct purple atlases. The preceding runtime integration also passed 15/15 GameTests
in one batch. The orientation test covers all 18 size/facing combinations and is included in the scenario
manifest. Geometry tests check clip area, UV continuity, facing transforms and boundary ownership; the resource test
rejects non-finite geometry, out-of-range UVs and non-device geometry outside the core's unit radius.

The development JAR is `neoforge-1.21.1/build/libs/ae2federation-0.0.10-dev.jar`.

## Real-client review

```sh
./gradlew :neoforge-1.21.1:runRefinedDeviceClient \
  -PrefinedDeviceOut=build/refined-devices-standalone --console=plain
```

This testmod-only run creates its own flat review world in `neoforge-1.21.1/run-refined-devices/`; it does not open a
player world. It constructs all three sizes with real ordinary/2x2/3x3 cable connections, vertical converters and
unformed blocks. It also captures native inventory icons and first-person held items for all three blocks,
plus a side-by-side comparison of formed ordinary and creative converters. The largest bodies straddle a chunk boundary. Creative converters power the review domains.
The first run removes/replaces a Quantum member and reloads resources. Running the same command again reopens the
saved world and captures it without rebuilding. Expect `REFINED_DEVICE_SCENE_OK`, automatic exit and screenshots in
`first-run/` or `reopened/`. The run fails if the final server-side formation check did not pass.

Both the first run and saved-world reopen reported `REFINED_DEVICE_SCENE_OK` and exited successfully.
The review uses native Minecraft rendering without external shaders. Third-party shader-pack rendering and large
installations have not been profiled as part of this change.

## Re-export the reviewed artwork

The saved source study is `tools/blender/build/refined-devices-final/refined.blend`. Rebuild the source study with
its documented generator first if it is absent. Exporting replaces the generated runtime meshes and atlases:

```sh
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/pack_device_atlas.py \
  tools/blender/build/refined-devices-final/textures
blender --background tools/blender/build/refined-devices-final/refined.blend \
  --python-exit-code 1 --python tools/blender/export_refined_devices.py
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/build_device_block_textures.py
```

Expect `DEVICE_ATLAS_OK 118 512` and `DEVICE_MESH_EXPORT_OK`. The original review snapshot's source-hash verifier
predates runtime integration, so its unchanged-production-assets assertion no longer applies to that historical
snapshot. Use the runtime checks above for the shipped assets.
