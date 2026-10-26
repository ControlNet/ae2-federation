# Gallery planning inventory (2026-10-26)

The owner requested a plan for a comprehensive `docs/gallery` image collection, including
`docs/teaser.webp`, primarily stills with optional GIFs. Reuse assets and tooling before
writing new code. This turn inventories sources and proposes scope; it does not produce
or migrate gallery media.

## Confirmed reusable sources

- `docs/teaser.webp`: existing README hero, visually inspected. Keep the original image
  in the proposed collection; preserve its current public path initially.
- `.omo/evidence/release-0.0.6-screenshots/`: 12 local PNGs covering topology, processing,
  providers, English/Chinese GuideME, base, smeltery, lineup and Federation screen.
  `09-world-base.png` was visually inspected: useful real-world layout, but old cable
  appearance and considerable empty sky. Prefer recapturing the scene on current assets.
- `build/topology-showcase-check/after-3-domains/topology.png`: visually inspected,
  readable multi-domain composition. Creative energy gives `922T / 922T AE`; names such
  as Nether Outpost do not prove cross-dimension placement (the shot says Overworld).
- `common/src/main/resources/assets/ae2federation/ae2guide/assets/statistics.png` and its
  `_zh_cn` counterpart: tracked guide screenshots. English image visually inspected;
  it predates the Federation power metric. Use the current UI scenario for a fresh shot.
- `build/multiblock-devices/`: extensive local Router/Switch Blender and in-game studies,
  including obsolete intermediate designs. Verify current production geometry per image.
- `build/beam-portal-check/`, `build/bundle-perf-check/`, `build/iris-check/`: local
  in-game beam/bundle evidence exists. Paths include before/after, unpowered and shader
  variants; existence alone does not make them approved gallery images.

## Existing production workflows

- `tools/visual/build_hero_scene.py`: datapack functions `base`, `lineup`, `beam`,
  `capacity`. Uses real game blocks. Functions clear/replace terrain: execute only in a
  disposable gallery world, never an existing player's world.
- UI scenario `ui.showcase-domains` (`DomainsShowcaseScenario`): topology captures;
  reproduction command in `topology-domains-showcase-2026-10-22.md`.
- UI scenario `ui.mapping` (`TaskThirtyThreeMappingScenario`): showcase rules,
  processing, providers and many statistics views, including Federation power.
  Test-world scripted activity must not be presented as player-server measurements.
- `runGuideClient`: existing English/Chinese GuideME scenes. Use current page IDs under
  `ae2guide`, since old release notes reference some outdated pages.
- `tools/blender/`: imports actual models/textures and exported cable shells; reusable
  cameras, scenes and animation composition. The generic baseline still uses historical
  flow geometry. Match current R/LITE or R/PLASMA before using new renders as product art.
- `tools/multiblock/`: geometry and runtime-parity tooling for formed devices.
- Pillow work uses the already approved `tools/visual/pixi.toml` environment. Blender
  scripts use Blender's own Python. No new environment or package install is needed.

## Proposed scope and caveats

Aim for 14-16 stills: teaser, real installation, topology, rules, shared storage,
remote crafting, remote processing, ME energy sharing, statistics, device family,
ordinary/bundled cables, formed Router/Switch, Beam Former, P2P, Quantum connection,
and in-game guide. Federation operating power can share the statistics/device material;
keep it distinct from sharing AE energy between networks. Add at most 1-2 short loops.

The Quantum Cube and Beam Converter alternatives in
`tools/blender/build/device-alternatives-final/alternatives.blend` are proposals, not
shipped appearances. Keep them in design notes or an explicitly labelled future-design
section; do not substitute them for current gameplay images. Existing temporary runtime
art is documented in the feature notes and is not newly introduced by this plan.

Prefer a flat final-media directory plus a hand-maintained `docs/gallery/README.md`
with captions, source type, capture version, source location and reproduction entrypoint.
Leave raw captures, frames and blend files in ignored build/evidence locations. Gallery
viewing must work from a fresh clone without those local sources. Start with selection
and recapture through existing tools; extend scene setup only for proven coverage gaps.
