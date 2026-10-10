# Mod page screenshots (0.0.6, 2026-10-10)

Supersedes `release-image-shortlist-2026-09-27.md` for gallery images. Output (gitignored):
`.omo/evidence/release-0.0.6-screenshots/` — 1600x960 PNGs, uploaded by hand to Modrinth/CurseForge.

## GUI shots (UI test client)

```sh
xvfb-run -a -s "-screen 0 1600x960x24" ./gradlew :neoforge-1.21.1:runUiTestClient \
  -PfederationUiSelection=ui.mapping -PfederationUiOutputDir=<dir> \
  -PfederationUiWindow=1600x960 -PfederationUiGuiScale=3 -PfederationUiWatchdogSec=300
```

Screenshots land in `<dir>/screenshots/ui.mapping/NNN_<name>.png` (~5 min, 48 checks). Best for a gallery:
`ui-showcase-topology` (8 named networks, Main Base selected), `ui-showcase-processing`, `ui-showcase-provider`.
`ui-showcase-pair` / `-zoom` / `-topology-remote` carry a stuck "Hide live flow" tooltip.
Caveat: the showcase runs on Creative Energy Cells, so the network detail shows "4612T / 4612T AE".

## Guide shots (GuideME 3D scenes)

Start `Xvfb :91 -screen 0 1600x960x24 +extension GLX`, then
`DISPLAY=:91 ./gradlew :neoforge-1.21.1:runGuideClient -PguidePage=ae2federation:<page> [-PguideLanguage=zh_cn]`,
wait for "Compiling ae2federation:index" + ~25 s, `DISPLAY=:91 import -window root out.png`.
To switch page, kill the java process matching `[g]uideClientRunVmArgs` (plain patterns match your own shell).
Good pages: `examples/market-hub.md` (scene + topology diagram), `across-domains.md`, `items/nexus_core.md`.

## Badges

README links `modrinth.com/mod/ae2-federation` and `curseforge.com/minecraft/mc-mods/ae2-federation` (slug checked
via WebFetch of `curseforge.com/projects/<id>`; curl gets Cloudflare 403).
