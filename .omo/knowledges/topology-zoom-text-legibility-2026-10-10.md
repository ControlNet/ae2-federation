# Topology canvas: text legibility when zoomed out (research, 2026-10-10)

Problem: zooming out in the topology view (`FederationTopologyView`) shrinks every card row, rule chip, Endpoint label
and domain plate name until the pixel font can no longer be read. Research plus a prototype; the LOD prototype is not merged. The Chinese vector font is (see the last section).

## How zoom scales text today

- The zoom belongs to LDLib2's `GraphView`. `refreshContentTransform()` sets a `translate + scale(scale)` transform on
  `contentRoot`, so every card, chip and Endpoint node is a real element drawn under a pose scaled by the zoom.
  `domain.lss` lets the zoom go from 0.1 to 3, and `MIN_FIT_SCALE` is also 0.1.
- Federation screens fix the window's GUI scale (`FederationGuiScale`). At 1600x960 and 1920x1080 it is 2, so one
  canvas unit is `zoom × 2` screen pixels. A font pixel needs at least one screen pixel, so text stops being legible
  below zoom 0.5 (pixel scale 1). It is a smudge by about 0.3, which is roughly where Fit lands in the related scope.
- There are two text paths on the canvas:
  - Card rows, rule chips and Endpoint labels are LDLib2 `Label`s. `TextElement.getFont()` is `LDLibFonts.font()` and
    draws through `LDLibFonts.drawText`, which picks a glyph atlas per draw with `GlyphBucket.select(pose)`. In the
    default `fontRenderMode = AUTO`, a fractional zoom takes the signed distance field (SDF) atlas
    (`BitmapSdfSource` upsamples the vanilla bitmap 4x before the distance transform; the `sdf_text.fsh` shader keeps a
    one pixel wide anti-aliased edge). This text is already smooth. It is unreadable because it is too few pixels
    tall, not because of how it is sampled.
  - Domain plate names were drawn with `GuiGraphics.drawString` and `Minecraft.getInstance().font`. That is vanilla's
    bitmap path: `FontTexture` atlases are uploaded with `blur = false` and no mipmaps, so a fractional scale drops or
    doubles pixel columns. These were the most garbled text at Fit.
- The legend (`graph_legend`) and the toolbar are siblings of the `graph-view`, not content children. They do not
  scale with the zoom.

## What LDLib2 2.2.34 already offers (read in its sources)

- A smooth font pipeline: `LDFontManager`, `LDFontSet`, `GlyphBucket`, glyph sources `BitmapSdfSource`,
  `TrueTypeSdfSource` and `UnihexSdfSource`, and the client config `fontRenderMode` (VANILLA, SDF, RASTER or AUTO;
  AUTO is the default). AUTO rasterises at the exact size when the line height is a whole number of screen pixels
  (pixel fonts need an exact texel mapping) and uses the SDF everywhere else. `isSmoothFont()` is false when
  `modernui` is loaded.
- Level of detail for graph content: `GraphView.getLod()` returns a `GraphViewLod` of FULL, SIMPLIFIED or BLOCK,
  based on `getPixelScale()` (zoom × GUI scale). The LSS properties are `lod-enabled` (default true),
  `lod-simplified-pixel-scale` (default 0.65) and `lod-block-pixel-scale` (default 0.25). LDLib2's own node editor
  uses it: `NodeElement` overrides `shouldDrawChildren()` to skip its subtree below FULL and paints flat rectangles
  in `drawBackgroundTexture` and `drawBackgroundAdditional`. The `GraphViewLod` javadoc says not to toggle
  `display` or visibility per frame. Nothing in the Federation view used LOD before the prototype.
- `UIElement.shouldDrawChildren()` is the documented seam for an element that stands in for its subtree. Children
  stay laid out and hit-testable, so clicks and UI-test checks that read element text keep working.
- `TextStyle.font(ResourceLocation)` can point a Label at any `assets/<ns>/font/*.json`, and LDLib2 ships
  `ldlib2:jetbrains_mono_bold`. A TTF font would be just as unreadable at 3 to 4 pixels tall, and it breaks the AE2
  pixel look.

## Options evaluated

