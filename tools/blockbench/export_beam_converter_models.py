"""Export the artist's 1x1x2 Beam Converter models and multipart state. Run with --output to regenerate elsewhere."""
import argparse
import json
from pathlib import Path

from export_beam_former_models import DIRS, NS, REPO, ROT, dir_name, export_elements, rotate


def export(output):
    assets = output / "assets" / NS
    models = assets / "models/block"
    models.mkdir(parents=True, exist_ok=True)
    (assets / "blockstates").mkdir(parents=True, exist_ok=True)

    def write(path, data):
        path.write_text(json.dumps(data, indent=2) + "\n")

    def model(name, source):
        textures = {str(i): f"{NS}:block/beam_converter/{Path(t['name']).stem}"
                    for i, t in enumerate(source["textures"])}
        textures["particle"] = f"{NS}:block/beam_converter/beam_converter_side"
        # Larger bodies still draw the Blender mesh from these atlases.
        textures["surface"] = f"{NS}:block/refined_devices/surface"
        textures["emission"] = f"{NS}:block/refined_devices/emission"
        write(models / f"{name}.json", {"parent": "minecraft:block/block", "render_type": "cutout",
                                       "textures": textures, "elements": export_elements(source)})

    projects = REPO / "tools/blockbench/projects"
    model("beam_converter", json.loads((projects / "beam_converter.bbmodel").read_text()))
    model("beam_converter_head", json.loads((projects / "beam_converter_restraints_formed.bbmodel").read_text()))
    parts = []
    for facing, (xr, yr) in ROT.items():
        def part(name, when):
            apply = {"model": f"{NS}:block/{name}"}
            if xr:
                apply["x"] = xr
            if yr:
                apply["y"] = yr
            parts.append({"when": when, "apply": apply})
        front = dir_name(rotate((0, 0, 1), xr, yr))
        back = dir_name(rotate((0, 0, -1), xr, yr))
        across = [direction for direction in DIRS if direction not in (front, back)]
        part("beam_converter", {"facing": facing, "formed": "false"})
        # A 1x1x2 member connects only along its facing: the rear one is the converter, the front one the head.
        alone = {direction: "false" for direction in across}
        part("beam_converter", {"facing": facing, "formed": "true", front: "true", back: "false", **alone})
        part("beam_converter_head", {"facing": facing, "formed": "true", front: "false", back: "true", **alone})
        # Members of larger bodies draw the Blender mesh; this model gives them only their particle.
        larger = [{direction: "true"} for direction in across] + [{front: value, back: value} for value in ("false", "true")]
        part("beam_converter", {"AND": [{"facing": facing, "formed": "true"}, {"OR": larger}]})
    write(assets / "blockstates/beam_converter.json", {"multipart": parts})
    print(f"Exported 2 Beam Converter models and {len(parts)} multipart cases.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=REPO / "common/src/main/resources")
    export(parser.parse_args().output)
