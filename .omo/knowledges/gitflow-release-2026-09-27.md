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
