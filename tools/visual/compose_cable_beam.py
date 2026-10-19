"""Assemble native beam evidence without changing render colors or adding glow.

Run with the approved tools/visual pixi environment. Outputs remain in the ignored build directory.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=Path("neoforge-1.21.1/build/beam-preview"))
    parser.add_argument("--reference-medium", type=Path)
    args = parser.parse_args()
    directory = args.directory
    font = ImageFont.truetype("DejaVuSans.ttf", 22)
    background = (14, 17, 20)

    def sheet(rows, output):
        canvas = Image.new("RGB", (1200, sum(image.height + 42 for _, image in rows)), background)
        draw = ImageDraw.Draw(canvas)
        y = 0
        for label, image in rows:
            draw.text((22, y + 10), label, font=font, fill=(192, 232, 242))
            canvas.paste(image, (0, y + 42))
            y += image.height + 42
        canvas.save(directory / output)

    def native(name):
        # Remove empty margins only; retain the native pixel size and colors.
        return Image.open(directory / name).convert("RGB").crop((0, 75, 1200, 345))

    sheet([(f"M / {load}% visual input" + (" / runtime default" if load == 60 else ""),
            native(f"load-{load}-tick-0.png")) for load in (0, 25, 60, 90, 100, 125)], "m-native-loads-v2.png")
    (directory / "m-native-loads.png").write_bytes((directory / "m-native-loads-v2.png").read_bytes())
    before = directory / "rejected-v1/medium.png"
    if before.exists():
        sheet([("Rejected v1 / 60% / original native view", native("rejected-v1/medium.png")),
               ("Revised M / 60% / same camera, shell, tick and exposure", native("medium-same-view.png"))],
              "m-before-after.png")
    if args.reference_medium:
        reference = Image.open(args.reference_medium).convert("RGB")
        reference = reference.resize((1200, round(reference.height * 1200 / reference.width)), Image.Resampling.LANCZOS)
        sheet([("Approved Blender M / 60% / reference lighting and camera", reference),
               ("Revised native Minecraft M / 60% / unmodified renderer output", native("load-60-tick-16.png"))],
              "m-reference-native.png")


if __name__ == "__main__":
    main()