| Option | Verdict |
|---|---|
| Better filtering / AA (linear, mipmaps, supersampling, MSAA) | Already done for Labels (SDF in AUTO). Supersampling a 5x8 font below one pixel per texel gives grey blobs. Does not make text readable. |
| Integer / snapped text scale | AUTO already rasterises at exact whole sizes. Useful only together with counter-scaling: draw at exactly 1 (or n) screen pixels per font pixel. |
| Vector / TTF font for the canvas | Feasible (TextStyle.font, LDLib2 SDF TTF support) but does not solve physical size and clashes with the AE2 look. Rejected for Latin; adopted for Chinese only (last section). |
| Zoom floor only | Keeping text legible needs zoom >= 0.5 at GUI scale 2, which breaks Fit for the related scope. Only useful as a floor under LOD. |
| Semantic zoom (LOD) | Works. It hides what cannot be read. |
| Counter-scaled labels | Works. Names stay readable at any zoom down to about 0.18 on the test window. |

## Prototype (worktree only, not on dev)

- `domain.lss`: `lod-simplified-pixel-scale: 1; lod-block-pixel-scale: 0`, so LOD switches below zoom 0.5 at GUI
  scale 2.
- Below FULL:
  - A card skips its rows (anonymous `Button` subclass overriding `shouldDrawChildren`) and draws its accent swatch
    and its bold name, vertically centred.
  - A rule label skips its chip Labels and paints each chip as a block in its state colour, at the chip's laid-out
    bounds. It adds the word, or its initial, only while the chip is at least 9 screen pixels tall.
  - An Endpoint node keeps its box and its state dot.
  - Plate names use the same fixed-size text. At FULL they now go through `LDLibFonts.drawText` instead of vanilla
    `drawString`.
