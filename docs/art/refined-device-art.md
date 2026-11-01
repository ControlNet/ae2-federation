# Textured laser and fragmented Quantum core

Third design round, 2026-10-26. The selected open laser and fragment-core design is now
[integrated into Minecraft](refined-device-runtime.md); the source-study details below describe the original delivery. The owner rejected the previous flat material treatment and white sphere,
selected a layered or fragmented spherical core, and confirmed that Federation accents must use the existing
blue-green palette. This round supersedes [the second proposals](device-alternatives.md).

## Delivery

Generated files are in `tools/blender/build/refined-devices-final/` (git-ignored).
`refined.blend` contains 12 editable scenes with packed images and animated core layers.

| File | Contents |
| --- | --- |
| `laser-refinement.png` | Open collimator and segmented-shroud alternatives |
| `quantum-refinement.png` | Open frame and fragmented core close-up |
| `artist-comparison.png` | Existing artist Beam Former and new laser at the same physical scale |
| `laser-detail.png` | Mechanical texture close-up |
| `connection-sheet.png` | Both devices connected to ordinary, 2x2 and 3x3 cables |
| `fragment-core.mp4`, `fragment-core.gif` | Four-second core loop, 30 rendered frames |
| `textures/texture-detail-sheet.png` | Enlarged pixel artwork samples |
| `verification.json` | Results from reopening and checking the saved Blender file |

The laser has a dark excitation chamber, four longitudinal rails, segmented focusing rings and a stepped muzzle.
Variant A exposes the optical structure; variant B adds directional shutters above and beside it.
The Quantum frame has twelve perimeter rails and eight corner anchors, with no panels or face-centre cages.
Its core has 22 outer and 12 inner solid curved fragments, visible gaps, a dark faceted nucleus and opposite layer
rotation. Local teal markings and rim lights preserve surface readability.

## Materials and cable fit

Mechanical faces use complete UV rectangles and purpose-drawn pixel textures at 16 pixels per block with nearest
sampling. The 117 diffuse textures have separate emission masks. Seams, stepped highlights, inset shadows,
couplings and small functional motifs are painted into the surface artwork. Curved core fragments have their
own UV artwork; the exact mechanical density assertion does not apply to their curved surfaces.

Accent colours come directly from `textures/part/cable/dense/collar.png`: `#6caebb`, `#477e93` and `#a2dce2`.
Darker core shades derive from that teal. Neutral metal colours also reference the original dense core and
large cable textures. There is no violet energy palette. Studio lighting and colour management affect the
displayed values; the source palette is recorded in `textures/textures.json`.

The cable references use production geometry and original texture UVs, including the artist's bundled cable
rings. New internal device beams use the reference carrier geometry, tinted to the source teal palette.
Their measured core radii and contact axes match all three cable sizes. Quantum internal carriers terminate
at the core centre. The connection scenes show two axes to keep the core visible.

| Cable section | Laser body | Quantum body |
| --- | --- | --- |
| 1x1 | 1x2x1 | 3x3x3 |
| 2x2 | 2x4x2 | 4x4x4 |
| 3x3 | 3x6x3 | 5x5x5 |

Dimensions above use Blender X/Y/Z. These are design assets: production resource files have not been replaced.
Runtime mesh export, animation, beam rendering and in-game transparency/occlusion still require integration.
Blender carrier rendering is a preview, not a validation of the Minecraft renderer.

## Open and verify

Run from the repository root:

```sh
blender tools/blender/build/refined-devices-final/refined.blend
blender --background tools/blender/build/refined-devices-final/refined.blend \
  --python-exit-code 1 --python tools/blender/verify_refined_devices.py
```

Expected: `REFINED_VERIFY_OK 4164 UV faces; 34 fragments per core; 6 cable cases`.
The verifier checks source hashes, diffuse texture hashes, UV coverage and mechanical density, packed images,
finite nondegenerate geometry, fragment counts and motion, device light colours, and cable/core alignment.

## Reproduce into a fresh directory

The generators refuse to overwrite an existing texture manifest or Blender delivery. Choose a new output
directory if `refined-devices-rebuild` already exists. Python artwork tools use the approved visual pixi environment.

```sh
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/build_refined_device_textures.py \
  tools/blender/build/refined-devices-rebuild/textures
blender --background --factory-startup --python-exit-code 1 \
  --python tools/blender/render_refined_devices.py -- \
  --output tools/blender/build/refined-devices-rebuild --resolution 1200
blender --background tools/blender/build/refined-devices-rebuild/refined.blend \
  --python-exit-code 1 --python tools/blender/verify_refined_devices.py -- --animate
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/compose_refined_devices.py \
  tools/blender/build/refined-devices-rebuild
ffmpeg -n -v error -framerate 7.5 \
  -i tools/blender/build/refined-devices-rebuild/core-animation/%03d.png \
  -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart \
  tools/blender/build/refined-devices-rebuild/fragment-core.mp4
```

Expected generation signals: `REFINED_TEXTURES_OK 117`, `REFINED_DEVICES_OK 12`,
`REFINED_VERIFY_OK`, `FRAGMENT_CORE_ANIMATION_OK`, and `REFINED_SHEETS_OK`.
The render generator defaults to OptiX; `--device cpu` is available for still generation.
