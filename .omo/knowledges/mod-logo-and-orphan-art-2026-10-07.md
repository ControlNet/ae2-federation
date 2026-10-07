# Mod logo rendered by the game; orphaned textures removed (2026-10-07)

## Logo

- `neoforge-1.21.1/src/main/resources/ae2federation_logo.png`: 512x512 RGBA, the Router's inventory icon. Declared in
  `neoforge.mods.toml` inside `[[mods]]` as `logoFile="ae2federation_logo.png"` plus `logoBlur=false` (FML
  `ModInfo` reads both from the mod table, falling back to the file table; the path is relative to the JAR root).
- Rendered by testmod `test/art/ModLogoRenderer`, started by
  `LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -s "-screen 0 1280x720x24" ./gradlew :neoforge-1.21.1:runLogoClient`
  (`-PlogoOut=<png>` for another path). It waits for the title screen, draws into a `TextureTarget` with the GUI
  projection of one 16x16 slot (`setOrtho(0, 16, 16, 0, ...)`) and calls `GuiGraphics.renderItem`, so the model's own
  `gui` transform and GUI lighting apply. Readback copies `Screenshot.takeScreenshot` but with
  `downloadTexture(0, false)`; vanilla's `true` forces every pixel opaque.
- The Router model is `cutout`, so the result has only alpha 0 and 255; no anti-aliasing was needed at 512.
- Pinned by `ModLogoContractTest` (declaration, 512x512, alpha, transparent corner, opaque centre). Checked in the dev
  client's Mods screen: shown sharp.
- The old `docs/art/icons/` renders of the pre-artist Router were deleted (nothing referenced them).

## Orphaned shipped textures

`textures/block/core.png`, `stream_u_reverse.png`, `stream_v_reverse.png` (with their `.mcmeta`) were never
referenced by any model or code; `cable_idle.png` was only copied by the generator after V07 dropped the opaque
mask-zero cube. All removed; `cable_idle.png` also left both scripts' cable texture sets.
`tools/visual/validate_assets.py` now fails on any texture no model uses (except the cable flow renderer's
`entity/cable_flow`).

## Still to decide (owner)

Old art kept in the tree: Blockbench iteration snapshots v01-v06, `tools/blockbench/projects/*.bbmodel` and the
design/MCP scripts, `docs/art/screenshots*`, `validation-v07`, `acceptance*.md`, `design/visual` spec. v07 stays: the
cable generator reads and hash-checks its textures.
