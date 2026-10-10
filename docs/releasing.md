# Gitflow and GitHub releases

This repository follows the same branch convention as `ControlNet/minecraft-matrix-bridge`.

| Branch | Purpose | CI |
| --- | --- | --- |
| `master` | Published versions | Build, test, and publish a new version to GitHub Releases, Modrinth, and CurseForge |
| `dev` | Integration for the next version | Build and test |
| `feature/*` | Changes based on `dev`; merge back into `dev` | Build and test |
| `release/vX.Y.Z` | Release preparation based on `dev`; merge into `master` and back into `dev` | Build and test |
| `hotfix/vX.Y.Z` | Urgent fix based on `master`; merge into `master` and back into `dev` | Build and test |

Pull requests also run correctness checks. CI does not merge branches or create pull requests.

## Development downloads

Runs on `dev` upload an installable JAR after the build, unit tests, and archive
checks pass. Open the **Quick correctness** run and download its
`ae2federation-neoforge-1.21.1-dev-<commit>-<attempt>` artifact. It contains the
binary JAR, `SHA256SUMS.txt`, and `BUILD.txt` with the full commit and embedded
mod version. The filename identifies the development commit; it does not change
the mod version declared in `gradle.properties`.

Development artifacts and uploaded Quick correctness GameTest logs expire after
7 days. The JAR is available before the GameTest suite finishes; inspect the
final workflow result for full test status. Quick correctness runs every
required manifest GameTest in one server, one after another
(`tools/dev_gametests.py --manifest --ci`), and fails unless that is one
complete pass of exactly the manifest; a test that fails there but passes alone
also fails it. A newer push to the same branch cancels the running check. These downloads do not create a
GitHub Release. Published versions remain available from GitHub Releases.

## Versioning

The first version is **0.0.1**, targeting Minecraft **1.21.1 / NeoForge**.

Set `mod_version` in `gradle.properties`, and set `release_channel` to `alpha`, `beta`, or `release` for Modrinth and CurseForge. Gradle expands it into production and testmod metadata; archive verification also reads this property. Do not edit generated files. Development branches may use a suffix such as `0.0.2-dev`, but a release on `master` must use `MAJOR.MINOR.PATCH`.

The release workflow creates `vX.Y.Z` automatically. Do not manually tag as part of the normal release flow. Versions must increase numerically. A later commit with the same published version runs checks without publishing again.

## Prepare the first release

Start with a clean working tree after committing the README and release infrastructure:

```bash
git switch dev
git pull --ff-only origin dev
git switch -c release/v0.0.1
git push -u origin release/v0.0.1
```

Finish release fixes on this branch and verify:

```bash
./gradlew :neoforge-1.21.1:build --dependency-verification=strict --no-configuration-cache --no-daemon
python3 -m unittest discover -s tests -p test_release_metadata.py -v
python3 -m unittest discover -s tests -p test_artifact_verify.py -v
python3 tools/artifact_verify.py inspect
python3 -B tools/required_gametests.py
git diff --check
```

Expected: `BUILD SUCCESSFUL`, passing Python tests, successful archive inspection, every selected GameTest passing, and no whitespace errors. Test the resulting mod in-game and complete the player-facing release notes before publishing.

