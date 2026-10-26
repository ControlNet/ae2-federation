"""Add explanatory typography to asset-backed gallery renders; export review WebPs.

Run with the approved tools/visual pixi environment. This never writes docs/gallery.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WHITE = '#f1f6fa'
MUTED = '#bacbd5'
CYAN = '#8de8f3'
PURPLE = '#d0b0ff'
LINE = '#758e9e'
FONT_DIR = Path('/usr/share/fonts/truetype/dejavu')


def font(size, bold=False):
    return ImageFont.truetype(str(FONT_DIR / ('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf')), size)


def compose(source, output):
    output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((source / 'renders.json').read_text())
    headers = {
        'network-overview': ('NETWORK SHARING', 'Independent networks. Shared resources.',
                             'Use Switches to connect ME networks to a Federation domain.'),
        'processing-installation': ('REMOTE PROCESSING', 'Send the job. Bring back the result.',
                                   'Map a Pattern Provider to an Endpoint on a separate machine subnet.'),
        'bundled-cable': ('BUNDLED CABLE', 'More room for a growing network.',
                          'A 2 × 2 bundle joins formed Routers through their full end faces.'),
        'beam-sizes': ('BEAM FORMER', 'Link across open space.',
                       'Face two matching Beam Formers toward each other to connect their cables.'),
    }
    entries = []
    for key, (section, title, subtitle) in headers.items():
        target = output / (key + '.webp')
        if target.exists():
            raise FileExistsError(target)
        image = Image.open(source / (key + '.png')).convert('RGB')
        draw = ImageDraw.Draw(image)

        def text(x, y, value, size=26, color=WHITE, bold=False, anchor=None):
            draw.text((x, y), value, font=font(size, bold), fill=color, anchor=anchor)

        def leader(point, x, y, label, color=CYAN, size=26):
            px, py = point
            end_y = y+43 if py > y else y-12
            draw.line([(px, py), (px, end_y), (x, end_y)], fill=LINE, width=2)
            draw.ellipse((px-4, py-4, px+4, py+4), fill=color)
            text(x, y, label, size, color, True, 'mt')

        def arrow(x1, x2, y, label, color):
            draw.line((x1, y, x2, y), fill=color, width=3)
            sign = 1 if x2 > x1 else -1
            draw.polygon([(x2, y), (x2-sign*12, y-6), (x2-sign*12, y+6)], fill=color)
            text((x1+x2)/2, y-37, label, 23, color, anchor='mt')

        text(64, 40, 'AE2 FEDERATION  /  ' + section, 21, CYAN, True)
        text(64, 88, title, 51, WHITE, True)
        text(64, 163, subtitle, 27, MUTED)
        a = manifest['images'][key]['anchors']
        if key in ('network-overview', 'processing-installation'):
            leader(a['left'], 285, 267, 'ME network A' if key == 'network-overview' else 'Your ME network', PURPLE)
            leader(a['right'], 1295, 320, 'ME network B' if key == 'network-overview' else 'Machine subnet', CYAN)
            if key == 'network-overview':
                leader(a['switch_left'], 425, 706, 'Switch', PURPLE)
                leader(a['switch_right'], 1135, 756, 'Switch', PURPLE)
                text(800, 655, 'Federation Cable', 25, CYAN, anchor='mt')
                footer = [('Storage', 'Use another network’s inventory.'),
                          ('Crafting', 'Request shared crafting services.'),
                          ('ME power', 'Choose which networks share power.')]
            else:
                arrow(610, 1005, 330, 'Job inputs', CYAN)
                arrow(1005, 610, 715, 'Results return', PURPLE)
                leader(a['provider'], 350, 755, 'Pattern Provider', PURPLE)
                leader(a['endpoint'], 1190, 755, 'Processing Endpoint', CYAN)
                footer = [('1  Map the pattern', 'Choose the Endpoint in the editor.'),
                          ('2  Process on its subnet', 'Only the job’s inputs are supplied.'),
                          ('3  Push results back', 'Machines return outputs to the Endpoint.')]
            footnote = 'Connection diagram · AE2 Interfaces represent the two networks · Power supplies and machine internals omitted'
        elif key == 'bundled-cable':
            leader(a['router'], 285, 744, 'Formed Router', CYAN)
            leader(a['bundle'], 840, 790, '2 × 2 bundle', CYAN)
            footer = [('8', 'Ordinary cable · default capacity'),
                      ('48', '2 × 2 bundle · default capacity'),
                      ('Unlimited', '3 × 3 bundle · no capacity limit')]
            footnote = 'Capacity counts networks and devices, not items per second · Model-based studio render · Power supply omitted'
        else:
            for size, label in [('1', '1 × 1'), ('2', '2 × 2'), ('3', '3 × 3')]:
                px, py = a[size]
                draw.line((px, py+10, px, 765), fill=LINE, width=2)
                text(px, 776, label, 31, CYAN, True, 'mt')
            footer = [('Matching sizes', 'Both ends use the same square.'),
                      ('Clear path', 'Keep solid blocks out of the beam.'),
                      ('256 blocks', 'Default maximum between fronts.')]
            footnote = 'Device artwork at a common scale · Studio render, shown without the active beam · Limits are server-configurable'
        draw.line((64, 850, 1536, 850), fill=LINE, width=1)
        for x, (heading, body) in zip((64, 565, 1066), footer):
            text(x, 873, heading, 28, WHITE, True)
            text(x, 918, body, 21, MUTED)
        text(64, 968, footnote, 17, MUTED)
        image.save(target, 'WEBP', quality=94, method=6)
        entries.append({'file': target.name, 'size': list(image.size), 'bytes': target.stat().st_size,
                        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                        'source': str(source / (key + '.png')),
                        'source_sha256': hashlib.sha256((source / (key + '.png')).read_bytes()).hexdigest(),
                        'kind': 'annotated Blender size comparison' if key == 'beam-sizes' else 'annotated Blender connection diagram',
                        'composition': {'script': 'tools/visual/compose_gallery.py',
                                        'script_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                                        'font': 'DejaVu Sans / DejaVu Sans Bold'},
                        'encoding': {'format': 'WebP', 'lossless': False, 'quality': 94, 'method': 6}})
    (output / 'composed.json').write_text(json.dumps(entries, indent=2) + '\n')
    contact = Image.new('RGB', (1600, 1000))
    for index, entry in enumerate(entries):
        thumb = Image.open(output / entry['file']).resize((800, 500), Image.Resampling.LANCZOS)
        contact.paste(thumb, ((index % 2)*800, (index//2)*500))
    contact.save(output / 'review.jpg', quality=95)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    compose(args.source, args.output)
