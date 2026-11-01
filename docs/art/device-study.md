# Quantum Cube and Beam Converter Blender study

This first-round proposal has been superseded by the owner's laser-emitter and open/glass direction.
See [the current alternatives](device-alternatives.md) for the latest delivery and cable compatibility studies.

Delivered 2026-10-26. This is an editable design proposal, with current-asset references rendered first.
The owner's additional direction is incorporated: the Quantum Cube is an open frame containing a high-energy
sphere, and cable centre beams converge on that sphere. The proposal does not replace production resources.

## Review files

All generated files live in the ignored `tools/blender/build/device-study-v2/` directory:

| File | Contents |
| --- | --- |
| `all.blend` | Eight current-asset reference scenes and seven editable design scenes; packed reference textures |
| `reference-sheet.png` | Current Router, Provider, Endpoint, Switch, 1x1/3x3 Beam Formers, and the two temporary assets |
| `quantum-overview.png` | Open frame and six-axis connection demonstration |
| `quantum-energy.mp4`, `quantum-energy.gif` | Four-second energy study: evolving sphere, surface discharges, inward packets |
| `converter-overview.png` | Three sizes and a rear inspection view |
| `size-sheet.png` | Quantum 3/4/5 and Converter 1x1x2 / 2x2x4 / 3x3x6; each framed separately |
| `quantum-front.png` | Axial inspection of the connected Quantum Cube |
| `all-manifest.json` | Imported resource digests, cable Java source digests, Blender version and scene list |
| `verification.json` | Saved-file geometry, convergence, animation and resource-integrity checks |

Open the scene from the repository root:

```sh
blender tools/blender/build/device-study-v2/all.blend
```

The file opens on the 3x3x3 Quantum Cube. Choose another scene in Blender's scene selector.
The frame, sockets, supports, energy sphere, optical channels and converter panels are independently editable
objects grouped in named collections. Press Space to preview the 120-frame, 30 fps energy timeline.
No add-on, trusted Python handler or external texture path is required to open the saved scene.
The MP4/GIF samples every fourth frame at 7.5 fps.

## What the reference renders establish

The current artist assets are the primary reference. The earlier
[visual specification](../../design/visual/AE2-Federation-Visual-Design-Spec-v0.2.md) predates their deeper frames
and stepped faces. In particular:

- The Router has bright ceramic edge rails, violet-grey grooves, and a cyan four-way terminal.
- Provider and Endpoint share the same frame language, with cyan Federation contact geometry and violet ME faces.
- The 3x3 Beam Former uses a deep dark face, stepped relief and angled components. Its geometry and pixel
  materials supply more useful dimensional references than the original flat-surface guideline.
- Narrow colour bands and discrete structural steps carry the detail. The proposal uses that hierarchy without
  random surface noise, metallic bevels, bolts or industrial weathering.

The references import production JSON geometry and original PNGs with nearest sampling, using the existing
Blender importer. The reference Quantum image is the current **unformed single block**; it is not a rendering of
the existing formed connected-face geometry. Similarly, the temporary Converter reference is one block.

## Quantum Cube: convergence frame

The 3x3x3, 4x4x4 and 5x5x5 proposals share twelve white edge rails and eight violet-inset corner anchors.
Each face has a central square cable socket, held by narrow cardinal supports. The four corner openings of each
face remain empty so the central sphere can be seen obliquely.

The six socket regions correspond to the existing 1x1, 2x2 and 3x3 face-centre port regions. Their visual lips
are slightly inset from those regions. Six straight optical axes meet at the body's exact geometric centre.
The sphere diameter is 48% of the body's edge: 1.44, 1.92 and 2.40 blocks. Cyan/white energy, violet-blue
surface variation and short moving discharges make the sphere the focus; violet inserts identify the frame.
Packets move inward on all six internal axes. The study shows an energized all-six-port state, not binding,
power or per-port connection logic.

The connection scene uses shell geometry freshly exported from the current Java cable builder. Its internal
light is the proposed simple carrier used to explain convergence; it is not a parity rendering of the shipped
R / PLASMA cable animation. The medium and large socket sizes are modelled, but connected 2x2 and 3x3 cable
bundles are not shown in this delivery.

## Beam Converter: conversion stack

The supported bodies are 1x1x2, 2x2x4 and 3x3x6. A single cyan terminal covers the front end; repeated one-block
cells establish the length on the other four longitudinal faces. Each cell has a white cover, dark chamber,
three short vanes and a cyan axial channel. The rear has a compact energy-acceptor motif.

Small amber pads are a proposed FE material accent. They are static markings, not a claim of a charge indicator.
They occupy little area so the existing white, violet-grey and cyan palette still dominates. The front and rear
have different geometry and remain recognizable without animation. Creative Converter variants and unformed
inventory models are outside this first formed-device study.

## Reproduce and verify

Requirements are the existing Blender 5.2.1 LTS installation, JDK 21, cached Minecraft/AE2 resource jars, and the
approved visual pixi environment. Blender uses its bundled Python; the exporter uses standard-library Python.
The media composer uses Pillow only through `tools/visual/pixi.toml`. No packages are installed into system Python.

Choose a fresh output directory; the generator refuses to overwrite an existing blend or render:

```sh
blender --background --factory-startup --python-exit-code 1 \
  --python tools/blender/device_study.py -- \
  --output tools/blender/build/device-study-review --resolution 1000

blender --background tools/blender/build/device-study-review/all.blend \
  --python-exit-code 1 --python tools/blender/verify_device_study.py -- --animate

pixi run --manifest-path tools/visual/pixi.toml python tools/visual/compose_device_study.py \
  tools/blender/build/device-study-review

ffmpeg -v error -framerate 7.5 \
  -i tools/blender/build/device-study-review/animation/%03d.png \
  -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart \
  tools/blender/build/device-study-review/quantum-energy.mp4

git diff --check
```

Expected signals: `DEVICE_STUDY_OK`, `DEVICE_STUDY_VERIFY_OK`, `DEVICE_ANIMATION_OK`, `DEVICE_SHEETS_OK`;
FFmpeg and `git diff --check` exit successfully. The generator also validates all 4096 cable connection sets.
Use `--device cpu` on the generator if OptiX is unavailable. The optional animation verifier chooses OptiX when
available and otherwise uses CPU. To check an existing delivery without generating animation, omit `--animate`.
The verifier checks actual reopened scenes, including six distinct axes landing at the sphere, inward packet
movement, finite non-degenerate meshes, packed file textures, and unchanged production-resource/source hashes.

## Integration boundary

These are Blender geometry and material studies, not exported Minecraft models. The frame and converter use
explicit coloured components; final UV atlases have not been authored. Blender's procedural sphere, transparent
beam shoulders, local light and compositor glow need a game renderer design. Static frame geometry should be
baked separately from dynamic energy effects, with distance limits and one logical energy renderer per formed
body. Renderer cost has not been measured in Minecraft.

The existing Quantum Cube still forms from a **solid** 3/4/5 cube, and
`DeviceStructureBakedModel` supplies its connected appearance. Showing this open frame requires replacing that
appearance and suppressing interior member surfaces. Whether players should instead build only a hollow frame
is a separate gameplay decision. Neither assembly rules nor collision/occlusion rules were changed here.
The converter's proposed relief also needs a bounds and occlusion review when ported. Production textures,
models, blockstates and Java files remain unchanged.
