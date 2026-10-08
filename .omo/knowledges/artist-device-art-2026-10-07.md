# Artist device art (PR #1), merged into dev 2026-10-07

The artist's PR #1 replaced the Router, Pattern Provider, Processing Endpoint and Bridge models and textures with
hand-made Blockbench art. It was merged into `master` by mistake; master was reset to the commit before it and the PR
was merged into `dev` with all its commits (merge 5062446). All art is All Rights Reserved (README).

## What the art touches, and what it does not

- No renamed item, recipe or language key is involved: the PR only changed the four devices, whose ids are the same
  on dev and master. The Nexus Processor and Nexus Core still have no icons (artist to supply).
- Provider and Endpoint: the front (Federation face) stays on the model's SOUTH face, as the `facing` blockstates
  expect. Their items now inherit the block model; its `gui` rotation `[30, 45, 0]` shows the front in inventories
  (checked in a guide recipe slot). The guide's `<BlockImage p:facing="south">` header shows the back, as it did before.
- Bridge: 8x8x5 instead of 8x8x6. `MultipartBridgePart.getBoxes` now matches the model, pinned by
  `MultipartBridgeContractTest.collisionBoxesMatchThePartModel` (AE2 part quads face NORTH, collision boxes SOUTH:
  turn each element half a turn about Y). `getCableConnectionLength` 5 now meets the inner plate exactly. Two hidden
  inner faces use `#missing`; they are covered by the middle body.
- `tools/visual/build_assets.py` and `validate_assets.py` now cover only the Federation Cable. Run unchanged, the
  generator would have overwritten the artist's models and front textures and restored 13 deleted textures. The
  generator's output is byte-identical to the checked-in cable assets (268 files); the validator passes.
- `models/block/bridge_south.json` (a generator intermediate referencing deleted textures) was removed.

## See-through gaps (fixed after the merge)

The new models are not full cubes, but the blocks kept the default full occlusion shape, so a neighbor dropped its
face toward the device and the player saw the void through the gaps (sky showed through the Provider's front).
`router/DeviceOcclusion` gives each block the shape its model actually closes: the Router (frame with gaps on all
six sides, body inset to 1..15) closes none; the Provider and Endpoint close only their back, because the recessed
front frame is notched 1 px into the four sides at y/x 5-6 and 10-11. Provider and Endpoint use the shape for light
too (`useShapeForLightOcclusion`), so the back still blocks light. Not `noOcclusion()`: that would also drop culling
and light blocking at the closed back.

- `DeviceOcclusionContractTest` computes the closed sides from the model JSON and compares them with the shapes for
  all six facings; it fails for a full block.
- GameTest `visual.device-occlusion` (`deviceocclusion`, 10 assertions) asks `Block.shouldRenderFace` in a world:
  six stones around a Router and the stone before a Provider/Endpoint keep their faces, the one behind is hidden.
  It failed before the fix. `router.*` ids are pinned to the Task 12 six, hence the `visual.` prefix.
- Production client, stone wall with the devices set in it: sky visible through the Provider/Endpoint front before,
  stone faces after.
- If the artist changes these models, the contract test says which sides they close; update `DeviceOcclusion`.

## Verification

- JUnit green; `python3 tools/dev_gametests.py bridgevalid bridgeinvalid bridgesamegrid bridgerejectfederationcable
  bridgereloadreplace`: 5/5.
- `pixi run --manifest-path tools/visual/pixi.toml python tools/visual/validate_assets.py`: passed, reproducible.
- Guide client screenshots of the four device pages render the new art.

## PR #2: Nexus item icons (merged into dev 2026-10-08)

The artist's PR #2 added animated icons for the Nexus Core (5 frames), the Nexus Processor (16 frames) and a new
item, `printed_nexus_processor` ("Nexus Circuit" / "联结电路板"). The owner accepted the new item, renamed it
Printed Nexus Circuit / `printed_nexus_circuit` after AE2's printed circuits, and made it the first step of the chain
(see `nexus-chain-ae2-style-2026-10-08.md`).

Like PR #1, it carried stale files from the artist's local asset folder. They were removed in the fix-up after the
merge:

- the old `models/item/federation_logic_processor.json`, whose item and texture no longer exist;
- `models/block/bridge_south.json`, which referenced deleted textures;
- the orphaned `textures/block/cable_idle.png`, `core.png` and `stream_{u,v}_reverse.png` (deleted in `e1bcfd5`).

The cable `00_glass/00_solid/00_stream` models came back as Blockbench re-exports. They had no change in geometry
but no longer matched the generator, so they were restored. Check an artist PR with
`tools/visual/validate_assets.py`, which catches all of these; JUnit does not.

The validator's strict animation rule is for the cable flow textures. Animated item icons only need each listed
frame to exist in the strip. `SurvivalRecipeContractTest.theCoreAndProcessorAreRegisteredNamedAndModelled` now
checks all three icons and that the old processor model stays gone.
