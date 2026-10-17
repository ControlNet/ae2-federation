"""Write the temporary textures of the ME Federation Beam Converter and Beam Former: the Federation Pattern Provider's,
recoloured, until the artist draws them. Amber for the converter, the creative purple for the creative one, cyan for
the former; greys stay as they are.
"""
from pathlib import Path
import colorsys
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
TEXTURES = REPO / 'common/src/main/resources/assets/ae2federation/textures/block'
SOURCES = {'front': 'pattern_provider', 'side': 'pattern_provider_side', 'back': 'pattern_provider_back'}
HUES = {'beam_converter': 36 / 360, 'creative_beam_converter': 290 / 360, 'beam_former': 190 / 360}


def recolour(image, hue):
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if a == 0 or s < 0.15:
                continue
            r2, g2, b2 = colorsys.hls_to_rgb(hue, l, s)
            pixels[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


for device, hue in HUES.items():
    folder = TEXTURES / device
    folder.mkdir(exist_ok=True)
    for name, source in SOURCES.items():
        image = Image.open(TEXTURES / f'{source}.png').convert('RGBA')
        recolour(image, hue).save(folder / f'{device}_{name}.png')
print('Wrote temporary Beam Converter and Beam Former textures from the Pattern Provider\'s.')
