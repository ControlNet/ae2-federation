# Gallery capture notes

## Final media

The curated gallery contains eleven detail images: the unchanged teaser, five UI screenshots,
one GuideME screenshot, and four annotated Blender renders. Two additional bilingual promotional overview
posters are documented in [PROMO.md](PROMO.md), bringing the total to thirteen static WebPs. UI and guide images remain lossless.
New renders are 1600 × 1000, WebP quality 94, method 6. All final media is tracked in ordinary Git.
The original `docs/teaser.webp` also remains at its existing path.

Raw PNGs, editable Blender scenes, local review sheets and game worlds stay under ignored `build/` paths.
A fresh clone needs none of these to display the gallery. Per-image source hashes and encoding settings,
plus the render's resource and tool inventories, are recorded in `sources.json`.

## Visual revision

The original gallery's world views had small subjects, distracting grass and test platforms, unclear connections,
and repeated views of the same devices. They were replaced with four compact scenes that each explain one feature.
The device strip, in-game lineup, standalone multiblock view, night view and animation were removed from the gallery.
The previous local delivery is preserved in `build/gallery-before-visual-rework/`.

| Image | What the scene communicates | Source |
| --- | --- | --- |
| `network-overview.webp` | Two separate ME networks join a Federation domain through Switches. | Production Switch and AE2 Interface models; production cable shell export. |
| `processing-installation.webp` | The Provider sends job inputs to the Endpoint's subnet; machine outputs return through the Endpoint. | Production Provider, Endpoint and AE2 Interface models; production cable shell export. |
| `bundled-cable.webp` | A 2 × 2 cable meets formed Router faces; capacity increases with the cable's cross-section. | Existing tiled multiblock geometry, shell texel adapter and original artist ring imported with its element rotations. |
| `beam-sizes.webp` | Front artwork and relative scale of the three Beam Former sizes, with their linking conditions. | Production 1 × 1 model and the 2 × 2 / 3 × 3 member models. |

The first two renders are connection diagrams: each AE2 Interface represents its network. Power supplies,
crafting CPUs and machine internals are omitted. They are not complete construction tutorials or gameplay captures.
The cable light reuses the repository's R/LITE and R/PLASMA study adapters at a fixed demonstration load;
studio lighting and light blending differ from the game. The Beam Former comparison shows device artwork without
an active beam. No Quantum Cube or Beam Converter models are included.

## Reproduce the four renders

The gallery renderer reuses `assets.py`, `materials.py`, `build_scene.py`, `export_cable.py`, the existing cable
light adapters, `tools/multiblock/geometry.py` and the Blockbench element exporter. The two gallery scripts add
scene composition, camera placement, labels and WebP export; they do not change production assets or gameplay.

Requirements: JDK 21, Blender with Cycles, the Minecraft and AE2 jars already cached by the development build,
the approved `tools/visual` pixi environment, and system DejaVu Sans fonts. These commands write a fresh ignored
output directory and do not overwrite the delivered images:

```sh
gallery_render_dir="$(mktemp -d "$PWD/build/gallery-render-XXXXXXXX")"
python3 tools/blender/export_cable.py --output "$gallery_render_dir/cable.json"
blender --background --factory-startup --python tools/blender/gallery.py -- \
  --output "$gallery_render_dir" --device optix
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/compose_gallery.py \
  --source "$gallery_render_dir" --output "$gallery_render_dir/composed"
python3 tools/multiblock/export_runtime_parity.py --check
```

Use `--device cpu` if OptiX is unavailable. Expected: four rendered PNGs, `gallery.blend`, `renders.json`,
four final WebPs, `composed.json`, `review.jpg`, and `PARITY_FIXTURE_CURRENT`. The shell exporter validates
all 4096 connection sets. Inspect `review.jpg` and each full-size WebP before replacing gallery images:
labels must point at the correct devices, avoid text/subject collisions, and explain the feature at half size.

## Preserved screenshots

Game captures: **2026-10-26**, development commit `545b4a3286d743f97609236e386c90876ca867a3`,
AE2 Federation `0.0.10-dev`, Minecraft `1.21.1`, NeoForge `21.1.250`, AE2 `19.2.17`, LDLib2 `2.2.34`,
GuideME `21.1.1`. These came from development clients, not released-JAR acceptance runs.

