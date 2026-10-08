"""Draw the Federation P2P tunnel's type panel.

AE2 tells its P2P tunnel modes apart by the small square panel on the tunnel's front; its base model takes the panel
as the ``type`` texture and shows the centre 8x8 of a 16x16 image. This panel is drawn here from scratch: concentric
one-pixel square rings around a 2x2 centre, in the Federation cable's teal, which no native mode uses.

    pixi run --manifest-path tools/visual/pixi.toml python tools/visual/p2p_panel_texture.py
"""
from pathlib import Path
from PIL import Image

# The Federation cable's teal, darkest to lightest.
TEAL = ['#416e77', '#57969c', '#68b7b8', '#94d3cd', '#c2e5dc']
# One colour per ring from the outside in; the last is the 2x2 centre.
RINGS = [TEAL[0], TEAL[3], TEAL[2], TEAL[1], TEAL[4]]
TARGET = Path(__file__).resolve().parents[2] / (
    'common/src/main/resources/assets/ae2federation/textures/part/p2p_tunnel_federation.png')


def rgba(hex_colour: str):
    return tuple(int(hex_colour[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def write_texture(target: Path):
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for ring, colour in enumerate(RINGS):
        low, high = 3 + ring, 12 - ring
        for y in range(low, high + 1):
            for x in range(low, high + 1):
                image.putpixel((x, y), rgba(colour))
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


if __name__ == '__main__':
    write_texture(TARGET)
    print(TARGET)
