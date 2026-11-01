# First gallery delivery (2026-10-26)

Historical delivery record: the user rejected the non-UI presentation. The revised selection and visual
criteria are recorded in [gallery-visual-rework-2026-10-26.md](gallery-visual-rework-2026-10-26.md).
The current gallery has eleven static WebPs; the counts and camera notes below describe the initial version.

The owner approved starting the planned gallery, deferring devices with temporary textures,
then explicitly requested Git tracking and WebP for online distribution.

## Delivered

- `docs/gallery/`: 15 still WebP images and one 60-frame, approximately four-second animated
  WebP. Media total: 3,100,128 bytes (2.96 MiB). Animated WebP: approximately 539 KiB.
- `README.md` renders the collection; `CAPTURES.md` records source boundaries, reproduction
  and verification; `sources.json` records source/output hashes, crop boxes, dimensions,
  encoding and the Blender montage's four reference-image hashes.
- Root README links the gallery. `docs/teaser.webp` remains untouched; the gallery copy is
  byte-identical. UI and GuideME stills are lossless; world images and the montage use WebP
  quality 92/method 6; animation uses quality 85/method 6 at 960x540, 15 fps.
- Quantum Cube, Beam Converter and proposed replacement artwork are excluded. The guide
  image uses Bundled Cables, since the current Market Hub scene includes a temporary Converter.
- Covered: installation, processing layout, multi-domain topology, rules, mapping, stock,
  transfers, core device renders, Nexus/component lineup, formed Router/Switch, bundled cable,
  Beam Former sizes/night/animation and the guide. A real terminal request/job walkthrough,
  standalone P2P scene and deferred-device artwork remain future gallery additions.

## Capture evidence

- Current captures: commit `545b4a3286d743f97609236e386c90876ca867a3`, `0.0.10-dev`,
  Minecraft 1.21.1 / NeoForge 21.1.250 / AE2 19.2.17 / LDLib2 2.2.34 / GuideME 21.1.1.
- `build/gallery-20261026/ui-mapping/report.json`: PASS, 1 scenario, 48 checks, 47 captures.
- `build/gallery-20261026/ui-domains/report.json`: PASS, 1 scenario, 28 steps, no explicit checks.
- UI screenshots show configured test-world patterns/traffic and creative energy. The renamed
  Calibrated Diamond is a vanilla diamond with a custom name; the existing fixture's processing
  patterns include intentionally extreme quantities. Do not describe them as real recipes or
  benchmarks. Captions and capture notes explain these constraints.
- The existing hero world was copied to `build/gallery-20261026/client/saves/gallery` and
  modified only there. Existing `build_hero_scene.py` regenerated its base/lineup. A temporary
  Gradle init script redirected `runGuideClient` to the isolated client and skipped the shared
  options task. Federation operating power is disabled in that copy, avoiding the temporary
  converter artwork. Main client exited cleanly after captures; original world is retained.
- Additional world structures use actual production blocks: 3x2x2 Router and Switch and three
  linked Beam Former pairs. Exact placement/cameras are in `CAPTURES.md`. Beam capture is idle
  visual animation, not a traffic meter.
- `bundled-cable.webp` reuses the 2026-10-20 3x3 GuideME acceptance shot; old exact build commit
  unrecorded. It is explicitly dated rather than attributed to the new capture commit.
- Blender reference images came from `tools/blender/build/device-study-v2/reference-*.png`.
  Every resource in that study's `production_sha256` inventory still matched current bytes.
  Only current Router/Switch/Provider/Endpoint reference renders were used, not design proposals.

## Verification and capture lessons

- All 16 media files decoded, sizes/frame counts/output hashes checked, teaser bytes matched,
  Markdown links resolved, and final contact sheet visually inspected. UI PNG-to-WebP exports
  preserve source pixels; all animation frames are readable. Validation command lives in
  `docs/gallery/CAPTURES.md` and does not need ignored raw captures.
- `build/gallery-20261026/final-contact.jpg` is a local review sheet, not another deliverable.
- Prefer tracked `tools/visual/game_input.py command` (opens chat with slash) over the old
  scratch `typer.py` when changing GuideME pages: fast Escape/chat transitions can send text
  as chat or leave the pause menu open. Always inspect screenshots before selecting them.
- Bulk cable placement does not form bundles; actual bundles require wrench formation or
  the saved registry. Avoid presenting arrays of unformed ordinary cables as bundled cables.
- Raw screenshots, copied worlds, intermediate PNG/GIF/video and transient orchestration stay
  in ignored build directories. No production implementation changes were needed.
