# Multiblock Router/Switch runtime integration (2026-10-11)

Branch `design/multiblock-devices`. The accepted offline appearance (`router_style='raised'`,
`switch_style='white-backed'`) now renders in Minecraft from validated solid-cuboid bodies.

## Decisions the user selected

- Invalid candidate (incomplete, hollow, L/T, protrusion, too large): the whole candidate stops linking.
- Longest edge at most 8 blocks per axis (`DeviceStructure.MAX_EDGE`).
- AE2 crafting-CPU rules: single block is a 1x1x1 body; same-type face contact is judged together; one
  protrusion invalidates the whole candidate; edge/corner contact stays separate; Router and Switch never
  mix; no new persistent identity, the existing per-block nodes and Federation evidence carry identity.
- Unloaded chunks: keep the last validated state, recheck on load, never force-load.

## How it works

- `DeviceStructure.evaluate` ports AE2 `MBCalculator`: grow the low corner x, y, z, then the high corner;
  check edge length, full interior, then the one-block shell (a same-type block there = `TOUCHING`). An
  `UNLOADED` cell makes the result `Unknown` unless the candidate was already invalid. Unlike AE2's
  `getBlockEntity`, the lookup uses `level.isLoaded` and never loads a chunk.
- `DeviceStructureUpdater` mirrors `CraftingBlockEntity`: block entity initialization and `neighborChanged`
  (filtered by `affects`, AE2's "changed position in bounds / now same type / not in cluster") enqueue a
  position; `LevelTickEvent.Post` checks each body once. Results are written only as blockstates
  (`formed` + six connection booleans) with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, so the check never
  triggers neighbour updates and the block entity survives.
- Suspension: `RouterFacePort` takes a `linking` supplier (`RouterBlock.formed`). An unformed face resolves to
  `Disconnected`, which withdraws its Federation evidence, so attached networks leave the domain. The boundary
  node itself stays as before (still hidden toward Federation ports), so reshaping never joins two native
  Grids. `RouterBlockEntity.setBlockState` marks ports and domain evidence dirty when `formed` flips.
- Interior faces of a formed body keep linking to each other through Federation (this keeps the body one
  domain member); their boundary nodes have no native connections.
- Right-clicking an unformed member shows `ae2federation.structure.*` (forming / unloaded / invalid reason)
  on the action bar instead of opening the domain screen.

## Client model

- Blockstates are a multipart with one unconditional `apply`, so every state uses the artist's model; mask 0
  (single block or unformed) keeps that model untouched.
- `DeviceStructureBakedModel` wraps each state with a non-zero mask in `ModelEvent.ModifyBakingResult` and builds
  quads lazily per mask from `DeviceStructureGeometry`, a client-free Java port of `geometry.py` +
  `switch_connectors.py`. Quads use FaceBakery vertex order, cull to a side only when lying on the block boundary.
- Parity: `tools/multiblock/export_runtime_parity.py` writes canonical SHA-256 hashes of every (device, mask)
  quad set to `common/src/test/resources/multiblock/device-structure-parity.json`; `DeviceStructureGeometryTest`
  compares all 128. Regenerate the fixture after any geometry change, then run `--check`.

## Pitfall found in game: see-through Switch channels

The connected Switch drops the body (element 0) and face slabs (1-4). In the original block those also
closed the 2x2 channel each edge assembly sits in; without them the 1-unit notches beside recessed pieces
16/17 showed the world through the block (visible at grazing angles; also present in the offline prototype,
where Blender staging hid it). Fix: `channel_walls` adds the two inner channel walls behind every emitted edge
of a connected Switch member, textured from the source face on that plane with end pixels repeated. A ray test
(`test_connected_switch_and_router_members_are_opaque`) fails without it; all 63 non-full masks per device have
zero see-through rays afterwards. Single blocks are unchanged.

## Test tricks

- Cross-chunk GameTest: a forced chunk keeps neighbours within two chunks loaded (ticket levels 31-33), so
  forcing the body's own chunk keeps the next one loaded. Force an anchor chunk two chunks away instead, then
  unforce the far chunk to unload it.
- In-game screenshots: `runStructureSceneClient` (testmod, `DeviceStructureScene`) builds a flat world, takes
  views, removes/replaces a member, reloads resource packs, and quits. A second run reopens the saved world
  and writes `reopened/` shots without rebuilding (save/re-entry check). Move the saved world aside to get a
  fresh first run. Software GL under xvfb works:
  `LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1600x960x24 +extension GLX" ./gradlew --no-daemon
  --dependency-verification=strict :neoforge-1.21.1:runStructureSceneClient --no-configuration-cache`.

## Not verified

Dedicated-server client sync with a remote player, other resource packs/shaders, hardware GPU lighting
(screenshots use llvmpipe), performance of many large bodies, and the right-click message through a real
click (GameTests check `DeviceStructureUpdater.status`).