Then run the [cross-dimension production run](testing/production-jar.md#cross-dimension-production-run) on the JAR you
are about to publish: a dedicated server and a real client with no testmod, a terminal order through a nether Endpoint
across an unload, and a restart. Record the JAR's SHA-256, the source commit and the result there. A release whose
JAR has no such record is not ready; the required manifest GameTests do not replace this run.

## Upgrade notes

Write these into the player-facing release notes of the release they first ship in:

- **First release after 0.0.5:** the Federation Logic Processor (`ae2federation:federation_logic_processor`) is
  removed and replaced by the Nexus Processor and Nexus Core. It is not migrated and has no alias: Federation Logic
  Processors in inventories, chests or ME storage of an existing world may disappear or become invalid items when the
  world is loaded with the new version. Players should use them up in device recipes before upgrading. Do not describe
  this upgrade as lossless.

## Publish

**Pushing the release merge to `master` publishes to GitHub, Modrinth, and CurseForge automatically once CI passes.** Run these commands when the release is ready:

```bash
git switch master
git pull --ff-only origin master
git merge --no-ff release/v0.0.1 -m "Release 0.0.1"
git push origin master
git switch dev
git merge --no-ff master -m "Merge release 0.0.1 back into dev"
git push origin dev
```

For subsequent releases, use the same sequence with the next version. For hotfixes, start the preparation branch from `master` instead of `dev`. After merging back, set the next development version on `dev`.

## Release automation

The **Release** workflow runs on pushes to `master` and can be rerun manually on `master`. GitHub publication uses the built-in `GITHUB_TOKEN`; only that job receives `contents: write`. Modrinth and CurseForge need these repository secrets:

| Secret | Value |
| --- | --- |
| `MODRINTH_TOKEN` | Modrinth personal access token with *Read projects*, *Read versions*, and *Create versions* |
| `MODRINTH_PROJECT_ID` | Modrinth project ID (`orso4Dml`) |
| `CURSEFORGE_TOKEN` | CurseForge upload API token |
| `CURSEFORGE_PROJECT_ID` | CurseForge project ID (`1713078`) |

Set them from an ignored local `.env` file with the same names:

```bash
gh secret set -f .env -R ControlNet/ae2-federation
gh secret list -R ControlNet/ae2-federation
```

The required manifest GameTests run in sixteen parallel groups, each test in its own GameTest server and assigned exactly once. Publication waits for every group to succeed.

After the build, unit tests, archive validation, and all required manifest GameTests pass, the workflow publishes:

- `ae2federation-neoforge-1.21.1-0.0.1.jar` — install this file.
- `ae2federation-neoforge-1.21.1-0.0.1-sources.jar` — source code for developers.
- `SHA256SUMS.txt` — checksums for both files.

After the GitHub Release is published, the same binary JAR is uploaded to Modrinth and CurseForge with the tags of the manually published 0.0.1 files, plus the Java 21 tag on CurseForge:

| | Modrinth | CurseForge |
| --- | --- | --- |
| Channel | `release_channel` | `release_channel` |
| Minecraft | `1.21.1` | `1.21.1` (Minecraft 1.21 type) |
| Loader | NeoForge | NeoForge |
| Environment | Client and server | Client, Server |
| Java | — | Java 21 |
| Dependencies | Applied Energistics 2, LDLib (required) | — |

The `platform-preflight` job resolves and validates every tag before a tag or release is created; a missing secret or an ambiguous or missing CurseForge tag stops the release. It uses `tools/platform_release.py`. Run `python3 tools/platform_release.py plan` to print the planned metadata, or `preflight` with the four variables set to check it against the live platforms without uploading. The Modrinth upload is read back and verified. CurseForge moderation may delay the file becoming public.

Before uploading, each platform job checks whether that platform already has this version: Modrinth by version number (authenticated, so versions under review count), CurseForge by file name in the public file list. An existing version is skipped, so a re-run or a manual dispatch on `master` only uploads what is missing. If the CurseForge list cannot be read, the job fails instead of uploading. A newly uploaded CurseForge file may stay out of the public list until moderation finishes, so prefer **Re-run failed jobs** over a full re-run shortly after an upload.

Release notes are generated by GitHub. The workflow uploads to a draft first, then publishes it after all uploads succeed. If publishing fails, rerun the failed workflow: it can reuse a tag at the same commit and resume a draft. Existing published releases are left unchanged, and tags are never moved. Do not delete or move a release tag to retry.

An unchanged version on a later commit does not repair an earlier incomplete release; rerun the original failed run. A manually dispatched run on a branch other than `master` is skipped.

## CI scope

The release checks establish build and automated gameplay correctness for the declared dependency versions. The separate optional qualification workflow retains the longer compatibility and reproducibility checks. Publishing does not imply completion of every historical performance or compatibility qualification.
