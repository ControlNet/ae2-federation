"""Temporary Switch texture until the artist draws one: the Router's texture with its cyan cores turned fluix purple.

Run from the repository root:
    pixi run --manifest-path tools/visual/pixi.toml python tools/visual/switch_placeholder.py
"""
import colorsys
from pathlib import Path

from PIL import Image

TEXTURES = Path('common/src/main/resources/assets/ae2federation/textures/block')
SOURCE = TEXTURES / 'router' / 'router.png'
TARGET = TEXTURES / 'switch' / 'switch.png'
# Fluix purple, a little more saturated than the Router's cyan so it reads at a glance.
HUE = 275 / 360
SATURATION = 1.25


def recolor(image):
    out = image.convert('RGBA')
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            # The cores are the cyan family; the frame is a low-saturation blue grey and stays.
            if 170 / 360 <= h <= 205 / 360 and s >= 0.25:
                nr, ng, nb = colorsys.hsv_to_rgb(HUE, min(1.0, s * SATURATION), v)
                pixels[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


if __name__ == '__main__':
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    recolor(Image.open(SOURCE)).save(TARGET)
    print(f'wrote {TARGET}')
