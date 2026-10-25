# Newest modpack releases in the weekly compat run (2026-10-24)

`tools/compat_run.py --latest <profile>` runs a modpack profile on the pack's newest CurseForge release instead of its
pinned server zip. `tools/compat_run.py latest --group modpacks` only compares pins with the newest releases (no
download). The `Newest modpacks` workflow (`.github/workflows/latest-modpacks.yml`, weekly and on dispatch) runs it
apart from `Compatibility`, so a pack update shows as its own red run. It reports only: it never edits pins, commits,
or opens issues. An empty `workflow_dispatch` string input falls back to its default, which is why the first version
(a `latest` job inside compat.yml) needed a `groups=none` value; the separate workflow removed both.

## CurseForge without a key

- File list: `https://www.curseforge.com/api/v1/mods/<project>/files?pageIndex=0&pageSize=50&sort=dateCreated&sortDescending=true&removeAlphas=true`.
  `removeAlphas=true` still returns betas (`releaseType` 2), so filter `releaseType == 1` and
  `{"1.21.1", "NeoForge"} <= gameVersions` yourself.
- Additional files: `.../mods/<project>/files/<fileId>/additional-files` (note the `files/` segment).
- Server pack marking: only on the parent file (`hasServerPack`, `additionalServerPackFilesCount`). The additional
  file object has no flag; `isCompatibleWithClient: false` and the file name are not reliable. Evidence: All the Mods
  9 1.1.0 (file 6985843) has an additional `Server-Files-1.1.0.zip` with `additionalServerPackFilesCount: 0`. The
  runner takes the server pack only when it is the parent's one additional file and errors otherwise.
- `www.curseforge.com/api/v1/mods/<id>` (project detail) is 403; website search and HTML pages hit a Cloudflare
  challenge. `https://api.cfwidget.com/minecraft/modpacks/<slug>` gives the project id.
- Downloads: `https://mediafilez.forgecdn.net/files/<id // 1000>/<id % 1000>/<url-quoted fileName>` works without a
  key (range request 206). `edge.forgecdn.net` 404s without a key; the website `/download` redirect adds a CDN key:
  never use it.

Project ids: ATM10 925200, CUS2 1324409 (slug create-ultimate-selection-2), NAST 1582045 (slug
new-age-science-and-technology).

## NeoForge version per pack

`pack_neoforge()` checks, in order: ServerPackCreator `variables.txt` (`MODLOADER=NeoForge`, `MODLOADER_VERSION`),
`manifest.json` (ServerPackCreator `modloaderVersion` or CurseForge `minecraft.modLoaders[].id`), `NEOFORGE_VERSION=` in
`*.sh`/`*.bat`/`*.ps1`, `libraries/net/neoforged/neoforge/<v>`, an installer jar's `install_profile.json`.

- ATM10: `startserver.sh` / `startserver.bat` `NEOFORGE_VERSION=`, plus `neoforge-<v>-installer.jar`.
- CUS2: `variables.txt` and ServerPackCreator `manifest.json`.
- NAST: only the installer, renamed `neoforge-1.21.1.jar`; read `install_profile.json` (`"version": "neoforge-21.1.251"`),
  never the file name.

## Results on 2026-10-24

| Pack | Pinned | Newest | NeoForge | Result |
|---|---|---|---|---|
| atm10 | 8.2 (21.1.251) | 8.3, file 9190182 | 21.1.259 | 91/91 |
| cus2 | 12.4.0 (21.1.243) | 12.9.0, file 9299724 | 21.1.256 | 59/59 |
| nast | 2.2.1 | same (file 9286724) | 21.1.251 | not run again |

Neither new pack ships LDLib2, so the profiles' added LDLib2 2.2.41 does not collide. If a pack starts shipping a mod
the profile adds, the latest run will fail with a duplicate mod; that is a harness problem, not AE2 Federation's.

Sizes: ATM10 8.3 server zip 1.24 GB, unpacked 1.4 GB; CUS2 12.9.0 zip 785 MB, unpacked 844 MB; a NeoForge server
install about 180 MB. The latest job never uses actions/cache (the repository cache is over its limit).
