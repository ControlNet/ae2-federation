"""Prepare bilingual poster pages from guide scene renders and gallery screenshot crops.

Use the tools/visual pixi environment. Generated pages, PNGs and fonts stay in build/.
"""
import argparse
import hashlib
import html
import json
from pathlib import Path
import re
import shutil

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
# Whole game windows, without the gallery frame around them.
UI_CROPS = {
    'sharing_rules': ('sharing-rules.webp', [60, 16, 1586, 946]),
    'mapping': ('processing-mappings.webp', [78, 96, 1045, 729]),
    'topology': ('topology.webp', [60, 16, 1906, 1064]),
    'statistics': ('statistics-flow.webp', [60, 16, 1586, 946]),
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if (output / 'sources.json').exists():
        raise FileExistsError('Choose a new composition directory')
    (output / 'assets').mkdir(exist_ok=True)
    (output / 'fonts').mkdir(exist_ok=True)
    records = []

    def asset(name, source, crop=None):
        image = Image.open(source).convert('RGB')
        if crop:
            image = image.crop(crop)
        target = output / 'assets' / (name + '.png')
        image.save(target)
        records.append({'asset': str(target.relative_to(output)), 'source': str(source.resolve().relative_to(ROOT)),
                        'source_sha256': digest(source), 'crop': crop,
                        'size': list(image.size), 'sha256': digest(target)})

    scenes = json.loads((args.scenes / 'scenes.json').read_text())
    for scene in scenes['scenes']:
        asset(scene['scene'], args.scenes / scene['file'])
    gallery = ROOT / 'docs/gallery'
    for name, (source, crop) in UI_CROPS.items():
        asset(name, gallery / source, crop)
    font = args.font.resolve()
    shutil.copy2(font, output / 'fonts' / 'NotoSansSC.ttf')
    licence = font.with_name('OFL.txt')
    shutil.copy2(licence, output / 'fonts' / 'OFL.txt')
    copy = json.loads((HERE / 'copy.json').read_text())
    template = (HERE / 'poster.html').read_text()
    for language, strings in copy.items():
        page = re.sub(r'\{\{(\w+)\}\}', lambda m: html.escape(strings[m[1]]).replace('\n', '<br>'), template)
        (output / (language + '.html')).write_text(page)
    manifest = {'assets': records,
                'fonts': {'NotoSansSC.ttf': {'source': font.name, 'sha256': digest(font), 'licence': 'SIL OFL 1.1'}},
                'tools': {str(p.relative_to(ROOT)): digest(p) for p in [HERE/'prepare.py', HERE/'copy.json', HERE/'poster.html']},
                'scene_manifest': scenes}
    (output / 'sources.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('POSTER_INPUTS_READY', output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scenes', type=Path, required=True, help='output directory of scenes.py')
    parser.add_argument('--font', type=Path, required=True, help='Noto Sans SC variable TTF, with OFL.txt beside it')
    parser.add_argument('--output', type=Path, required=True)
    main(parser.parse_args())
