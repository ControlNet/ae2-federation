"""Arrange same-named in-game screenshots from several capture folders into one labeled comparison sheet.

One column per folder (e.g. BEFORE=dir AFTER=dir, or every iteration), one row per shot. Only scales, labels and
arranges the captures; it adds no glow, colour or sharpening.
"""
from pathlib import Path
import argparse

from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser()
parser.add_argument('columns', nargs='+', metavar='LABEL=DIR', help='Column label and capture folder, left to right')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--names', nargs='+', help='Shot names without .png; default: every PNG present in all folders')
parser.add_argument('--width', type=int, default=960, help='Width of each scaled capture')
parser.add_argument('--title', default='')
args = parser.parse_args()

columns = []
for column in args.columns:
    label, separator, folder = column.partition('=')
    if not separator:
        raise SystemExit(f'Expected LABEL=DIR, got {column}')
    columns.append((label, Path(folder)))
names = args.names or sorted(set.intersection(*({p.stem for p in folder.glob('*.png')} for _, folder in columns)))
if not names:
    raise SystemExit('No shot is present in every folder')
font_path = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font = ImageFont.truetype(font_path, 26) if Path(font_path).exists() else ImageFont.load_default()
gap, label_height, header = 12, 40, 56 if args.title else 0


def load(path):
    image = Image.open(path).convert('RGB')
    return image.resize((args.width, round(image.height * args.width / image.width)), Image.LANCZOS)


rows = [(name, [load(folder / f'{name}.png') for _, folder in columns]) for name in names]
height = header + sum(label_height + max(i.height for i in images) + gap for _, images in rows)
sheet = Image.new('RGB', (len(columns) * (args.width + gap) + gap, height), (14, 17, 20))
draw = ImageDraw.Draw(sheet)
if args.title:
    draw.text((gap, 12), args.title, font=font, fill=(230, 240, 245))
y = header
for name, images in rows:
    for index, ((label, _), image) in enumerate(zip(columns, images)):
        x = gap + index * (args.width + gap)
        draw.text((x, y + 6), f'{label} / {name}', font=font, fill=(200, 230, 240) if index else (200, 200, 200))
        sheet.paste(image, (x, y + label_height))
    y += label_height + max(i.height for i in images) + gap
args.output.parent.mkdir(parents=True, exist_ok=True)
sheet.save(args.output)
print(args.output)
