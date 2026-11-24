"""Export the artist's Beam Converter models and multipart state. Run with --output to regenerate elsewhere."""
import argparse
import itertools
import json
from pathlib import Path

from export_beam_former_models import AXES, CORNER_ST, DIRS, NS, REPO, ROT, dir_name, export_elements, rotate

# The 2x2 project spans x, y 0..32 and z -16..32: the converter body is the rear layer, the head the two in front.
LARGE_LAYERS = 3


def face_corners(name, fr, to, uv, k):
    """Each corner of a face as (position, texture coordinate), in Minecraft's vertex order and UV rotation."""
    (ax, sx), (ay, sy) = AXES[name]
    normal = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[name]
    at_max = name in ("south", "east", "up")
    corners = []
    for i, (gx, gy) in enumerate([(0, 0), (0, 1), (1, 1), (1, 0)]):
        position = [0.0, 0.0, 0.0]
        position[normal] = (to if at_max else fr)[normal]
        position[ax] = fr[ax] + (gx if sx > 0 else 1 - gx) * (to[ax] - fr[ax])
        position[ay] = fr[ay] + (gy if sy > 0 else 1 - gy) * (to[ay] - fr[ay])
        s, t = CORNER_ST[(i + k) % 4]
        corners.append((tuple(round(c, 4) for c in position),
                        (round(uv[0] + s * (uv[2] - uv[0]), 4), round(uv[1] + t * (uv[3] - uv[1]), 4))))
    return corners


def quarter_turn(point, origin):
    """A quarter turn about the z axis through origin, the way Minecraft turns a positive z angle."""
    x, y, z = point
    return (origin[0] - (y - origin[1]), origin[1] + (x - origin[0]), z)


