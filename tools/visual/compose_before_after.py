"""Pair same-named in-game screenshots from a before and an after folder into one labeled comparison sheet.

Only scales, labels and arranges the captures; it adds no glow, colour or sharpening.
"""
from pathlib import Path
import argparse

from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser()
parser.add_argument('before', type=Path)
parser.add_argument('after', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--names', nargs='+', help='Shot names without .png; default: every PNG present in both folders')
parser.add_argument('--width', type=int, default=960, help='Width of each scaled capture')
parser.add_argument('--title', default='')
args = parser.parse_args()

names = args.names or sorted(p.stem for p in args.before.glob('*.png') if (args.after / p.name).exists())
if not names:
    raise SystemExit('No shot is present in both folders')
font_path = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font = ImageFont.truetype(font_path, 26) if Path(font_path).exists() else ImageFont.load_default()
gap, label, header = 12, 40, 56 if args.title else 0


def load(path):
    image = Image.open(path).convert('RGB')
    return image.resize((args.width, round(image.height * args.width / image.width)), Image.LANCZOS)


rows = [(name, load(args.before / f'{name}.png'), load(args.after / f'{name}.png')) for name in names]
height = header + sum(label + max(b.height, a.height) + gap for _, b, a in rows)
sheet = Image.new('RGB', (2 * args.width + 3 * gap, height), (14, 17, 20))
draw = ImageDraw.Draw(sheet)
if args.title:
    draw.text((gap, 12), args.title, font=font, fill=(230, 240, 245))
y = header
for name, before, after in rows:
    for column, (tag, image) in enumerate((('BEFORE', before), ('AFTER', after))):
        x = gap + column * (args.width + gap)
        draw.text((x, y + 6), f'{tag} / {name}', font=font, fill=(200, 230, 240) if column else (200, 200, 200))
        sheet.paste(image, (x, y + label))
    y += label + max(before.height, after.height) + gap
args.output.parent.mkdir(parents=True, exist_ok=True)
sheet.save(args.output)
print(args.output)
