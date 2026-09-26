"""Author the experimental standard-shader flow texture; V07 art stays frozen."""
from pathlib import Path
from PIL import Image


def write_texture(target: Path):
    image = Image.new('RGBA', (16, 32))
    # The horizontal repeat is four world blocks. Transparent edges soften the
    # two intersecting ribbons without changing the square glass enclosure.
    profile = [0, 40, 85, 125, 160, 190, 210, 220, 220, 210, 190, 160, 125, 85, 40, 0]
    pulse = [0, 0, 0, 0.12, 0.3, 0.55, 1, 0.75, 0.15, 0, 0, 0, 0, 0, 0, 0]
    for y in range(16):
        for x in range(16):
            activity = pulse[x]
            if y in (5, 6, 9, 10) and x in (11, 12):
                activity = max(activity, 0.35)
            alpha = round(profile[y] * (0.65 + 0.35 * activity))
            image.putpixel((x, y), (
                round(12 + 150 * activity),
                round(158 + 84 * activity),
                round(183 + 66 * activity),
                alpha,
            ))
            # A shared dim center meets each arm at the same edge alpha. It has
            # no directional pulse, so branches do not stack unrelated highlights.
            center_alpha = round(max(profile[x], profile[y]) * 0.65)
            image.putpixel((x, y + 16), (12, 158, 183, center_alpha))
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
