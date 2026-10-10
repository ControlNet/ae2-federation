# Gitflow and initial GitHub release

- Follow the owner's `ControlNet/minecraft-matrix-bridge` convention: `master` publishes, `dev` integrates, `feature/*` branches return to `dev`, and `release/vX.Y.Z` / `hotfix/vX.Y.Z` merge into `master` and back into `dev`.
- First version: `0.0.1`. `gradle.properties` is authoritative for production/testmod resource expansion, archive names, and expected artifact manifest versions.
- `.github/workflows/release.yml` runs on `master` pushes or manual dispatch on `master`. It builds and verifies production archives, runs Python regression tests and all required manifest GameTests, then publishes a tag, binary, sources, and checksums to GitHub Releases only.
- Publishing is a separate job with `contents: write`; build jobs have read access. Draft upload allows retry after an interrupted upload. Never overwrite published assets or move existing tags. An unchanged version on a later commit skips publication; rerun the original failed run to resume an incomplete release.
- The public filename includes NeoForge and Minecraft version; internal Gradle archive names remain compatible with existing tooling.
- Developer/release details belong in `docs/releasing.md`, linked briefly from the player README.
- Unit tests use temporary metadata for version planning, and the existing archive lifecycle tests use explicitly synthetic artifacts. These are test-only inputs, not release assets.

## Local verification

- Gradle production build passed: 253 JUnit tests, no failures/errors/skips.
- Five release metadata tests and 15 archive regression tests passed, including rejection of mismatched packaged mod versions.
- `harnessnativesmoke` ran exactly one required GameTest successfully with the expanded 0.0.1 metadata. The full manifest suite is configured in CI; it was not rerun locally for this change.
- actionlint 1.7.7 passed for both changed workflows. The exact workflow packaging script produced both release JARs and passing SHA-256 checksums under ignored `build/release/`.
- GitHub publication itself has not been exercised; no tag or remote release was created during setup.

## First publication execution

- The initial serial release run was canceled before publication to partition the 152 required GameTests across eight runners. `select_shard` tests verify complete, unique, nonempty coverage; the no-argument command still runs the entire suite. Publication depends on all eight groups.

- First parallel CI exposed intermittent `provenanceopaqueboundary` setup failure: the cable initialized as an independent grid before the chest/power-cell grid, leaving durable conflicting network identities after the grids joined. The failure log had three initial native nodes and no alias-provider node, so it preceded the provenance assertion. A standalone local replay passed. `ProvenanceStorageFixture.ready()` now places its cable only after the chest grid is active, booted, and settled; the native provenance assertions are unchanged.

## Published v0.0.1

- Release: https://github.com/ControlNet/ae2-federation/releases/tag/v0.0.1
- Successful CI: https://github.com/ControlNet/ae2-federation/actions/runs/36254274818
- Tag and master commit: `d040046be0fc07b32bfb51e781e09e6bae93b553`.
- All eight GameTest groups passed. The downloaded CI log contains exactly 152 unique passing IDs, matching the complete manifest. The build, 253 JUnit tests, 24 Python tests, archive checks, checksums, and publication passed.
- The four provenance GameTests also passed locally after staging fixture cable placement. The existing source-contract test requires the explicit `confirmedNetworkId(grid()).isPresent()` expression; it is retained in the staged readiness predicate.
- Downloaded public assets are byte-identical to the final CI artifacts; mod metadata reports `0.0.1`. Local delivery folder: ignored `build/releases/v0.0.1/`.
- Binary: `ae2federation-neoforge-1.21.1-0.0.1.jar`, 1,016,669 bytes; SHA-256 `ca8eb3e26ca3e0e701ff168031e2b0288363d814969e1cbda7f7da7055616737`.
- Sources SHA-256: `746bffdcb93a71391edf219dece52b0287cff7095a0efe709f932ea2163b2414`.
- User-facing release notes describe dependencies, installation, no survival recipes, and the optional cable preview. No Modrinth or CurseForge project was created; the owner will create those pages and upload the binary.
- Release changes were merged back into `dev`; the published tag remains fixed.

## Published v0.0.3 (2026-10-02)

- Flow: `release/v0.0.3` from `dev` (commit "Prepare 0.0.3": `mod_version`, README version line and crafting
  paragraph) → `git merge --no-ff release/v0.0.3 -m "Release 0.0.3"` on `master` → `git merge --no-ff master -m
  "Merge release 0.0.3 back into dev"`. Master `a43cc3f`; tag `v0.0.3` created by CI points at it.
- Release run 36968419366: build, platform preflight, eight GameTest groups (165 tests, each once, including the
  `crafting.remote-*` cases), GitHub publish, Modrinth and CurseForge all succeeded.
- Binary `ae2federation-neoforge-1.21.1-0.0.3.jar`, 1,289,571 bytes, SHA-256
  `2bc4c27d61f0d7d96d55d6a6b85d9b17ffd778240d3cebf26b77f9e8be6a6d99`, identical to the local `artifact_verify.py
  inspect` hash; sources SHA-256 `db92505d21457bc32720ce9f6d7ba2bf6e9dee04deb84993e4eb248666ea6cd6`.
- Local pre-release gate: full manifest twice, federationVerify Tasks 26–29 and the remote crafting group, UI
  harness, CI build, the five Python suites. The Task 40 docs QA suite fails on its README rule (it requires
  qualification phrases the player README no longer has); it was failing before this release and is not run by the
  release workflow.
