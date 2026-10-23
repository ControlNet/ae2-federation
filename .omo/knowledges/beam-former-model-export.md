# Beam Former model integration

The `size` enum blockstate uses `small`, `large`, and `huge` for 1x1, 2x2, and 3x3.
`BeamFormerBlock.forming` maps the existing `CableCrossSection` result and sets facing and size together.
Placement defaults to small; wrench turns preserve size. `FormableDevice.unforming`, invoked by
`DeviceStructureUpdater.set` for unformed states, resets the former to small. Unknown unloaded bodies retain
last validated size, consistent with the updater's existing policy.

`tools/blockbench/export_beam_former_models.py` ports the checked trial's clipping, rotated-centre ownership,
UV rotation and vanilla blockstate rotation logic. It writes six models and 42 multipart cases.
A large member selects a quadrant only with exactly one connection on each in-plane axis and none along
the facing axis. All other large masks fall back to the small model for that facing, including damaged,
partly unloaded bodies. The large model intentionally reaches z=17. Original artist files remain
unchanged. The huge model keeps the existing placeholder textures.

Small and large use empty occlusion to avoid hiding faces visible through the frame; huge retains cube
occlusion and inherited shade brightness. Small and large return shade brightness 1.0F. Collision and
selection remain unchanged. The placeholder texture builder writes only the legacy texture directories; the cable asset builder writes only cable assets, so neither overwrites the export.

Validation regenerates both exporters in a temporary tree and compares bytes, checks the 64-pixel large
texture, and verifies unique multipart selection across all six facings, three sizes and 64 connection masks.
Validation also checks the expected intact quadrant and small fallback for every mask.
The former shape GameTest writes its ten-case evidence only after checking sizes and resets on broken
2x2 and 3x3 bodies; the manifest requires ten assertions.

For verification, set `PIXI_CACHE_DIR=/tmp/beam-art-pixi-cache` if the default package cache is read-only.
Run `./gradlew :neoforge-1.21.1:jar` before unit tests in a fresh worktree: the existing harness contract
requires the production JAR to exist. The beam-filtered GameTest batch includes four converter tests and
eight former tests; all twelve passed for this integration.
