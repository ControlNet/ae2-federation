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

## In-world shots (production client, real networks)

Images 09-12 in the evidence folder. Install: `build/visual-v07/{server,client}` with the verified release jar
`ae2federation-neoforge-1.21.1-0.0.6.jar` (old `0.1.0-dev` jars moved to `build/visual-v07/old-jar/`).
`server.properties`: `level-name=hero-world`, `level-type=minecraft:normal`, `level-seed=20261010`, creative,
monsters off; plains near (592, 68, 900). Steps:

```sh
cd build/visual-v07/server && mkfifo cmd && (tail -f cmd | ./run.sh nogui > ../server-hero.log 2>&1 &)
Xvfb :79 -screen 0 1920x1080x24 +extension GLX & echo $! > build/visual-v07/xvfb.pid   # kill by this PID only
DISPLAY=:79 LIBGL_ALWAYS_SOFTWARE=1 pixi run --manifest-path tools/visual/pixi.toml portablemc \
  --main-dir build/ae2f-work/prod-client --work-dir build/visual-v07/client start --jvm /usr/bin/java \
  --resolution 1920x1080 --jvm-args='-Xmx4G' -u VisualProbe -s 127.0.0.1 -p 25579 neoforge:21.1.250
python3 tools/visual/build_hero_scene.py build/visual-v07/server/hero-world
echo reload > build/visual-v07/server/cmd; echo "function hero_scene:base" > ...; echo "function hero_scene:lineup" > ...
```

Camera: `gamemode spectator VisualProbe`, `tp VisualProbe <pos> facing <pos>` (facing aims from the feet; subtract
~1.6 from the target Y to aim the eyes), `game_input.py F1`, `import -window root`. Base hero:
`583 72.5 902 facing 587.5 70.5 896`; lineup: `550.5 73.6 906.4 facing 550.5 72.5 896`.

Gotchas:
- The Nexus circuit/processor/core textures are animated with interpolation, and the AE2 controller cycles colours:
  burst-capture (12 shots, 0.25-0.5 s apart) and pick a clean frame. A doubled item is a blended frame, not a bug.
- After re-running a function, teleport away (~400 blocks) and back so the client drops stale entities.
- Energy comes from full Dense Energy Cells (no Creative cells), so the Federation screen shows honest numbers.
  Federation screen shot: creative, stand in front of Switch A, `game_input.py rightclick`; rename networks and
  toggle rules with an XTest click/typing helper (window coordinates = screen coordinates at 1920x1080).
- `sendCommandFeedback false` hides command errors; switch it on to debug.
