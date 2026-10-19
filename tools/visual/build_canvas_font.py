"""Builds the topology canvas's Chinese font: a subset of Droid Sans Fallback (Apache-2.0) holding the GB2312 hanzi,
every CJK character in the mod's Chinese lang file, and the CJK and full-width punctuation.

The subset carries no Latin glyphs on purpose. font/canvas.json puts it before minecraft:default, so Chinese is drawn
from these outlines while Latin text falls through to the player's own default font, pixel font and resource packs
included. Characters outside the subset fall through to Unifont, as everywhere else in the game.

Usage: python build_canvas_font.py [--level 1|2] [source TTF]
"""
from pathlib import Path
import argparse
import json

from fontTools import subset
from fontTools.ttLib import TTFont

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / 'common/src/main/resources/assets/ae2federation'
OUTPUT = ASSETS / 'font/canvas_cjk.ttf'
DEFAULT_SOURCE = Path('/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf')
# Below this sit Latin, general punctuation (· — …) and symbols the default font already draws.
CJK_START = 0x2E80


def gb2312_hanzi(level):
    """The GB2312 hanzi: level 1 is rows 16-55 (3755 common characters), level 2 adds rows 56-87 (6763 in all)."""
    last_row = 55 if level == 1 else 87
    chars = set()
    for row in range(16, last_row + 1):
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
    parser.add_argument('--level', type=int, choices=(1, 2), default=2)
    parser.add_argument('source', nargs='?', type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()

    punctuation = {chr(code) for code in [*range(0x3000, 0x3040), *range(0xFF00, 0xFFF0)]}
    wanted = {ord(char) for char in gb2312_hanzi(args.level) | lang_chars() | punctuation}
    # Keep the source's timestamp, so rebuilding from the same inputs gives the same bytes.
    font = TTFont(args.source, recalcTimestamp=False)
    cmap = font.getBestCmap()
    unicodes = sorted(code for code in wanted if code in cmap)
    assert all(code >= CJK_START for code in unicodes)
    missing = sorted(char for char in lang_chars() if ord(char) not in cmap)
    assert not missing, f'the source font lacks lang characters: {"".join(missing)}'

    options = subset.Options()
    # Minecraft draws glyph by glyph: no shaping, no vertical metrics.
    options.layout_features = []
    options.layout_closure = False
    options.drop_tables += ['GSUB', 'GPOS', 'GDEF', 'vhea', 'vmtx']
    options.name_IDs = ['*']
    options.name_languages = ['*']
    options.notdef_outline = False
    options.hinting = False
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=unicodes)
    subsetter.subset(font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUTPUT)
    print(f'{OUTPUT.relative_to(REPO)}: {len(unicodes)} characters, {OUTPUT.stat().st_size} bytes')


if __name__ == '__main__':
    main()
