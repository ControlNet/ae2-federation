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

## Verification

- JUnit green; `python3 tools/dev_gametests.py bridgevalid bridgeinvalid bridgesamegrid bridgerejectfederationcable
  bridgereloadreplace`: 5/5.
- `pixi run --manifest-path tools/visual/pixi.toml python tools/visual/validate_assets.py`: passed, reproducible.
- Guide client screenshots of the four device pages render the new art.
