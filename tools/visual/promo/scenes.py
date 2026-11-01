"""Render guide example scenes for the promotional posters with the GuideME client.

The guide is copied into the output directory and loaded from there, so the shipped pages and structures stay
untouched. Beam Converter blocks are removed from the copied structures because their texture is a placeholder.
Each scene is drawn on the poster background colour and cropped to the scene panel.

Requires Xvfb, ImageMagick `import`, JDK 21 and the Gradle build. Use the tools/visual pixi environment.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time

from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
GUIDE = ROOT / 'common/src/main/resources/assets/ae2federation/ae2guide'
BACKGROUND = (10, 15, 22)
PLACEHOLDER_BLOCKS = ['ae2federation:beam_converter']
# Zoom that fits each structure into a 3200 x 2000 window at GUI scale 2.
SCENES = {
    'market_hub': 14,
    'shared_warehouse': 16,
    'order_desk': 16,
    'endpoint_furnaces': 10,
    'nether_outpost_overworld': 11,
    'nether_outpost_nether': 16,
}
PAGE = 'promo/scene.md'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def strip_blocks(path):
    lines = path.read_text(encoding='utf-8').split('\n')
    kept = [line for line in lines if not ('pos:' in line and any(block in line for block in PLACEHOLDER_BLOCKS))]
    for i in range(len(kept) - 1):
        if kept[i].rstrip().endswith(',') and kept[i + 1].strip().startswith(']'):
            kept[i] = kept[i].rstrip()[:-1]
    path.write_text('\n'.join(kept), encoding='utf-8')
    return len(lines) - len(kept)


def write_page(guide, scene, zoom):
    target = guide / PAGE
    temporary = target.with_suffix('.tmp')
    temporary.write_text(f"""---
navigation:
  parent: index.md
  title: Promo scene
---

# Promo scene

<GameScene zoom="{zoom}" background="#{BACKGROUND[0]:02x}{BACKGROUND[1]:02x}{BACKGROUND[2]:02x}">
  <ImportStructure src="../assets/examples/{scene}.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>
""", encoding='utf-8')
    # The guide reloads pages live; replacing the file in one step avoids parsing a half-written page.
    temporary.replace(target)


def crop_scene(raw, target):
    image = Image.open(raw).convert('RGB')
    difference = ImageChops.difference(image, Image.new('RGB', image.size, BACKGROUND))
    red, green, blue = difference.split()
    background = ImageChops.add(ImageChops.add(red, green), blue).point(lambda v: 255 if v <= 3 else 0)
    panel_box = background.getbbox()
    if panel_box is None:
        raise RuntimeError('Scene panel not found in ' + str(raw))
    panel = image.crop(panel_box)
    content = ImageChops.invert(background.crop(panel_box)).getbbox()
    if content is None:
        raise RuntimeError('Empty scene in ' + str(raw))
    margin = 24
    box = (max(content[0] - margin, 0), max(content[1] - margin, 0),
           min(content[2] + margin, panel.width), min(content[3] + margin, panel.height))
    panel.crop(box).save(target)
    return list(panel.size), list(panel.crop(box).size)


def wait_for(log, text, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if log.exists():
            content = log.read_text(errors='replace')
            if text in content:
                return
            if 'BUILD FAILED' in content:
                raise RuntimeError('Guide client failed, see ' + str(log))
        time.sleep(2)
    raise TimeoutError(f'{text!r} not seen in {log}')


def stop(process):
    if process and process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)


def main(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    guide = output / 'ae2guide'
    shutil.copytree(GUIDE, guide)
    (guide / 'promo').mkdir()
    removed = {scene: strip_blocks(guide / 'assets/examples' / (scene + '.snbt')) for scene in SCENES}
    client = output / 'client'
    client.mkdir()
    (client / 'options.txt').write_text('onboardAccessibility:false\nguiScale:2\nlang:en_us\n')
    init = output / 'scenes.init.gradle'
    init.write_text(f"""gradle.projectsEvaluated {{
    def p = gradle.rootProject.project(':neoforge-1.21.1')
    p.neoForge.runs.named('guideClient') {{
        gameDirectory = file('{client}')
        systemProperty('guideme.ae2.guide.sources', '{guide}')
        programArguments.empty()
        programArguments.addAll('--width', '3200', '--height', '2000')
    }}
    p.tasks.named('prepareGuideClientOptions') {{ enabled = false }}
}}
""")
    first = next(iter(SCENES))
    write_page(guide, first, SCENES[first])
    environment = dict(os.environ, DISPLAY=args.display)
    log = output / 'client.log'
    xvfb = game = None
    records = []
    try:
        xvfb = subprocess.Popen(['Xvfb', args.display, '-screen', '0', '3200x2000x24', '+extension', 'GLX'],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        time.sleep(2)
        with log.open('w') as stream:
            game = subprocess.Popen(['./gradlew', '--no-daemon', '-I', str(init), ':neoforge-1.21.1:runGuideClient',
                                     '-PguidePage=ae2federation:' + PAGE],
                                    cwd=ROOT, env=environment, stdout=stream, stderr=subprocess.STDOUT,
                                    start_new_session=True)
        wait_for(log, 'Compiling ae2federation:' + PAGE, 900)
        time.sleep(12)
        for scene, zoom in SCENES.items():
            write_page(guide, scene, zoom)
            time.sleep(8)
            raw = output / ('raw-' + scene + '.png')
            subprocess.run(['import', '-window', 'root', str(raw)], env=environment, check=True)
            target = output / (scene + '.png')
            panel, size = crop_scene(raw, target)
            records.append({'scene': scene, 'zoom': zoom, 'removed_placeholder_blocks': removed[scene],
                            'panel': panel, 'size': size, 'file': target.name, 'sha256': digest(target)})
            print('SCENE_OK', scene, f'{size[0]}x{size[1]}')
    finally:
        stop(game)
        stop(xvfb)
    if 'ParseException' in log.read_text(errors='replace'):
        raise RuntimeError('A scene page failed to parse, see ' + str(log))
    manifest = {'scenes': records, 'camera': {'yaw': 195, 'pitch': 30}, 'window': [3200, 2000], 'gui_scale': 2,
                'background': '#%02x%02x%02x' % BACKGROUND, 'removed_blocks': PLACEHOLDER_BLOCKS,
                'tool_sha256': digest(Path(__file__))}
    (output / 'scenes.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('SCENES_READY', output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='new directory for the scene renders')
    parser.add_argument('--display', default=':93', help='free X display number for Xvfb')
    main(parser.parse_args())
