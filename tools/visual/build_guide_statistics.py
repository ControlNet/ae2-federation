"""Turn the ui.mapping guide-statistics shots into the Statistics guide page's images.

The ui.mapping scenario's last steps take `guide-statistics-en_us` and `guide-statistics-zh_cn` in a 3196x1916 window
(run it under an Xvfb screen of at least that size). Write the English one to
`ae2guide/assets/statistics.png` and the Chinese one to `ae2guide/_zh_cn/assets/statistics.png`:

    pixi run --manifest-path tools/visual/pixi.toml python tools/visual/build_guide_statistics.py <shot.png> <out.png>
"""
import math
import sys
from PIL import Image

src, out = sys.argv[1:3]
# The shot has four pixels per GUI pixel. The domain screen's panel spans x 120-3163, y 32-1883; its tab rail spans
# x 32-123, y 184-487. Crop to both and make the world beside the rail transparent.
img = Image.open(src).convert("RGB").crop((32, 32, 3164, 1884))
panel_left, rail_top, rail_bottom = 120 - 32, 184 - 32, 488 - 32
# GuideME draws an image at width/4 GUI pixels, capped by the page, with nearest sampling. Smoothing it to 2520 wide
# (630 GUI pixels, the page width at GUI scale 2 in a 1600x960 window) keeps small text readable after that.
width = 2520
f = width / img.width
img = img.resize((width, round(img.height * f)), Image.LANCZOS)
# 255 colours keep it near 150 KB; k-means refining keeps the mean error under 1 per channel. Index 255 is transparent.
q = img.quantize(colors=255, method=Image.Quantize.MAXCOVERAGE, kmeans=4, dither=Image.Dither.NONE)
palette = q.getpalette()[:255 * 3] + [0, 0, 0]
q.putpalette(palette)
left = math.floor(panel_left * f)
q.paste(255, (0, 0, left, math.floor(rail_top * f)))
q.paste(255, (0, math.ceil(rail_bottom * f), left, q.height))
q.save(out, optimize=True, transparency=255)
