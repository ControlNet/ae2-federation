# Release 0.0.8 without placeholder-art blocks (2026-10-24)

The owner wanted 0.0.8 to ship without blocks whose art is still placeholder: the ME Federation Beam Converter, the
Creative Beam Converter and the ME Federation Quantum Cube (the 3x3 Beam Former art landed just before, PR #7). The
exclusion is temporary; 0.0.9 is meant to carry their final art.

## Approach (owner's choice)

- One commit on `release/v0.0.8` only (`debd60f6`), on top of "Prepare 0.0.8" (`1d77ee25`). After master is merged
  back into `dev`, `git revert debd60f6` on `dev` at once, so `dev` keeps the blocks and 0.0.9 needs no undo. A Gradle
  flag on `dev` was rejected: the guide would need two versions (91 guide files mention the converter).
- Operating power is locked off: `OperatingPowerRule.required` defaults to false and the `[power]` config section is
  removed (with no converter, nothing could pay). The topology gauge already hides when power is not required; the
  Statistics Federation power card is skipped in `FederationStatisticsView`.

## What the commit touches

- Unhooked, classes kept: `PowerRegistration.register` and `QuantumRegistration.register` in `NeoForgeEntrypoint`,
  creative tab entries, `DeviceStructureBakedModel.bake` for the cube (its `.get()` would throw at model bake), the Jade
  providers for both (their config toggles would list them).
- Deleted: blockstates, block/item models, textures, recipes, loot tables, recipe advancements, the pickaxe tag
  entries, lang keys (block names, Jade quantum lines, cube messages, Jade config names).
- Guide: `git revert --no-commit c852559a` (the commit that put a converter in every example) applied cleanly; the rest
  (Power page cut to ME power, getting started, item and mechanics pages, three scenes, the Prototype example) was
  done by Codex gpt-6.1-sol from a brief and reviewed.
- Tests: `BeamConverterGameTests`, `QuantumCubeGameTests`, `OperatingPowerGameTests` deleted; converter parts cut from
  `BeamFormerGameTests`, `CableLinkParityGameTests`, `DeviceStructureGameTests`, the UI showcase; 15 manifest cases
  removed (342 left, 273 required). `OperatingPowerCostsTest` now turns the rule on in `@BeforeEach`.

## Checks that passed

Strict build with 787 unit tests, release metadata and artifact tests, `artifact_verify.py inspect`, the manifest
batch 273/273, Quick correctness and Compatibility on the release head, `validate_assets.py`, `git diff --check`, no
`beam_converter`/`quantum_cube` entry in the JAR, all 32 development guide pages compiled by `runGuideClient` with no
PageCompiler warning.

## Guide screenshots with scrolling

`runGuideClient -PguidePage=<page>` under a private `Xvfb :N` (pick a free N from `/tmp/.X11-unix`; other sessions
hold some), wait for the "Compiling ae2federation" lines, then scroll with python-xlib from the `tools/visual` pixi
env (`xtest.fake_input` button 5 at the content area, about 15 px per notch) and `import -window root`. Kill the
client Java, the Gradle wrapper and the Xvfb by literal PIDs afterwards.
