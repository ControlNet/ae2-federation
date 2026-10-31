"""Draw the standalone Quantum Cube's native block texture and model.

The standalone block uses a complete opaque 16px face; formed cubes keep the Blender mesh.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from build_refined_device_textures import colors

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'common/src/main/resources/assets/ae2federation'


def face(kind, palette):
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
    if kind == 'quantum':
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
    # The Beam Converter's block model and textures are the artist's (tools/blockbench/export_beam_converter_models.py).
    folder = ASSETS / 'textures/block/quantum_cube'
    folder.mkdir(parents=True, exist_ok=True)
    face('quantum', colors()).save(folder / 'quantum.png')
    textures = {side: 'ae2federation:block/quantum_cube/quantum'
                for side in ('north', 'south', 'east', 'west', 'up', 'down', 'particle')}
    # Referencing both atlases here ensures that formed mesh sprites are stitched too.
    textures.update(surface='ae2federation:block/refined_devices/surface',
                    emission='ae2federation:block/refined_devices/emission')
    (ASSETS / 'models/block/quantum_cube.json').write_text(json.dumps(
        {'parent':'minecraft:block/cube', 'textures':textures}, indent=2)+'\n')
    print('DEVICE_BLOCK_TEXTURES_OK: 1 opaque standalone face; 1 native cube model')

if __name__ == '__main__':
    main()
