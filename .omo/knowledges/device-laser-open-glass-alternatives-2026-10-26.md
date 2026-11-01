# Owner correction and device alternatives, 2026-10-26

This supersedes the first-round preferences in `device-blender-study-2026-10-26.md`.

- Owner rejected the panel-array Beam Converter as ugly and battery-like. Requested a laser-emitter appearance
  and multiple designs. Do not return to the repeated battery-cell exterior.
- Owner found the Quantum face structures too closed. Remove central cross supports and permanent face sockets;
  prefer completely open faces or glass. Preserve the central high-energy sphere and cable-beam convergence.
- Explicitly account for both ordinary and bundled Federation cable material/connection compatibility.
- Delivered four laser alternatives L1 Split Rail, L2 Octagonal Barrel, L3 Crystal Resonator, L4 Reflector Focus;
  three Quantum alternatives Q1 Open Edge Frame, Q2 Glass Containment, Q3 Corner Glass. No selection yet.
- Current deliverable: `tools/blender/build/device-alternatives-final/alternatives.blend`, 28 scenes/stills and
  labelled option/connection sheets. Details and exact commands: `docs/art/device-alternatives.md`.
- New sources: `tools/blender/device_alternatives.py`, `device_cable_reference.py`,
  `verify_device_alternatives.py`, `tools/visual/compose_device_alternatives.py`.
- Materials sample packed original textures. Core (4,6) white, (2,6) silver, (3,6) dark, (6,6) glass;
  glass RGBA is 204/255/255/75. Constant UV sampling MUST use a connected CombineXYZ node: unlinked Image Texture
  vector defaults invoke implicit generated coordinates, which can sample transparent pixels.
- Ordinary device contact is 10/16 wide, from Java-exported connection 7. Do not attach a generic 12/16 tube.
- Bundles use their actual 2x2/3x3 artist rings, original UV/element rotations, and BundleShellLayout's texel
  rule. Huge ring model offset is (16,24,16) voxels. End caps retain per-member dense arms.
- Same R/LITE or R/PLASMA adapter on both sides of a device interface; 3x3 core scale is 4/3, glow 1.5.
- Reopened-file verification passed: 28 scenes, 21 size/connection cases, exact 10-pixel ordinary contact widths,
  equal beam core radii at interfaces, Quantum convergence, packed images and unchanged production assets.
- Still a design study. No new game UV atlases, block models, dynamic renderer, assembly or collision rules were
  shipped. Intermediate v3/v4 renders are retained separately; use the `device-alternatives-final` delivery.