- `git rev-parse --short A B` fails with "Needed a single revision" in this setup; query one ref per call.
- Release notes: CI's `--generate-notes` leaves only a "Full Changelog" line. Write player-facing notes in the 0.0.2
  layout (title, dependency line, upgrade callout, sections, Full Changelog link), check them with
  `python3 tools/privacy_guard.py message <file>`, then `gh release edit vX.Y.Z --notes-file <file>`. Modrinth and
  CurseForge changelogs only link to the GitHub release, so they need no edit. Done for 0.0.3 after publication.
- Since 2026-10-02 the release workflow runs GameTests in 16 shards, and Quick correctness on dev runs them in one
  batch server (`dev_gametests.py --manifest --ci`); see `gametest-speed-2026-09-29.md`.

## Published v0.0.4 (2026-10-04)

- Flow, done in a separate `git worktree` so the shared main tree stayed on `dev`: `release/v0.0.4` from `dev`
  ("Prepare 0.0.4": only `mod_version`, the README has no version line any more) → a late fix landed on `dev`
  (Router centre = Federation Logic Processor) and was merged into the release branch → Quick correctness green on
  the release head → `git merge --no-ff` into `master` ("Release 0.0.4", `22684d7`, tag `v0.0.4` by CI) → `master`
  merged back into `dev` ("Merge release 0.0.4 back into dev").
- Release run 37175201014: build, platform preflight, 16 GameTest shards (164 distinct manifest tests, each in its own
  server, all passed), GitHub, Modrinth and CurseForge publish all succeeded.
- Binary `ae2federation-neoforge-1.21.1-0.0.4.jar`, 1,335,893 bytes, SHA-256
  `9b06f5169f96ebe24dce862318f46ed3080bd0ee8bc45a42e20164850e8a819c`; sources SHA-256
  `9bcc29c0fde75376a8f59fbbcc433c9906807073e542ac8ceac913bcd2f5928b`. Downloaded assets pass `sha256sum -c`, and the
  jar's `router.json` and `neoforge.mods.toml` (0.0.4) were checked.
- Player notes replaced the generated "Full Changelog" body. Keep each list item on one line, as in 0.0.3: GitHub
  release bodies may render a wrapped line as a hard break. Upgrade callout for 0.0.4: 0.0.3 remote crafting jobs are
  not carried over, a Crafting rule gains its Storage rule on load, claimed Endpoints share ME power by default.
- The owner kept 0.0.x (not 0.1.0) because the build has not been played through in survival yet.

## Published v0.0.5 (2026-10-05)

- Same flow as 0.0.4 in a separate worktree: `release/v0.0.5` from `dev` ("Prepare 0.0.5": only `mod_version`) →
  Quick correctness and Compatibility (addon and modpack profiles) green on the release head → `git merge --no-ff`
  into `master` ("Release 0.0.5", `5f8b61e`, tag `v0.0.5` by CI) → `master` merged back into `dev`.
- Release run 37311588158: build, platform preflight, 16 GameTest shards, GitHub, Modrinth and CurseForge publish
  all succeeded.
- Binary `ae2federation-neoforge-1.21.1-0.0.5.jar`, 1,511,268 bytes, SHA-256
  `a463220aa3b3cd35b2ce151e81733b3db4f067f714b2699401347ddd13b8c9ab`, identical to the local
  `artifact_verify.py inspect` hash; sources SHA-256 `2d72829b6ea930f38267ff0f7a7d868ea6edbda45bc7d75aa76b5c0b7d88fc06`.
  The jar's `neoforge.mods.toml` reads version 0.0.5, NeoForge `[21.1.216,)`, FML `[4,)`.
- `test_artifact_verify.py` needs the built jar; run it after `./gradlew :neoforge-1.21.1:build`, as CI does, or
  its tests error with FileNotFoundError.
- Notes: upgrade callout says worlds load as they are (no save-format change since 0.0.4, only more lenient loading)
  and that 0.0.4 refused other NeoForge builds; sections "Modpacks and addons" and "In-game guide".

## Published v0.0.6 (2026-10-10)

- Same flow in a separate worktree: `release/v0.0.6` from `dev` ("Prepare 0.0.6": only `mod_version`) → Quick
  correctness and Compatibility (all addon profiles plus ATM10, CUS2, neoforge-min) green → master "Release 0.0.6"
  (`391e505`). Release run 38066437883 failed one of 16 GameTest shards (`projectioncpulateafterrestart`, a test-only
  race: see `gametest-chunk-unload-race-2026-10-10.md`), so publish, Modrinth and CurseForge were skipped and no tag
  was made. The fix landed on dev (`7544489`), was cherry-picked onto the release branch, Quick correctness passed
  again, and a second master merge (`8749cf4`) ran release 38068473359: 16/16 shards, GitHub, Modrinth and CurseForge
  published. Tag `v0.0.6` points at `8749cf4`. An unpublished version can simply be released again from a later master
  commit; nothing had to be reverted.
- Binary `ae2federation-neoforge-1.21.1-0.0.6.jar`, 2,105,913 bytes (+~0.5 MB: the canvas CJK font). Downloaded
  assets pass `sha256sum -c`; the jar's `neoforge.mods.toml` reads 0.0.6, NeoForge `[21.1.216,)`, AE2 `[19.2.9,)`,
  LDLib2 `[2.2.34,)`. Before release, production code compiled against AE2 19.2.9 + LDLib2 2.2.34 + GuideME 21.1.1
  (`-Pae2_version=19.2.9 --dependency-verification=lenient`), and no mixin changed since 0.0.5.
- Notes: upgrade callout (the Router no longer joins ME networks, use a Switch; the Federation Logic Processor is gone
  and every recipe is new; saved Storage rules stay on); sections New blocks and items, Networks and crafting, Look and
  interface, Modpacks and addons, In-game guide. The owner chose not to migrate 0.0.5 Routers or keep the processor.
