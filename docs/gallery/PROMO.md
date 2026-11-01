# Promotional overviews

Two long posters for posts and mod pages, one per language:

| Language | Final image | Dimensions | Size |
| --- | --- | --- | --- |
| Chinese | [overview-zh.webp](overview-zh.webp) | 2400 × 10752 | 1,543,572 bytes |
| English | [overview-en.webp](overview-en.webp) | 2400 × 11166 | 1,666,124 bytes |

The page is laid out 1200 CSS pixels wide and captured at 2x, so the game UI panels keep close to their native
pixels. Both use WebP quality 94, method 6. Raw captures, intermediate HTML, fonts and review sheets stay in the
ignored `build/` directory.

## Content

Each section shows one thing a player can build, using the guide's own example structures:

1. Hero: the Market Hub example, three networks joined by one Switch.
2. Shared storage: the Shared Warehouse example and the topology view with its sharing-rule panel.
3. Orders across networks: the Factory That Takes Orders and Outsourced Furnaces examples, and the
   pattern-to-Processing-Endpoint mapping view.
4. Across dimensions: both halves of the Nether Outpost example.
5. Overview: the topology and statistics views.

The scenes are rendered by GuideME from the example SNBT files under
`common/src/main/resources/assets/ae2federation/ae2guide/assets/examples/`, with production AE2 and Federation
models. The Beam Converter blocks are removed from a copy of those structures because their texture is a
placeholder; the shipped files are not changed. Coloured labels match each network's cable colour in the scene.
The four UI panels are whole game windows cropped from the gallery screenshots; nothing in them is painted in.

The copy follows the example pages and `mechanics/rules.md`, `crafting/processing-endpoints.md` and
`mechanics/statistics.md`. The download link is the CurseForge project page.

## Editable sources

- [Scene renderer](../../tools/visual/promo/scenes.py): runs the guide client offscreen and crops each scene.
- [HTML/CSS layout](../../tools/visual/promo/poster.html): one background colour, 1200 CSS pixels wide.
- [Bilingual copy](../../tools/visual/promo/copy.json): separate Chinese and English text.
- [Asset preparation](../../tools/visual/promo/prepare.py): scene imports, UI crops and the font.
- [Chrome renderer](../../tools/visual/promo/render.mjs): native Node WebSocket/CDP, no npm dependencies.
- [WebP exporter](../../tools/visual/promo/export.py): dimensions, encoding, hashes and local previews.

Per-asset provenance, crops, font and tool hashes, scene settings and layout-check results are in
[promo-sources.json](promo-sources.json). Final-image hashes also appear in [sources.json](sources.json).
Artwork attribution continues to follow [Third-Party Notices](../../THIRD_PARTY_NOTICES.md).

## Reproduce

Run from the repository root. Requirements: JDK 21 for the Gradle build, Xvfb, ImageMagick (`import`),
Node 24+, Google Chrome (set `CHROME_BIN` if its executable has another name) and the approved `tools/visual`
pixi environment for Pillow. The font is Noto Sans SC (SIL OFL 1.1); download the variable TTF and its licence:

```sh
mkdir -p build/promo-fonts
curl -L -o 'build/promo-fonts/NotoSansSC[wght].ttf' \
  'https://github.com/google/fonts/raw/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf'
curl -L -o build/promo-fonts/OFL.txt 'https://github.com/google/fonts/raw/main/ofl/notosanssc/OFL.txt'
```

Then render the scenes (about three minutes, most of it starting the client) and compose both posters into new
output directories:

```sh
promo_run_dir="$(mktemp -d "$PWD/build/promo-XXXXXXXX")"
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/promo/scenes.py \
  --output "$promo_run_dir/scenes" --display :93
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/promo/prepare.py \
  --scenes "$promo_run_dir/scenes" --font 'build/promo-fonts/NotoSansSC[wght].ttf' --output "$promo_run_dir/compose"
node tools/visual/promo/render.mjs "$promo_run_dir/compose"
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/promo/export.py \
  --source "$promo_run_dir/compose" --output "$promo_run_dir/export"
```

Expected: six `SCENE_OK` lines, `POSTER_RENDER_OK zh 2400x10752`, `POSTER_RENDER_OK en 2400x11166` and
`POSTERS_EXPORTED`. Pick a free X display if `:93` is taken. The renderer fails if an image does not load or a
label or line overflows its box. The export folder holds the two WebPs, their source manifest and three review
JPEGs. This workflow does not overwrite `docs/gallery`.

## Verification

```sh
python3 -m py_compile tools/visual/promo/scenes.py tools/visual/promo/prepare.py tools/visual/promo/export.py
node --check tools/visual/promo/render.mjs
git diff --check
git diff --cached --check
```

These commands should exit successfully. The gallery's [media verification command](CAPTURES.md#verify-the-delivered-media)
also checks both posters. Review both WebPs at full size: labels must sit on the network they name, and headings
must not wrap into adjacent content.
