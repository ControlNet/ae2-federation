# Switch white backing correction

User requested the background between Switch modules match the original white
single-block material. Inspecting the original model and texture confirmed the
prior infill used dark atlas pixel `(3, 1)` (RGB 77, 77, 103), while the original
body includes white pixel `(2, 2)` (RGB 242, 242, 242).

The new optional `white-backed` style changes only infill UVs to the original
white pixel. All V3 socket openings, walls, cyan contact meshes, purple modules,
frame and small junction grooves remain identical. This also preserves the
raised Router correction. No production model or texture was edited.

Validation: the existing 16 geometry tests pass; direct old/new mesh comparisons
for 1x1x1, 1x1x4, 2x1x2 and 3x3x3 assert that only infill UVs differ. The raised
Router is identical with either Switch style. Python compilation and whitespace
checks pass. Rendering is performed by `tools/multiblock/render_switch_white.py`
into worktree `build/multiblock-devices/switch-white-v4/`, using the same cameras
and lighting for old/new and original-single-block comparisons. Scope remains
offline prototype geometry, not Minecraft runtime integration.

All three renders completed and were visually inspected. The white field now
matches the original block's body/frame palette; socket sidewalls remain visible.
PNG dimensions and saved Blender scene were verified. The render report confirms
source model and texture hashes remain unchanged.