def bake_quarter_turns(element):
    """Minecraft allows element angles of at most 45 degrees: turn the rest of a z angle into the geometry itself."""
    rot = element.get("rotation") or [0, 0, 0]
    if not rot[2] or abs(rot[2]) <= 45:
        return element
    assert not rot[0] and not rot[1], element["name"]
    turns = round(rot[2] / 90)
    origin = element.get("origin", [8, 8, 8])
    fr, to, faces = list(element["from"]), list(element["to"]), element["faces"]
    for _ in range(turns % 4):
        a, b = quarter_turn(fr, origin), quarter_turn(to, origin)
        nfr, nto = [min(p, q) for p, q in zip(a, b)], [max(p, q) for p, q in zip(a, b)]
        turned = {}
        for name, face in faces.items():
            if face.get("texture") is None:
                continue
            want = {quarter_turn(p, origin): uv for p, uv in face_corners(name, fr, to, face["uv"], face.get("rotation", 0) // 90)}
            want = {tuple(round(c, 4) for c in p): uv for p, uv in want.items()}
            normal = DIRS[name]
            new_name = dir_name((-normal[1], normal[0], normal[2]))
            u1, v1, u2, v2 = face["uv"]
            for uv in ([u1, v1, u2, v2], [u2, v1, u1, v2], [u1, v2, u2, v1], [u2, v2, u1, v1]):
                match = next((k for k in range(4)
                              if dict(face_corners(new_name, nfr, nto, uv, k)) == want), None)
                if match is not None:
                    turned[new_name] = {**face, "uv": uv, "rotation": 90 * match}
                    if not match:
                        del turned[new_name]["rotation"]
                    break
            else:
                raise ValueError(f"no face for {element['name']} {name} turned")
        fr, to, faces = nfr, nto, turned
    rest = rot[2] - 90 * turns
    return {**element, "from": fr, "to": to, "faces": faces, "rotation": [0, 0, rest] if rest else [0, 0, 0]}


def export(output):
    assets = output / "assets" / NS
    models = assets / "models/block"
    models.mkdir(parents=True, exist_ok=True)
    (assets / "blockstates").mkdir(parents=True, exist_ok=True)

    def write(path, data):
        path.write_text(json.dumps(data, indent=2) + "\n")

    def model(name, source, elements=None):
        textures = {str(i): f"{NS}:block/beam_converter/{Path(t['name']).stem}"
                    for i, t in enumerate(source["textures"])}
        textures["particle"] = f"{NS}:block/beam_converter/beam_converter_side"
        # The 3x3x6 body still draws the Blender mesh from these atlases.
        textures["surface"] = f"{NS}:block/refined_devices/surface"
        textures["emission"] = f"{NS}:block/refined_devices/emission"
        write(models / f"{name}.json", {"parent": "minecraft:block/block", "render_type": "cutout",
                                       "textures": textures,
                                       "elements": export_elements(source) if elements is None else elements})

    projects = REPO / "tools/blockbench/projects"
    model("beam_converter", json.loads((projects / "beam_converter.bbmodel").read_text()))
    model("beam_converter_head", json.loads((projects / "beam_converter_restraints_formed.bbmodel").read_text()))
    large = json.loads((projects / "large_beam_converter.bbmodel").read_text())
    large = {**large, "elements": [bake_quarter_turns(element) for element in large["elements"]]}
    members = list(itertools.product((0, 1), (0, 1), range(LARGE_LAYERS)))
    for qx, qy, layer in members:
        lo = (16 * qx, 16 * qy, 16 * layer - 16)
        hi = tuple(c + 16 for c in lo)
        model(f"beam_converter_large_{qx}{qy}{layer}", large, export_elements(large, (lo, hi), lo))
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
        positive_x = dir_name(rotate((1, 0, 0), xr, yr))
        negative_x = dir_name(rotate((-1, 0, 0), xr, yr))
        positive_y = dir_name(rotate((0, 1, 0), xr, yr))
        negative_y = dir_name(rotate((0, -1, 0), xr, yr))
        part("beam_converter", {"facing": facing, "formed": "false"})
        # A 1x1x2 member connects only along its facing: the rear one is the converter, the front one the head.
        alone = {positive_x: "false", negative_x: "false", positive_y: "false", negative_y: "false"}
        part("beam_converter", {"facing": facing, "formed": "true", front: "true", back: "false", **alone})
        part("beam_converter_head", {"facing": facing, "formed": "true", front: "false", back: "true", **alone})
        # A 2x2x3 member has one connection on each axis across the front; its connections along the facing tell its
        # layer. A 3x3x6 corner has the same connections: the baked model draws the Blender mesh there instead.
        for qx, qy, layer in members:
            when = {"facing": facing, "formed": "true",
                    positive_x: "true" if qx == 0 else "false", negative_x: "true" if qx == 1 else "false",
                    positive_y: "true" if qy == 0 else "false", negative_y: "true" if qy == 1 else "false",
                    front: "true" if layer < LARGE_LAYERS - 1 else "false", back: "true" if layer > 0 else "false"}
            part(f"beam_converter_large_{qx}{qy}{layer}", when)
        # Every other formed member is in a 3x3x6 body (or a broken one): this model gives it only its particle.
        others = [{front: "false", back: "false"},
                  {positive_x: "true", negative_x: "true"}, {positive_y: "true", negative_y: "true"}]
        for (p, n), (q, m) in (((positive_x, negative_x), (positive_y, negative_y)),
                               ((positive_y, negative_y), (positive_x, negative_x))):
            others += [{p: "false", n: "false", q: "true"}, {p: "false", n: "false", m: "true"},
                       {front: "true", back: "true", p: "false", n: "false"}]
        part("beam_converter", {"AND": [{"facing": facing, "formed": "true"}, {"OR": others}]})
    write(assets / "blockstates/beam_converter.json", {"multipart": parts})
    print(f"Exported {2 + len(members)} Beam Converter models and {len(parts)} multipart cases.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=REPO / "common/src/main/resources")
    export(parser.parse_args().output)
