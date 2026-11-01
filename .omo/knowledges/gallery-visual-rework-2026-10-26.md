# Gallery visual rework

The user rejected the first gallery's non-UI imagery. Successful capture, valid WebP encoding and source
provenance did not make those images suitable for presentation. The teaser and UI were the exceptions.
This supersedes the visual acceptance implied by gallery-delivery-2026-10-26.md.

## Observed failures

- World overview and furnace shots: small subjects, excessive grass, distracting test installations, no clear
  explanation of which networks are independent or how a job travels.
- Lineup and four-device strip: inventory of objects rather than a useful feature explanation.
- Standalone multiblocks: plain cuboids with no connection or reason to build them.
- Beam views: mostly backs of devices, large platform foreground, unclear sizes and repeated night/animation views.
- Cropped bundled-cable screenshot: readable object, but no capacity, attachment or size context.

## Revised delivery

Eleven static WebPs in docs/gallery. Seven existing teaser/UI/guide images remain byte-identical. Four replacement
images explain network sharing, remote processing, bundled-cable capacity and Beam Former sizes. Five weak or
redundant images were removed. The full initial gallery is backed up locally in build/gallery-before-visual-rework/.

Use real production models in compact studio scenes, reserve room for type, label the actual parts, and put a
single feature and its meaning inside each image. Verify at both full size and 800px width. Keep leader lines clear
of label text. Do not use image count or successful encoding as a quality criterion.

## Reuse and correctness

tools/blender/gallery.py reuses the asset/material importers, production cable shell exporter, tiled Router/Switch
geometry, existing R/LITE and R/PLASMA render adapters and the Blockbench element exporter. The ring import must
respect element rotations: the older bundle_beams.ring_quads helper ignores these and produces protruding bars
for the current artist project. Use export_elements plus assets.model_quads instead.

tools/visual/compose_gallery.py adds explanatory typography and exports WebP with the approved tools/visual pixi
environment. Both scripts write only new build directories. Reproduction and verification commands live in
docs/gallery/CAPTURES.md. Final raw artifacts are in build/gallery-rework-final/.

Render diagrams are not complete builds: AE2 Interfaces represent separate networks, and power supplies and
machine internals are omitted. Switches join ME networks; Routers terminate cable segments. Provider fronts and
Endpoint fronts face the Federation cable. Results must be pushed into an Endpoint's non-front face. The beam
comparison uses a common scale and omits active beams. Studio cable light is an adapter, not a runtime screenshot.

No Quantum Cube / Beam Converter artwork is used. Concurrent refined-device source and tooling work was left
outside this task's edits and staging.

Verification: all 11 WebPs decoded with matching dimensions, sizes and hashes; all seven preserved files matched
the backup exactly. All 46 render resource hashes and recorded tool hashes matched current inputs. Markdown links
resolved, Python compilation and Git whitespace checks passed, and the multiblock parity command returned
PARITY_FIXTURE_CURRENT. Final media totals 1,309,992 bytes (1.25 MiB), down about 58% from the initial collection.
