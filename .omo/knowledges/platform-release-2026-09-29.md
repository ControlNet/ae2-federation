# Modrinth and CurseForge release automation

## Manually published 0.0.1 (source of truth for tags)

- Modrinth project `orso4Dml` (slug `ae2-federation`, status processing/under review on 2026-09-29). Version
  `eRN7VfQD`: name and version_number `0.0.1`, channel alpha, game_versions `1.21.1`, loader neoforge, environment
  `client_and_server`, single primary file `ae2federation-neoforge-1.21.1-0.0.1.jar` (no sources jar), required
  dependencies `XxWD5pD3` (Applied Energistics 2, slug `ae2`) and `B1CBVXHX` (LDLib, slug `ldlib`; it publishes the
  `ldlib2-neoforge-1.21.1-*` files). Project environment `["client_and_server"]`.
- CurseForge project `1713078`, file `8982557`: Alpha, `1.21.1`, NeoForge, Client & Server, display name = filename,
  no relations, same changelog. The public page does not show Java tags, so the original Java tag is unknown; CI adds
  Java 21.
- Unapproved projects are invisible to anonymous Modrinth API calls (404) and to the ControlNet user project list;
  use `Authorization: <PAT>`. The CurseForge upload token reads only `/api/game/versions` and `/version-types`
  (`/api/projects/<id>/files` is 403); `www.curseforge.com` blocks curl with Cloudflare 403, but WebFetch could read
  the public files list and file page after the project became visible.
- CurseForge name `1.21.1` maps to three IDs (11779 type "Minecraft 1.21", 12735 untyped, 16115 "Addons"). Resolve by
  (name, type name); IDs used: 11779, NeoForge 10150, Java 21 11135, Client 9638, Server 9639.

## Implementation

- Mirrors `ControlNet/minecraft-matrix-bridge`: secrets `MODRINTH_TOKEN`, `MODRINTH_PROJECT_ID`, `CURSEFORGE_TOKEN`,
  `CURSEFORGE_PROJECT_ID`; actions `cloudnode-pro/modrinth-publish@v2` and `itsmeow/curseforge-upload@v3`
  (numeric game_versions IDs pass through unchanged; display name defaults to the filename).
- `tools/platform_release.py` (stdlib): `plan`, `preflight`, `modrinth-exists`, `curseforge-exists`,
  `verify-modrinth`. Channel comes from
  `release_channel` in `gradle.properties` because master versions must be plain `X.Y.Z`.
- `platform-preflight` job runs before the tag is created. Platform jobs run after the GitHub publish job and skip
  when the platform already has the version (user request 2026-09-29): Modrinth `modrinth-exists` by version_number
  with the PAT; CurseForge `curseforge-exists` by fileName/displayName via the public, paginated
  `https://www.curseforge.com/api/v1/mods/<id>/files?pageIndex=N&pageSize=50&removeAlphas=false` (no token; plain
  curl works; `pagination.totalCount` is reliable). A listing failure fails the job. Files pending moderation may be
  missing from that list, so prefer "Re-run failed jobs" right after an upload.
- That public listing omits Java tags even for matrix-bridge files that CI uploaded with Java, so it cannot tell
  whether 0.0.1 had a Java tag. 0.0.1 `gameVersions`: Client, 1.21.1, NeoForge, Server; releaseType 3 = alpha.
- Local `.env` (git-ignored) holds the four variables; `gh secret set -f .env` copies them to the repository.

## Verification (2026-09-29)

- 11 synthetic-catalog unit tests; release metadata and GameTest selection tests; actionlint 1.7.7; `git diff --check`.
- Live read-only `preflight` resolved every tag; `modrinth-exists` returned true for 0.0.1; `verify-modrinth eRN7VfQD`
  confirmed the CI plan reproduces the manual 0.0.1 version. `curseforge-exists`/`modrinth-exists` return
  true for 0.0.1 and false for a simulated 0.0.2. No upload was performed; no CI run was triggered.
