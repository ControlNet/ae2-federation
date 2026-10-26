# Laser Converter and open/glass Quantum alternatives

Historical proposal set. The owner's later texture and core feedback is addressed in
[the textured laser and fragmented Quantum refinement](refined-device-art.md).

Second design round, 2026-10-26. This supersedes the first
[device study](device-study.md) as the current proposal set. The owner rejected the Converter's battery-like
panel array, requested a laser-emitter appearance and multiple options, and asked for Quantum faces to be
fully open or glass. Ordinary and bundled cable material compatibility is part of this round.

## Delivered proposals

Generated files: `tools/blender/build/device-alternatives-final/` (git-ignored).

| ID | Proposal | Main geometry |
| --- | --- | --- |
| L1 | Split Rail | Four longitudinal rails, three exposed collimation stages, exit nozzle |
| L2 | Octagonal Barrel | Stepped faceted barrel and a projecting muzzle |
| L3 | Crystal Resonator | Faceted resonator crystal, two glass cavity walls, lower ceramic keel |
| L4 | Reflector Focus | Broad concave reflector, exposed focus and forward lens |
| Q1 | Open Edge Frame | Twelve thin edges and eight corner anchors; no face panels |
| Q2 | Glass Containment | Same sparse frame with cable-matched glass surfaces |
| Q3 | Corner Glass | Glass surfaces held only by corner anchors and short fingers |

Quantum face-centre supports and permanent socket cages are removed. Small receiver rims appear only where
cables connect, attached to the cable ends. The sphere remains central. Two connected axes are shown in the
connection studies so the sphere stays visible; the existing six-face port semantics are not changed.

Laser standalone proposals show the optical muzzle directly. A cable-matched square transition flange appears
in connected scenes. This is proposed connection-dependent art; the runtime does not implement it yet.

Every proposal has a standalone view and three connected sizes: 28 scenes and stills in total.

| Cable section | Converter body | Quantum body |
| --- | --- | --- |
| Ordinary 1x1 | 1x1x2 | 3x3x3 |
| Bundle 2x2 | 2x2x4 | 4x4x4 |
| Bundle 3x3 | 3x3x6 | 5x5x5 |

## Review media

- `laser-options.png`: four standalone laser alternatives under the same camera, at 2x2x4.
- `quantum-options.png`: three standalone Quantum alternatives at 4x4x4.
- `laser-connections.png`: all four laser alternatives against all three cable sizes.
- `quantum-connections.png`: all three Quantum alternatives against all three cable sizes.
- `connection-highlights.png`: L3 and Q1 with each cable size, for a shorter material comparison.
- `L1-hero.png` through `Q3-hero.png`: individual full-resolution proposals.
- `<ID>-cable-1.png`, `-2.png`, `-3.png`: individual connection views.
- `alternatives.blend`: editable meshes, native materials and packed original cable textures.
- `manifest.json`, `verification.json`: provenance and reopened-file checks.

The connection sheets frame each assembled scene independently. They are not a common-scale size diagram.
Open the saved file with:

```sh
blender tools/blender/build/device-alternatives-final/alternatives.blend
```

Use Blender's scene selector to choose `Proposal / <ID>` or `Connection / <ID> / <section>`.
Meshes are grouped into structure, optics and current-cable reference collections. The Quantum sphere retains
its editable native shader/arc animation. Cable R light is a static 60%-load sample in these still studies.

## Material and geometry matching

`device_cable_reference.py` reads the original images and uses constant texel coordinates in their shader nodes.
It does not substitute a newly chosen near-white or new glass colour:

| Surface | Existing source |
| --- | --- |
| White rail | `part/cable/dense/core.png`, texel (4,6), RGB 242/242/242 |
| Outer silver | Same image, (2,6), RGB 213/214/220 |
| Dark stripe | Same image, (3,6), RGB 77/77/103 |
| Glass | Same image, (6,6), RGBA 204/255/255/75 |
| Cyan and highlight | `part/cable/dense/collar.png`, (3,6) and (4,6) |
| Bundle metal | `block/large_cable/large_cable.png`, (6,14) |

Glass is a single surface per face, with the source alpha 75/255. Connected Quantum glass faces are split around
the cable opening. No extra face-thick glass boxes are stacked over the port. The large glass surfaces look more
prominent than the narrow cable windows because they cover a larger area; the source material is the same.

Ordinary shells come from the production Java exporter. The first cell uses connection bits 7, keeping its
10/16-block dense arm at the machine; subsequent cells use their proper full cable connections. The port is not
assumed to be the full 12/16-block cable shell width.

The 2x2 and 3x3 shells are reconstructed by the existing `BundleShellLayout` texel rule, at two voxels inside
the bundle boundary with three-voxel rails. They retain their closed end caps and one dense contact per member.
The artist's `large_cable_straight.bbmodel` and `huge_cable_straight.bbmodel` are imported with original UVs and
rotations; the huge project's (16,24,16)-voxel offset matches `export_large_cable_rings.py`. The 3x3 ring is not a
scaled 2x2 ring. The ring exporter check confirms the checked-in Java tables match these projects.

Light uses the repository's R/LITE and R/PLASMA adapters, including the 3x3 core's 4/3 scaling and glow's 1.5 scaling.
The same adapter generates the device's internal carrier. Reopened meshes are checked for equal core radius
across each interface, and Quantum carriers end at the exact sphere centre. Blender transparency/additive
blending still differs from Minecraft; these are geometry/material studies, not runtime GPU validation.

## Reproduce and verify

Blender uses its bundled Python. The Java exporter and ring-table check use standard-library Python; Pillow
composition uses the approved visual pixi environment. Choose an unused output directory:

```sh
python3 tools/blockbench/export_large_cable_rings.py --check

blender --background --factory-startup --python-exit-code 1 \
  --python tools/blender/device_alternatives.py -- \
  --output tools/blender/build/device-alternatives-review --resolution 1100

blender --background tools/blender/build/device-alternatives-review/alternatives.blend \
  --python-exit-code 1 --python tools/blender/verify_device_alternatives.py

pixi run --manifest-path tools/visual/pixi.toml python tools/visual/compose_device_alternatives.py \
  tools/blender/build/device-alternatives-review

git diff --check
```

Expected: ring check exits 0; the cable exporter validates all 4096 connection sets; generation prints
`DEVICE_ALTERNATIVES_OK 28`; reopened verification prints `DEVICE_ALTERNATIVES_VERIFY_OK 28 scenes, 21 connection checks`;
composition prints `DEVICE_ALTERNATIVE_SHEETS_OK`; `git diff --check` is silent.
Use `--device cpu` if OptiX is unavailable. `--only L1 Q1` can generate a subset for editing, but the full comparison
composer expects all seven proposals. Existing blend/render files are protected against replacement.

Verification inspects actual saved meshes: finite/non-degenerate faces, packed images, material sampling links,
opaque frame texels versus the original glass alpha, supported body dimensions, 10-pixel ordinary contacts,
bundle ring/contact counts, equal carrier radii, sphere convergence and unchanged production-resource hashes.

## Remaining production work

The deliverable is a set of Blender alternatives. No candidate is selected for production and no game resources
or behavior were changed. Final device UV atlases, unformed/item models, connection-dependent flanges, assembled
model visibility, collision/occlusion and dynamic energy rendering still require implementation after design
selection. Bundle shell and light adapters support these straight presentation cases; they are not an exporter
for arbitrary runtime networks. Quantum's existing solid multiblock assembly rule remains unchanged.
