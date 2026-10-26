# Placeholder-free release assessment (2026-10-26)

## Scope and baseline

- Read-only implementation assessment at `dev` `9840c48d`; no release changes or publication performed.
- `gradle.properties` still declares 0.0.8. Local `master` is `6a141db4` (Release 0.0.8).
- The previous exclusion is `debd60f6`, restored on development by `fd9ddd40`. See
  `release-0.0.8-placeholder-exclusion-2026-10-24.md` for historical validation, not current validation.
- Current art documentation identifies ME Federation Beam Converter, Creative ME Federation Beam Converter and
  ME Federation Quantum Cube as temporary art. These are the full English names confirmed in `en_us.json`;
  `Creative Beam Converter` is only shorthand, not a separate block.
  Beam Former has artist models for all three sizes. Cable placeholder JSON is an input to custom baked geometry,
  not evidence that the visible cable should be excluded. Switch has artist textures and connected tiles.

## Reuse check

- `git show --format= --binary debd60f6 | git apply --check` fails only for deletion of
  `common/src/testmod/java/space/controlnet/ae2federation/test/OperatingPowerGameTests.java`.
- The file's only change since restoration is a two-line comment explaining game-time statistics.
- Adding `--exclude=common/src/testmod/java/space/controlnet/ae2federation/test/OperatingPowerGameTests.java`
  makes the patch check pass. Neither check modifies files. This checks textual applicability, not runtime behavior.
- Seven files in the original patch changed after restoration: the two statistics guide pages,
  DeviceStructureGameTests, FederationTestMod, OperatingPowerGameTests, NeoForgeEntrypoint and the scenario manifest.
- New compatibility playthrough coverage needs separate adjustment: `PlaythroughRemote.initialize()` directly
  accesses `QuantumRegistration.QUANTUM_CUBE`, selected by the `quantum` topology in `PlaythroughTopology` and
  `PlaythroughFactory`. The old exclusion patch predates this code. The Playthrough workflow runs on non-master pushes.

## Release implications

- Reuse a release-only exclusion commit, retaining the development implementation, rather than removing Java
  subsystems or introducing a general build-profile framework for a single release.
- Unregistering converters requires locking Federation operating power off, including config reload behavior;
  merely hiding items or removing recipes is insufficient. Native ME network energy sharing remains separate.
- Synchronize creative entries, model baking, Jade, recipes/tags/assets, guide pages/scenes, UI and relevant tests.
- Existing development worlds containing excluded blocks require an upgrade warning; 0.0.8 already excluded them.
- Estimated engineering effort: low to moderate, approximately half to one working day for adaptation and ordinary
  checks; reserve one to two working days including full release validation and client checks. This is an estimate,
  not measured execution time. No build, GameTest suite or client validation ran during this assessment.
