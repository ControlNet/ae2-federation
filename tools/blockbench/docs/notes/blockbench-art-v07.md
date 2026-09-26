# V07 isolated cable correction; production integration still pending

User approved V06 and requested Mod integration, then interrupted before
implementation to address isolated cable mismatch. Production resources have
not yet been changed. Preserve that pending integration request; do not mistake
this editor correction for completed Mod integration.

Cause: generator special-cases mask zero as opaque cable_idle cube; connected
masks use glass and animated core. V07 uses cable_end's actual center glass
[5..11] and stream [6..10], restores the closed east face, and removes collars
and arms. Textures and animation metadata reused exactly; all six faces closed.
Same 6-unit outer bounds; no new direction, port or connection logic.

Actual native Blockbench export/captures and all 15 project loads passed.
Validator checks two closed nested cubes, exact shared textures/frame times,
12 unchanged device/cable projects and all unchanged textures. 48-file archive
at tools/blockbench/versions/v07-isolated-cable; all older version hashes pass.

Pending integration: approved V06 textures plus V07 zero-mask geometry, maintained
production generator and validator (currently zero bypasses layer checks), item
mask-zero rendering, actual game transparency/animation and normal client/server
build validation. No production changes or in-game pass claimed yet. V08+ for
new art edits; keep V06 approved full blocks unchanged.

## Production integration completed

The later explicit implementation request has now been fulfilled. Approved
textures and isolated geometry are in generated game resources and the built
JAR. See docs/art/acceptance-v07.md and .omo/knowledges/visual-assets-v07.md for
actual client/server checks, native Minecraft screenshots and performance data.
The frozen V07 archive itself remains unchanged.
