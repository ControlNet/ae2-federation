"""Pack the approved device pixel artwork into the runtime diffuse/emissive atlases."""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'common/src/main/resources/assets/ae2federation/textures/block/refined_devices'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('textures', type=Path)
    args = parser.parse_args()
    manifest = json.loads((args.textures / 'textures.json').read_text())
    size = 512
    atlas = Image.new('RGBA', (size, size))
    glow = Image.new('RGBA', (size, size))
    tiles = {}
    x = y = 2
    row = 0
    for name, spec in sorted(manifest['textures'].items()):
        w, h = spec['width'], spec['height']
        if x + w + 2 > size:
            x = 2
            y += row + 4
            row = 0
        assert y + h + 2 <= size
        with Image.open(args.textures / (name + '.png')) as im:
            atlas.paste(im, (x, y))
            emission = im.convert('RGBA')
        with Image.open(args.textures / (name + '-emission.png')) as mask:
            emission.putalpha(mask.point(lambda p: 255 if p >= 90 else 0))
        glow.paste(emission, (x, y))
        # Duplicate borders so mipmaps do not pull transparent black into painted edges.
        for dest, source in ((atlas, atlas.crop((x, y, x+w, y+h))), (glow, emission)):
            dest.paste(source.crop((0, 0, w, 1)).resize((w, 2)), (x, y-2))
            dest.paste(source.crop((0, h-1, w, h)).resize((w, 2)), (x, y+h))
            dest.paste(source.crop((0, 0, 1, h)).resize((2, h)), (x-2, y))
            dest.paste(source.crop((w-1, 0, w, h)).resize((2, h)), (x+w, y))
        tiles[name] = [x, y, w, h]
        x += w + 4
        row = max(row, h)
    tiles['teal-light'] = [size-4, size-4, 2, 2]
    for dest in (atlas, glow):
        dest.paste((108, 174, 187, 255), (size-6, size-6, size, size))
    OUT.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT / 'surface.png')
    glow.save(OUT / 'emission.png')
    (args.textures.parent / 'runtime-atlas.json').write_text(json.dumps({'size':size, 'tiles':tiles}, indent=2)+'\n')
    print('DEVICE_ATLAS_OK', len(tiles), size)

if __name__ == '__main__':
    main()
