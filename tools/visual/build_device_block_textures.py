"""Draw native single-block device textures and the creative converter's violet atlas.

Standalone construction blocks use complete opaque 16px faces. Formed devices keep
Blender meshes; their creative variant uses the same UV layout with violet accents.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from build_refined_device_textures import colors

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'common/src/main/resources/assets/ae2federation'


def creative_palette(source):
    result = dict(source)
    result.update(ice=(222, 192, 255), teal=(165, 113, 211), teal_dark=(101, 65, 141))
    result['deep_teal'] = tuple(round(c*.55) for c in result['teal_dark'])
    result['abyss'] = tuple(round(c*.32) for c in result['teal_dark'])
    return result


def face(kind, palette, creative=False):
    im = Image.new('RGBA', (16, 16), (*palette['silver'], 255))
    d = ImageDraw.Draw(im)
    def rect(box, color):
        d.rectangle(box, fill=(*palette[color], 255))
    def line(points, color):
        d.line(points, fill=(*palette[color], 255))
    # Layered metal rim, recessed gasket and teal corner fasteners.
    rect((1, 1, 14, 14), 'steel')
    line([(0, 15), (0, 0), (15, 0)], 'white')
    line([(15, 1), (15, 15), (1, 15)], 'dark')
    rect((3, 3, 12, 12), 'deep')
    line([(3, 12), (3, 3), (12, 3)], 'dark')
    line([(12, 4), (12, 12), (4, 12)], 'pale')
    for x in (1, 12):
        for y in (1, 12):
            rect((x, y, x+2, y+2), 'teal_dark')
            line([(x, y+2), (x, y), (x+2, y)], 'ice')
            rect((x+1, y+1, x+1, y+1), 'teal')
    if kind == 'front':
        ring = [(6, 4), (9, 4), (11, 6), (11, 9), (9, 11), (6, 11), (4, 9), (4, 6), (6, 4)]
        line(ring, 'teal')
        line([(6, 5), (9, 5), (10, 6)], 'ice')
        rect((6, 6, 9, 9), 'teal_dark')
        rect((7, 7, 8, 8), 'ice')
        if creative:
            rect((5, 6, 10, 9), 'deep_teal')
            line([(5, 7), (6, 6), (9, 9), (10, 8), (10, 7), (9, 6), (6, 9), (5, 8), (5, 7)], 'ice')
    elif kind == 'back':
        rect((5, 5, 10, 10), 'steel')
        rect((6, 6, 9, 9), 'abyss')
        for x in (6, 9):
            line([(x, 6), (x, 9)], 'teal')
        line([(7, 5), (8, 5)], 'ice')
        for y in (6, 9):
            rect((4, y, 4, y), 'ice')
            rect((11, y, 11, y), 'teal')
    elif kind == 'side':
        for y in (5, 7, 9):
            line([(5, y), (10, y)], 'steel')
            line([(6, y+1), (10, y+1)], 'dark')
        line([(4, 5), (4, 10)], 'teal')
        rect((11, 5, 11, 7), 'ice')
        rect((11, 9, 11, 10), 'teal_dark')
    elif kind == 'top':
        rect((5, 5, 10, 10), 'dark')
        line([(5, 10), (5, 5), (10, 5)], 'silver')
        line([(6, 6), (9, 6), (9, 9)], 'teal')
        rect((7, 7, 8, 8), 'deep_teal')
        rect((6, 10, 8, 10), 'ice')
    elif kind == 'quantum':
        # Four separated curved plates around a dark central convergence symbol.
        for points in (
            [(4, 7), (4, 6), (6, 4), (7, 4)],
            [(9, 4), (10, 4), (11, 6), (11, 7)],
            [(11, 9), (11, 10), (9, 11), (8, 11)],
            [(6, 11), (5, 11), (4, 9)]):
            line(points, 'teal')
        rect((6, 6, 9, 9), 'deep_teal')
        line([(7, 6), (6, 7), (8, 9), (9, 8)], 'ice')
        rect((7, 7, 8, 8), 'abyss')
        for x, y in ((4, 6), (9, 4), (11, 9), (6, 11)):
            rect((x, y, x, y), 'ice')
    else:
        raise ValueError(kind)
    return im


def main():
    ordinary = colors()
    purple = creative_palette(ordinary)
    # The ordinary converter's block model and textures are the artist's (tools/blockbench/export_beam_converter_models.py).
    for name in ('creative_beam_converter', 'quantum_cube'):
        creative = name.startswith('creative_')
        palette = purple if creative else ordinary
        folder = ASSETS / 'textures/block' / name
        folder.mkdir(parents=True, exist_ok=True)
        kinds = ('quantum',) if name == 'quantum_cube' else ('front', 'back', 'side', 'top')
        for kind in kinds:
            face(kind, palette, creative).save(folder / (kind+'.png'))
        base = 'ae2federation:block/'+name+'/'
        textures = {side: base+'quantum' for side in ('north', 'south', 'east', 'west', 'up', 'down', 'particle')}
        if name != 'quantum_cube':
            textures.update(north=base+'back', south=base+'front', east=base+'side', west=base+'side',
                            up=base+'top', down=base+'top', particle=base+'side')
        atlas = 'creative_' if creative else ''
        # Referencing both atlases here ensures that formed mesh sprites are stitched too.
        textures.update(surface='ae2federation:block/refined_devices/'+atlas+'surface',
                        emission='ae2federation:block/refined_devices/'+atlas+'emission')
        (ASSETS / 'models/block' / (name+'.json')).write_text(json.dumps(
            {'parent':'minecraft:block/cube', 'textures':textures}, indent=2)+'\n')
    replacements = {ordinary[key]: purple[key] for key in ('ice', 'teal', 'teal_dark', 'abyss', 'deep_teal')}
    folder = ASSETS / 'textures/block/refined_devices'
    for name in ('surface', 'emission'):
        with Image.open(folder / (name+'.png')) as original:
            im = original.convert('RGBA')
        pixels = [(*replacements.get((r, g, b), (r, g, b)), a) for r, g, b, a in im.getdata()]
        im.putdata(pixels)
        im.save(folder / ('creative_'+name+'.png'))
    print('DEVICE_BLOCK_TEXTURES_OK: 5 opaque standalone faces; 2 creative atlases; 2 native cube models')


if __name__ == '__main__':
    main()
