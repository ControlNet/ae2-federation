"""Render guide structures from chosen cameras with the GuideME client, for design reviews of models in game.

    python tools/visual/guide_views.py OUT VIEWS.json [--extra DIR] [--display :95]

VIEWS.json lists [name, structure, zoom, yaw, pitch]; structure is a path under the guide's assets directory without
.snbt (for example "bundled_cables" or "examples/market_hub"). SNBT files in --extra are copied into the assets
directory first, so a review can render structures of its own. Each view is cropped to its scene panel as OUT/name.png.
The guide is copied into OUT, so the shipped pages stay untouched. Run it in the tools/visual pixi environment; it needs
Xvfb and ImageMagick `import`, as tools/visual/promo/scenes.py does.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent / 'promo'))
import scenes  # noqa: E402

def page(guide, structure, zoom, yaw, pitch):
    target = guide / scenes.PAGE
    tmp = target.with_suffix('.tmp')
    tmp.write_text(f"""---
navigation:
  parent: index.md
  title: Promo scene
---

# Promo scene

<GameScene zoom="{zoom}" background="#0a0f16">
  <ImportStructure src="../assets/{structure}.snbt" />
  <IsometricCamera yaw="{yaw}" pitch="{pitch}" />
</GameScene>
""")
    tmp.replace(target)

def main(out, views, extra, display):
    out = Path(out).resolve(); out.mkdir(parents=True, exist_ok=False)
    guide = out / 'ae2guide'; shutil.copytree(scenes.GUIDE, guide); (guide / 'promo').mkdir()
    for snbt in Path(extra).glob('*.snbt') if extra else []:
        shutil.copy(snbt, guide / 'assets' / snbt.name)
    client = out / 'client'; client.mkdir()
    (client / 'options.txt').write_text('onboardAccessibility:false\nguiScale:2\nlang:en_us\n')
    init = out / 'init.gradle'
    init.write_text(f"""gradle.projectsEvaluated {{
    def p = gradle.rootProject.project(':neoforge-1.21.1')
    p.neoForge.runs.named('guideClient') {{
        gameDirectory = file('{client}')
        systemProperty('guideme.ae2.guide.sources', '{guide}')
        programArguments.empty()
        programArguments.addAll('--width', '1600', '--height', '1000')
    }}
    p.tasks.named('prepareGuideClientOptions') {{ enabled = false }}
}}
""")
    page(guide, *views[0][1:])
    env = dict(os.environ, DISPLAY=display); log = out / 'client.log'
    xvfb = game = None
    try:
        xvfb = subprocess.Popen(['Xvfb', display, '-screen', '0', '1600x1000x24', '+extension', 'GLX'],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        time.sleep(2)
        with log.open('w') as s:
            game = subprocess.Popen(['./gradlew', '--no-daemon', '-I', str(init), ':neoforge-1.21.1:runGuideClient',
                                     '-PguidePage=ae2federation:' + scenes.PAGE], cwd=scenes.ROOT, env=env,
                                    stdout=s, stderr=subprocess.STDOUT, start_new_session=True)
        scenes.wait_for(log, 'Compiling ae2federation:' + scenes.PAGE, 900)
        time.sleep(12)
        for name, *view in views:
            page(guide, *view); time.sleep(8)
            raw = out / ('raw-' + name + '.png')
            subprocess.run(['import', '-window', 'root', str(raw)], env=env, check=True)
            try:
                scenes.crop_scene(raw, out / (name + '.png')); print('VIEW_OK', name, flush=True)
            except RuntimeError as error:
                print('VIEW_FAIL', name, error, flush=True)
    finally:
        scenes.stop(game); scenes.stop(xvfb)
    if 'ParseException' in log.read_text(errors='replace'):
        print('PARSE_EXCEPTION in log')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('output', type=Path, help='new directory for the renders')
    parser.add_argument('views', type=Path, help='JSON list of [name, structure, zoom, yaw, pitch]')
    parser.add_argument('--extra', type=Path, help='directory of extra .snbt structures')
    parser.add_argument('--display', default=':95', help='free X display number for Xvfb')
    args = parser.parse_args()
    main(args.output, json.loads(args.views.read_text()), args.extra, args.display)
