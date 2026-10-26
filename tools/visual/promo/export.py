"""Encode the two reviewed poster captures as tracked-ready WebP files.

Use the tools/visual pixi environment. The output must be a new directory.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(args):
    args.output.mkdir(parents=True, exist_ok=False)
    layout = json.loads((args.source / 'layout-check.json').read_text())
    assert {r['language'] for r in layout} == {'zh', 'en'}
    sources = json.loads((args.source / 'sources.json').read_text())
    outputs = []
    previews = []
    for report in layout:
        language = report['language']
        assert report['width'] == 1200 and report['scale'] == 2
        assert not report['problems'] and all(i['loaded'] for i in report['images'])
        source = args.source / (language + '.png')
        image = Image.open(source).convert('RGB')
        assert list(image.size) == [report['width'] * 2, report['height'] * 2]
        target = args.output / ('overview-' + language + '.webp')
        image.save(target, 'WEBP', quality=94, method=6)
        with Image.open(target) as encoded:
            encoded.load()
            assert encoded.size == image.size and encoded.n_frames == 1
            preview = encoded.resize((900, round(encoded.height * 900 / encoded.width)), Image.Resampling.LANCZOS)
            previews.append(preview)
            preview.save(args.output / ('overview-' + language + '-preview.jpg'), quality=94)
        outputs.append({'file': target.name, 'language': language,
                        'kind': 'promotional overview / GuideME scene renders and actual UI crops',
                        'source': str(source), 'source_sha256': digest(source),
                        'size': list(image.size), 'bytes': target.stat().st_size, 'sha256': digest(target),
                        'encoding': {'format': 'WebP', 'quality': 94, 'method': 6, 'lossless': False}})
    contact = Image.new('RGB', (1800, max(p.height for p in previews)), (10, 15, 22))
    for i, preview in enumerate(previews):
        contact.paste(preview, (i * 900, 0))
    contact.save(args.output / 'overview-bilingual-review.jpg', quality=94)
    sources['outputs'] = outputs
    sources['layout_check'] = layout
    sources['tools'].update({str(Path(__file__).relative_to(Path(__file__).resolve().parents[3])): digest(Path(__file__))})
    renderer = Path(__file__).with_name('render.mjs')
    sources['tools']['tools/visual/promo/render.mjs'] = digest(renderer)
    (args.output / 'sources.json').write_text(json.dumps(sources, indent=2) + '\n')
    print('POSTERS_EXPORTED', json.dumps(outputs))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    main(parser.parse_args())