- `topology.webp`: `ui.showcase-domains`, `26_ui-showcase-domains.png`.
- `sharing-rules.webp`: `ui.mapping`, `732_ui-showcase-pair.png`.
- `processing-mappings.webp`: `ui.mapping`, `740_ui-showcase-processing.png`.
- `statistics-stock.webp`: `ui.mapping`, `827_ui-showcase-statistics-stock-items.png`.
- `statistics-flow.webp`: `ui.mapping`, `757_ui-showcase-statistics.png`.
- `ingame-guide.webp`: the `mechanics/bundled-cables.md` GuideME page.

The UI fixture uses configured example names, creative power and testing patterns, including a renamed vanilla
diamond and synthetic output quantities. Statistics record storage operations driven by that fixture; they are
not throughput benchmarks. All topology networks shown are in the Overworld, including "Nether Outpost".
These screenshots and the teaser are preserved byte-for-byte from the previous delivery.

## Recapture the UI and guide

```sh
gallery_run_dir="$(mktemp -d "$PWD/build/gallery-ui-XXXXXXXX")"
xvfb-run -a -s '-screen 0 3200x2000x24' ./gradlew --no-daemon \
  :neoforge-1.21.1:runUiTestClient \
  -PfederationUiSelection=ui.mapping \
  -PfederationUiOutputDir="$gallery_run_dir" \
  -PfederationUiWindow=1920x1080 -PfederationUiGuiScale=3 \
  -PfederationUiWatchdogSec=600
```

Expected: `1/1` scenario and `48/48` checks pass, with screenshots under `screenshots/ui.mapping/`.
The scenario changes its window size during its checks; select by screenshot name, not by assuming every
capture is 1920 × 1080. Allow a 3200 × 2000 virtual display for its final high-resolution guide screenshots.

```sh
gallery_domains_dir="$(mktemp -d "$PWD/build/gallery-domains-XXXXXXXX")"
xvfb-run -a -s '-screen 0 1920x1080x24' ./gradlew --no-daemon \
  :neoforge-1.21.1:runUiTestClient \
  -PfederationUiSelection=ui.showcase-domains \
  -PfederationUiOutputDir="$gallery_domains_dir" \
  -PfederationUiWindow=1920x1080 -PfederationUiGuiScale=3 \
  -PfederationUiWatchdogSec=300
```

Expected: the scenario passes and `ui-showcase-domains` shows all network cards in view. This scenario has
28 steps and no separate `.check` assertions; its waits must complete successfully.

For the guide, the existing task opens the requested page:

```sh
./gradlew :neoforge-1.21.1:runGuideClient \
  -PguidePage=ae2federation:mechanics/bundled-cables.md
```

Expected: the page contains the bundled cable's 3D scene and construction instructions. The task prepares its
development-client options. For this capture, a temporary Gradle init script redirected the client into
`build/gallery-20261026/client/` and disabled the shared options-preparation task. All new raw captures and
the copied world remain under `build/gallery-20261026/`.

## Verify the delivered media

```sh
pixi run --manifest-path tools/visual/pixi.toml python - <<'PY'
from pathlib import Path
import hashlib
import json
from PIL import Image

root = Path('docs/gallery')
manifest = json.loads((root / 'sources.json').read_text())
expected = {entry['file'] for entry in manifest['images']}
actual = {p.name for p in root.iterdir() if p.suffix.lower() in {'.webp', '.png', '.gif', '.jpg'}}
assert actual == expected
for entry in manifest['images']:
    path = root / entry['file']
    assert path.suffix == '.webp'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], path
    with Image.open(path) as image:
        assert image.format == 'WEBP'
        assert list(image.size) == entry['size']
        assert image.n_frames == entry.get('frames', 1)
        for frame in range(image.n_frames):
            image.seek(frame)
            image.load()
assert (root / 'teaser.webp').read_bytes() == Path('docs/teaser.webp').read_bytes()
print(f'GALLERY_OK: {len(expected)} WebP files; dimensions, frames, hashes and teaser verified')
PY
git diff --check
git diff --cached --check
git ls-files docs/gallery
```

Expected: `GALLERY_OK`, no whitespace errors, and the gallery media and documentation listed as tracked files.
Visually check full-size UI text and the four renders at half size; decoding and hashes do not assess composition.