- `drawLegible(...)` draws text at a whole number of screen pixels per font pixel: `min(GUI scale / 2, what fits)`,
  never less than one. It snaps the origin to the screen pixel grid and draws under an identity-plus-scale pose, so
  `GlyphBucket` sees an exact line height and takes the crisp raster path. When the text does not fit it falls back
  first to a shorter form (the 4-hex tag of an unnamed network, or a chip's initial), then to an ellipsis.
  - Gotcha: `Button` keeps its own hidden text element among its children, so `getChildren().get(i)` is not the i-th
    child you added. Find children by class instead (`pill-chip`, `endpoint-dot`).
- `TaskThirtyThreeGraphControlsScenario` gained zoom screenshots after `ui-scope-related`: `ui-zoom-060`, `045`,
  `033`, `025` and `018`, each centred on the content. All 67 checks still pass with the prototype.
- Result: names are crisp and readable at every tested zoom. At 0.18 an unnamed network reads as its tag. Chips read
  as colour plus initial down to about zoom 0.4, then as colour only.
- Ellipsizing uses `plainSubstrByWidth` on the plain string, which ignores the bold width.
- Known gaps:
  - Nested plate names ("This domain" over "Domain XXXX") overlap at fixed size, because their spacing is 12 canvas
    units.
  - A related card's padlock and dashes stay at canvas size.
  - The jump from FULL rows to the compact card is a hard switch at zoom 0.5.

## Recommendation given

- Use LOD plus counter-scaled names, with a zoom floor of about 0.15 to 0.2. Details stay one click away in the aside.
- The switch threshold must equal the compact texel size: switch to compact when `pixelScale < max(1, guiScale / 2)`.
  - The prototype's static LSS threshold of 1 is right at GUI scales 2 and 3.
  - At GUI scale 4 or 5 the static threshold switches at pixel scale 1, where FULL text is 1 pixel per font pixel,
    but compact names draw at 2. Text would grow when zooming out.
  - To fix it, set `lodSimplifiedPixelScale(texel)` when `FederationGuiScale` applies a scale, or have `compact()`
    compare `graph.getScale()` with 0.5 directly.
- Moving plate names to `LDLibFonts.drawText` is a small consistency fix. At FULL fractional zooms (0.6 in the
  screenshots) vanilla nearest sampling only doubles a few columns. The bad garbling happens below pixel scale 1,
  which compact mode covers.

## Open questions

- At low zoom, should a chip show its initial (S, C, E...) or colour only?
- What size should the compact name be? The prototype uses `max(1, guiScale / 2)` screen pixels per font pixel.
- Should hovering a compact card show a name tooltip?
- How should nested plate names lay out at fixed size?

## Font comparison (2026-10-10, branch `font-compare`, not on dev)

The user ruled out LOD and counter-scaled labels for now and asked for a side-by-side of fonts instead.

### Setup

- One switch: `-PfederationCanvasFont=<font id>` sets the system property `ae2federation.canvasFont`
  (`neoforge-1.21.1/build.gradle`, `uiTestClient` run).
- `FederationTopologyView` reads the property as `CANVAS_FONT`. It is applied in three places:
  - `applyCanvasFont` sets `TextStyle.font(...)` on every `TextElement` under `graph.contentRoot` after
    `rebuildGraph()`: card rows, chips and Endpoint labels.
  - `canvasFont(Component)` adds `Style.withFont` where the layout measures chip, Endpoint and plate-name widths
    with the vanilla `Font`.
  - Plate names are drawn with `LDLibFonts.drawText` instead of vanilla `drawString`.
- Legend, toolbar and aside keep the default font.
- Variant B sets `fontRenderMode = "SDF"` in `run-uitest/config/ldlib2-client.toml`. That is LDLib2's global
  client option, so it would change every LDLib2 screen for a player, not just this canvas.
- The test-only fonts sit in testmod resources under `assets/ae2federation_test/font/` (`monocraft.json`,
  `lato.json` and their TTFs), so they never reach the mod jar.
- Scenario steps:
  - `ui.graph-controls` takes `ui-zoom-100/060/045/033/025/018` in the related scope.
  - `ui.chinese-scales` takes `ui-zh-zoom-*` in the domain scope. It has no rules, so no chips.
- Window 1600x960 with option GUI scale 3. `FederationGuiScale` forces the effective scale to 2, so pixel scale is
  zoom × 2.
- Output goes to `build/topology-font-compare/<variant>/<zoom>.png` and `sheets/*.png`. The sheets are built with
  Pillow through the `tools/visual` pixi environment.

### Results (pixel scale = zoom × 2)

| Variant | Legible down to | Look | Licence / jar cost |
|---|---|---|---|
| A vanilla pixel font, AUTO | 0.45 fully; 0.33 bold names and figures only | AE2 native | none |
| B pixel font, SDF mode | Same as A: AUTO already uses the SDF at every fractional zoom | Same | Global LDLib2 option; cannot be set per screen |
| C JetBrains Mono Bold (`ldlib2:jetbrains_mono_bold`) | 0.33 mostly readable; 0.25 no | Heavy monospace, coder look, wider | OFL-1.1; already in LDLib2, no size cost |
| D `minecraft:uniform` | Worse than A at every zoom below 1 (Latin is drawn at about 5 px cap height) | Thin, small | Vanilla, no cost |
| E1 Monocraft (pixel-style TTF) | 0.33 about like C; 0.25 no. A slashed zero reads like "8" when small ("188%") | Closest to the Minecraft look, monospace and wider | OFL-1.1 per upstream (the font's name table carries no licence string); 198 KiB. NeoForge ships it in its early-display jar, which is not reachable as a resource |
| E2 Lato Regular + Droid Sans Fallback | Best at 0.33 (coordinates and figures readable); 0.25 marginal | Smooth proportional sans, not AE2 | Lato OFL-1.1, 646 KiB; Droid Sans Fallback Apache-2.0, 3.8 MiB for CJK |

- No font is readable at 0.25 or 0.18. Fit in the related scope lands at about 0.3.
- A font change buys about one zoom step (0.45 → 0.33), not legibility when zoomed far out. Physical size is still
  the limit.

### CJK (zh_cn)

- A to E1 fall back to Unifont for CJK. LDLib2 appends `include/unifont` to every chain; vanilla custom fonts have
  no such fallback.
- Unifont CJK glyphs are 16 px designs drawn 8 units tall, so a Unifont pixel is half a canvas unit.
  - Chinese text such as 主世界 or 共享供电 already blurs at zoom 0.6.
  - It is unreadable at 0.33 in every variant except E2, so CJK hits the limit before Latin does.
- E2's Droid Sans Fallback vector CJK holds up best: 网络 18A7 stays readable at 0.33. It costs 3.8 MiB.
  - No Noto Sans SC was available offline. The installed Noto fonts are mono only, and nothing was downloaded.
- Risk for fonts without CJK coverage (C, E1): the vanilla `Font` used for layout widths has no Unifont fallback,
  so CJK widths are measured as missing glyphs while LDLib2 draws them with Unifont.

### Caveats when reading the sheets

- The 0.33 and 0.25 verdicts come from the 2x nearest-neighbour detail crops. At native size (about 6 px line
  height at 0.33) C, E1 and E2 are only marginal.
- Plate names in A use vanilla `drawString`. B to E use LDLib2's path, so for plate names compare against B, not A.
- The `fit` sheet lands at a different zoom per variant. Wider fonts grow the chips and `labelExtent`, so
  `fitToChildren` zooms out further. Only the fixed-zoom sheets compare like with like.
- Layout widths come from vanilla's `TrueTypeGlyphProvider`, while drawing goes through LDLib2's
  `TrueTypeSdfSource`. No mismatch was visible, but matching advances are only promised for the default font.

## Shipped: a vector font for Chinese only (2026-10-10, on dev)

The user picked "vector CJK font, Latin stays the pixel font" from the comparison.

- `font/canvas.json` (`ae2federation:canvas`) has two providers: the TTF `canvas_cjk.ttf` (size 9, oversample 8,
  `filter: {uniform: false}` so Force Unicode Font still means Unifont), then `reference minecraft:default`.
  - The TTF has no glyph below U+2E80 (its cmap starts at U+3000). Latin, digits and `·` fall through to the
    player's default font, resource packs included. Any glyph in the TTF would win over the pixel font.
  - Characters outside the subset fall through to Unifont, as elsewhere in the game. Names from other mods may mix
    the two.
- `canvas_cjk.ttf` is Droid Sans Fallback (Apache-2.0) cut by `tools/visual/build_canvas_font.py` (fontTools, added
  to the `tools/visual` pixi environment): the GB2312 hanzi (6763), every CJK character in `zh_cn.json`, U+3000-303F
  and U+FF00-FFEF. Layout, vertical metrics and hinting are dropped. 955 KiB on disk, about 530 KiB in the jar
  (jar 2.0 MiB). Level 1 only (3755) would be 566 KiB on disk. `canvas_cjk.LICENSE.txt` carries the attribution,
  the change notice and the Apache licence.
  - Droid was chosen over Noto Sans SC: it was the one measured readable, it is static TrueType, and Noto's
    google/fonts build is a variable font that would need instancing first.
- `FederationTopologyView.CANVAS_FONT` is applied as in the comparison (`applyCanvasFont`, `canvasFont` on every
  width measurement) and plate names go through `LDLibFonts.drawText`. Vanilla `drawString` turned "067B" into
  "0673" at zoom 0.45; LDLib2's renderer does not. This also fixes English plate names at fractional zooms.
- Checked:
  - English: a canvas font that only references `minecraft:default` renders pixel-identically to the default font
    in LDLib2, at zoom 1.0 and 0.45. With the TTF in front, English is unchanged.
  - Chinese: 能量 / 存储 / 共享供电 / 主世界 are crisp at 0.6 and readable at 0.45 (Unifont was a blur at 0.6).
    Size 9 matches the pixel font's height better than 8.
  - Rule chips and Endpoint labels in Chinese fit their boxes (measured width = drawn width). This was checked with
    a temporary related-domain step in the Chinese scenario, not committed.
  - `CanvasFontContractTest` pins the provider order, the no-Latin rule, coverage of `zh_cn.json` and the licence.
    Java's `Font.canDisplay` reports format characters (U+200C-200F, 2028-202E, 206A-206F) as displayable in any
    font; the test skips them.
- `ui.graph-controls` drifts between runs: the left link sometimes shows Storage + Energy chips, sometimes Energy
  only. That was already true of the comparison runs and is unrelated to fonts.
- Bold gotcha (found in the user's playtest): bold redraws a glyph `boldOffset` to the right. LDLib2 and vanilla use 1
  unit for TTF glyphs (0.5 for Unifont, 1 for the bitmap font). The Droid strokes at size 9 are thinner than one
  unit, so bold "网络" in card names showed doubled strokes. `CanvasFont.boldExceptChinese` keeps Latin and digits
  bold and draws Chinese plain. Card names are the only bold text on the canvas; aside headings use the default font.
