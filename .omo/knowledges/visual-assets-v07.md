# Approved V07 production assets integrated

2026-09-26: User explicitly approved current V07 and requested implementation.
Completed production integration, not just editor previews. Approved textures
come from tools/blockbench/versions/v07-isolated-cable/textures (V06 blocks,
retained V02 Bridge/connected cable). tools/visual/build_assets.py verifies
snapshot texture hashes and copies them, retaining generated models/UVs/Part
adapters. Removed old texture drawing pipeline so generation cannot revert art.

Removed mask-zero opaque cable_idle exception. Zero now has closed 6-unit glass
and 4-unit animated core, no collar. Item already inherits mask zero. Validator
now includes zero in stream connectivity and exact surface checks; checks source
pixel equality, ME face transforms and isolated item/layer routing. No Java,
network logic, IDs/aliases, dependency or UI changes. Dedicated client boundary
and cached 64-model chunk-rebuild selection remain unchanged.

Gradle build + verifySharedJarContent passed; 236 tests, zero failures/errors/skips.
Resource check: 267 models, 5094 faces, 64 masks, reproducible output. Art archive
hashes intact. Actual JAR 7a47e1dafd63a246837477b7850e636e281ef50bca2a23c1cee25adbf68ed4f2
matches both isolated runtime installations in build/visual-v07. No BB/website
files packaged. Actual client joined dedicated server, gallery and 64 masks,
Provider six states, Bridge six mounts, items/hand/drop renders, node neighbor
transitions, native player placement at 9,1,4, F3+T and saved-world restart tested.
Twenty-three native F2 screenshots in docs/art/screenshots-v07.

A first night screenshot pair showed no flow difference (phase coincidence),
so it was rejected as motion evidence. Supplemental six-frame F2 burst at ~0.5s
confirmed five differing cable-region frames and replaced the pair. Record at
docs/art/validation-v07/animation.json. No assets changed after building/testing.

Software GL 1280x720, render distance 8, cap60: gallery mean17.67/p9520.22ms;
2048 cables +128 devices mean49.76/p9553.14ms; max queued uploads/batches 0/0
in both samples. Not a hardware benchmark or zero-cost assertion. Initial client
connection raced server readiness and was retried. Existing vanilla/LDLib2
warnings and server startup catch-up warning remain; no asset-loading errors.
No unrelated business factory qualification or exhaustive hardware/Fabulous/
shader-pack, sorting-camera or new wrench/selection-edge pass claimed.

Exact commands, limits and screenshot index: docs/art/README.md and
 docs/art/acceptance-v07.md. Raw profiles in build/visual-v07/client/debug/profiling.
Previous run/build/visual-v1 and earlier frozen art snapshots retained. Future
art changes must use a new version and update production source selection.
