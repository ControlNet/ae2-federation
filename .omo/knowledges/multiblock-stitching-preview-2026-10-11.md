# Connected Router/Switch geometry preview

Implemented the user's requested first stitching preview in the isolated
the sibling `ae2-federation-multiblock` worktree on `design/multiblock-devices`.

## Scope and implementation

- `tools/multiblock/geometry.py` reads the actual 51-element Router and 73-element Switch models.
  It validates complete solid cuboids, retains only global corner/edge assemblies, extends the
  frame across seams, and generates exterior panels using the original texture's center pixels.
- The narrow borderless extensions sample edge pixels; the central motifs keep their original
  scale. The Router's raised face geometry is retained. No substitute texture or placeholder
  model was introduced, and production resources were not modified.
- `tools/multiblock/render.py` uses installed Blender 5.2.1 and its bundled Python, with nearest
  texture sampling, original UV rotations for retained components, and packed source textures.
- Generated images, quad JSON, render reports/logs, and the editable `.blend` live in the
  worktree-root `build/multiblock-devices/`, as requested by the user.
- This is offline geometry implementation, not Minecraft assembly or client rendering integration.
  Do not describe the images as in-game evidence or the gameplay multiblock feature as finished.

## Verification and visual findings

`python -m unittest discover -s tools/multiblock -v` passes eight tests: complete-cuboid validation,
eight global corners, exterior-cell coverage, exact panel area on all six orientations, correct
skin depth, UV/geometry bounds and nonzero faces, deterministic translation, and real asset shape.
These are geometry fixtures, not simulated Minecraft integration results.

The Router and Switch 3x2x2 comparisons and the 1x1x4 preview were visually inspected. The Router's
cyan details form a connected grid within one frame. The Switch exposes broad pale panels between
purple motifs while retaining cyan perimeter details. This difference comes from existing art;
new panel artwork remains an optional later visual decision.

The first front orthographic preview had a staging floor intersecting the lower half-block because
the body was centered vertically. Fixed the renderer to hide the floor in front inspection scenes
and re-rendered that image; this was a presentation fix, not a mesh modification.

Asset SHA-256 values are captured before and after rendering and must match. Eight tests, Python
syntax compilation, and `git diff --check` are the relevant checks; no Java source changed and no
Minecraft runtime test was claimed. Reproduction commands and artifact names are documented in
`tools/multiblock/README.md`.
