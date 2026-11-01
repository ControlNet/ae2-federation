"""Write the temporary textures of the automation blocks until the artist draws them. The ME Federation Automation
Interface takes the Processing Endpoint's, recoloured green. The Creative Computing Device, itself a placeholder for
testing, takes the Router's standalone texture, recoloured magenta. Greys stay as they are.
"""
from pathlib import Path
import colorsys
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
TEXTURES = REPO / 'common/src/main/resources/assets/ae2federation/textures/block'


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


AUTOMATION_HUE = 130 / 360
folder = TEXTURES / 'automation_interface'
folder.mkdir(exist_ok=True)
for name in ['', '_side', '_back']:
    image = Image.open(TEXTURES / f'processing_endpoint{name}.png').convert('RGBA')
    recolour(image, AUTOMATION_HUE).save(folder / f'automation_interface{name}.png')
DEVICE_HUE = 320 / 360
folder = TEXTURES / 'creative_computing_device'
folder.mkdir(exist_ok=True)
image = Image.open(TEXTURES / 'router' / 'router.png').convert('RGBA')
recolour(image, DEVICE_HUE).save(folder / 'creative_computing_device.png')
print('Wrote temporary Automation Interface and Computing Device textures.')
