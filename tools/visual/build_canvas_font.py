"""Builds the topology canvas's Chinese font: a bitmap sheet drawn from Fusion Pixel Font 10px (OFL-1.1), holding
the GB2312 hanzi the font has, every CJK character in the mod's Chinese lang file, and the CJK and full-width
punctuation, plus font/canvas.json that puts the sheet in front of minecraft:default.

The glyphs are 10-pixel designs at one texel per canvas unit, the ratio the default Latin pixel font uses, so Chinese
stays legible as far out as Latin text does; Unifont's 16-pixel glyphs drawn 8 units tall blur first. The sheet has
no Latin glyphs on purpose: Latin falls through to the player's own default font, resource packs included, and
characters outside the sheet fall through to Unifont, as everywhere else in the game.

Source: fusion-pixel-10px-proportional-zh_hans.otf from the fusion-pixel-font-10px-proportional-otf release
archive, version 2026.09.25, https://github.com/TakWolf/fusion-pixel-font/releases

Usage: python build_canvas_font.py <fusion-pixel-10px-proportional-zh_hans.otf>
"""
from pathlib import Path
import argparse
import json

from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / 'common/src/main/resources/assets/ae2federation'
SHEET = ASSETS / 'textures/font/canvas_cjk.png'
FONT_JSON = ASSETS / 'font/canvas.json'
# Below this sit Latin, general punctuation (· — …) and symbols the default font already draws.
CJK_START = 0x2E80
CELL = 10
COLUMNS = 64
# Fusion Pixel draws a hanzi in rows 3-11 of its 10px em; lifting it 2 rows puts its ink in rows 1-9 of the cell,
# with the Latin baseline under row 8, so a hanzi stands one unit above and below Latin capitals.
LIFT = 2
ASCENT = 9


def gb2312_hanzi():
    """The GB2312 hanzi, rows 16-87: level 1 (3755 common characters) and level 2."""
    chars = set()
    for row in range(16, 88):
        for cell in range(1, 95):
            try:
                chars.add(bytes([0xA0 + row, 0xA0 + cell]).decode('gb2312'))
            except UnicodeDecodeError:
                pass
    return chars


def lang_chars():
    """Every CJK character the mod's Chinese strings use, so the mod's own text never falls back to Unifont."""
    text = ''.join(json.loads((ASSETS / 'lang/zh_cn.json').read_text(encoding='utf-8')).values())
    return {char for char in text if ord(char) >= CJK_START}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    args = parser.parse_args()

    cmap = TTFont(args.source).getBestCmap()
    punctuation = {chr(code) for code in [*range(0x3000, 0x3040), *range(0xFF00, 0xFFF0)]}
    missing = sorted(char for char in lang_chars() if ord(char) not in cmap)
    assert not missing, f'the source font lacks lang characters: {"".join(missing)}'
    chars = sorted(char for char in gb2312_hanzi() | lang_chars() | punctuation if ord(char) in cmap)
    assert all(ord(char) >= CJK_START for char in chars)

    rows = [chars[i:i + COLUMNS] for i in range(0, len(chars), COLUMNS)]
    rows[-1] += ['\u0000'] * (COLUMNS - len(rows[-1]))  # Minecraft skips NUL cells
    font = ImageFont.truetype(str(args.source), CELL)
    ink = Image.new('1', (COLUMNS * CELL, len(rows) * CELL), 0)
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char == '\u0000':
                continue
            cell = Image.new('1', (CELL, CELL), 0)
            draw = ImageDraw.Draw(cell)
            draw.fontmode = '1'
            draw.text((0, -LIFT), char, font=font, fill=1)
            ink.paste(cell, (x * CELL, y * CELL))
    sheet = ink.convert('P')
    sheet.putpalette([0, 0, 0, 255, 255, 255])
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(SHEET, optimize=True, transparency=0)

    provider = {'type': 'bitmap', 'file': 'ae2federation:font/canvas_cjk.png', 'height': CELL, 'ascent': ASCENT,
                'chars': [''.join(row) for row in rows], 'filter': {'uniform': False}}
    canvas = {'providers': [provider, {'type': 'reference', 'id': 'minecraft:default'}]}
    FONT_JSON.write_text(json.dumps(canvas, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{SHEET.relative_to(REPO)}: {len(chars)} characters, {SHEET.stat().st_size} bytes; '
          f'{FONT_JSON.relative_to(REPO)}: {FONT_JSON.stat().st_size} bytes')


if __name__ == '__main__':
    main()
