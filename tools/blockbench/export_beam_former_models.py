"""Export the artist's Beam Former models and multipart state. Run with --output to regenerate elsewhere."""
import argparse
import itertools
import json
import math
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
NS = "ae2federation"

# Face corners in vertex order (Minecraft FaceInfo): geometric (gx, gy) per vertex is (0,0),(0,1),(1,1),(1,0).
# Each face's gx/gy axes as (model axis, sign): sign +1 means gx grows with the coordinate.
AXES = {"south": ((0, 1), (1, -1)), "north": ((0, -1), (1, -1)), "east": ((2, -1), (1, -1)),
        "west": ((2, 1), (1, -1)), "up": ((0, 1), (2, 1)), "down": ((0, 1), (2, -1))}
CORNER_ST = [(0, 0), (0, 1), (1, 1), (1, 0)]


def frac(lo, hi, c0, c1, sign):
    """Fractions along a face axis of the clipped span [c0,c1] inside the element span [lo,hi]."""
    a, b = (c0 - lo) / (hi - lo), (c1 - lo) / (hi - lo)
    return (a, b) if sign > 0 else (1 - b, 1 - a)


def clip_face(face, name, fr, to, cfr, cto):
    (ax, sx), (ay, sy) = AXES[name]
    g0, g1 = frac(fr[ax], to[ax], cfr[ax], cto[ax], sx)
    h0, h1 = frac(fr[ay], to[ay], cfr[ay], cto[ay], sy)
    k = face.get("rotation", 0) // 90
    # Geometric corner i samples st corner (i + k) % 4; map the clipped geometric rect's corners through that.
    def st(gx, gy):
        # Bilinear over the four geometric corners.
        c = [CORNER_ST[(i + k) % 4] for i in range(4)]
        g = [(0, 0), (0, 1), (1, 1), (1, 0)]
        s = t = 0.0
        for (px, py), (cs, ct) in zip(g, c):
            w = (gx if px else 1 - gx) * (gy if py else 1 - gy)
            s += w * cs
            t += w * ct
        return s, t
    s0, t0 = st(g0, h0)  # geometric corner 0 of the clipped rect
    s2, t2 = st(g1, h1)  # geometric corner 2
    u1, v1, u2, v2 = face["uv"]
    # Corner 0 samples st corner k, corner 2 samples st corner (2+k)%4; rebuild [u1,v1,u2,v2] from their st.
    sa, ta = CORNER_ST[k % 4]
    lo_s, hi_s = (s0, s2) if sa == 0 else (s2, s0)
    lo_t, hi_t = (t0, t2) if ta == 0 else (t2, t0)
    out = dict(face)
    out["uv"] = [u1 + lo_s * (u2 - u1), v1 + lo_t * (v2 - v1), u1 + hi_s * (u2 - u1), v1 + hi_t * (v2 - v1)]
    return out


def export_elements(model, clip=None, offset=(0, 0, 0)):
    res = model["resolution"]["width"]
    out = []
    for e in model["elements"]:
        fr, to = e["from"], e["to"]
        rot = e.get("rotation") or [0, 0, 0]
        origin = e.get("origin", [8, 8, 8])
        cfr, cto = list(fr), list(to)
        if clip is not None:
            lo, hi = clip
            if any(rot):
                # Rotated parts go whole to the member that holds their rotated centre.
                cx, cy = (fr[0] + to[0]) / 2 - origin[0], (fr[1] + to[1]) / 2 - origin[1]
                a = math.radians(rot[2])
                px = origin[0] + cx * math.cos(a) - cy * math.sin(a)
                py = origin[1] + cx * math.sin(a) + cy * math.cos(a)
                if not (lo[0] <= px < hi[0] and lo[1] <= py < hi[1]):
                    continue
            else:
                for i in (0, 1):
                    cfr[i], cto[i] = max(fr[i], lo[i]), min(to[i], hi[i])
                if cfr[0] >= cto[0] or cfr[1] >= cto[1]:
                    continue
        faces = {}
        for name, f in e["faces"].items():
            if f.get("texture") is None:
                continue
            # Faces on a clipped (inner) boundary plane are hidden by the neighbouring member's piece.
            if clip is not None and not any(rot):
                normal_axis = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[name]
                at_max = name in ("south", "east", "up")
                if normal_axis in (0, 1):
                    plane = cto[normal_axis] if at_max else cfr[normal_axis]
                    orig = to[normal_axis] if at_max else fr[normal_axis]
                    if plane != orig:
                        continue
            g = clip_face(f, name, fr, to, cfr, cto) if (cfr != fr or cto != to) else dict(f)
            uv_w = model["textures"][g["texture"]].get("uv_width", res)
            face = {"uv": [round(x * 16 / uv_w, 4) for x in g["uv"]], "texture": f"#{g['texture']}"}
            if g.get("rotation"):
                face["rotation"] = g["rotation"]
            if not any(rot):
                axis = {"west": 0, "east": 0, "down": 1, "up": 1, "north": 2, "south": 2}[name]
                positive = name in ("east", "up", "south")
                plane = (cto if positive else cfr)[axis] - offset[axis]
                if plane == (16 if positive else 0):
                    face["cullface"] = name
            faces[name] = face
        if not faces:
            continue
        el = {"name": e["name"], "from": [c - o for c, o in zip(cfr, offset)],
              "to": [c - o for c, o in zip(cto, offset)], "faces": faces}
        if any(rot):
            axis = "xyz"[[i for i, r in enumerate(rot) if r][0]]
            el["rotation"] = {"angle": [r for r in rot if r][0], "axis": axis,
                              "origin": [c - o for c, o in zip(origin, offset)]}
        out.append(el)
    return out


ROT = {"south": (0, 0), "west": (0, 90), "north": (0, 180), "east": (0, 270), "up": (90, 0), "down": (270, 0)}
DIRS = {"east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0), "south": (0, 0, 1),
        "north": (0, 0, -1)}


def rotate(v, xr, yr):
    x, y, z = v
    for _ in range(xr // 90):  # Rx(-90): (y, z) -> (z, -y)
        y, z = z, -y
    for _ in range(yr // 90):  # Ry(-90): (x, z) -> (-z, x)
        x, z = -z, x
    return x, y, z


def dir_name(v):
    return next(n for n, d in DIRS.items() if d == v)


def export(output):
    assets = output / "assets" / NS
    models = assets / "models/block"
    models.mkdir(parents=True, exist_ok=True)
    (assets / "blockstates").mkdir(parents=True, exist_ok=True)

    def write(path, data):
        path.write_text(json.dumps(data, indent=2) + "\n")

    def model(name, source, elements):
        textures = {str(i): f"{NS}:block/beam_emitter/{Path(t['name']).stem}"
                    for i, t in enumerate(source["textures"])}
        textures["particle"] = textures["1"]
        write(models / f"{name}.json", {"parent": "minecraft:block/block", "render_type": "cutout",
                                       "textures": textures, "elements": elements})

    projects = REPO / "tools/blockbench/projects"
    small = json.loads((projects / "beam_emitter.bbmodel").read_text())
    large = json.loads((projects / "large_beam_emitter.bbmodel").read_text())
    huge = json.loads((projects / "huge_beam_emitter.bbmodel").read_text())
    model("beam_former", small, export_elements(small))
    for qx, qy in itertools.product((0, 1), repeat=2):
        lo, hi = (16 * qx, 16 * qy), (16 * qx + 16, 16 * qy + 16)
        model(f"beam_former_large_{qx}{qy}", large, export_elements(large, (lo, hi), (*lo, 0)))
    # The 3x3 project spans -16..32 about its centre member.
    for mx, my in itertools.product((0, 1, 2), repeat=2):
        lo, hi = (16 * mx - 16, 16 * my - 16), (16 * mx, 16 * my)
        model(f"beam_former_huge_{mx}{my}", huge, export_elements(huge, (lo, hi), (*lo, 0)))
    parts = []
    for facing, (xr, yr) in ROT.items():
        def part(size, name, connections=None):
            apply = {"model": f"{NS}:block/{name}"}
            if xr:
                apply["x"] = xr
            if yr:
                apply["y"] = yr
            when = {"facing": facing, "size": size}
            if connections:
                when = {"AND": [when, connections]}
            parts.append({"when": when, "apply": apply})
        part("small", "beam_former")
        positive_x = dir_name(rotate((1, 0, 0), xr, yr))
        negative_x = dir_name(rotate((-1, 0, 0), xr, yr))
        positive_y = dir_name(rotate((0, 1, 0), xr, yr))
        negative_y = dir_name(rotate((0, -1, 0), xr, yr))
        axial = [dir_name(rotate((0, 0, sign), xr, yr)) for sign in (-1, 1)]
        for qx, qy in itertools.product((0, 1), repeat=2):
            connections = {direction: "false" for direction in DIRS}
            connections[positive_x if qx == 0 else negative_x] = "true"
            connections[positive_y if qy == 0 else negative_y] = "true"
            part("large", f"beam_former_large_{qx}{qy}", connections)
        # An intact member has one connection on each in-plane axis and none along its facing axis.
        fallback = [{direction: "true"} for direction in axial]
        for positive, negative in ((positive_x, negative_x), (positive_y, negative_y)):
            fallback.extend({positive: value, negative: value} for value in ("false", "true"))
        part("large", "beam_former", {"OR": fallback})
        # A 3x3 member's connections on each in-plane axis tell its column: +only, both, -only.
        for mx, my in itertools.product((0, 1, 2), repeat=2):
            connections = {direction: "false" for direction in DIRS}
            for index, positive, negative in ((mx, positive_x, negative_x), (my, positive_y, negative_y)):
                connections[positive] = "true" if index < 2 else "false"
                connections[negative] = "true" if index > 0 else "false"
            part("huge", f"beam_former_huge_{mx}{my}", connections)
        fallback = [{direction: "true"} for direction in axial]
        fallback.extend({positive: "false", negative: "false"}
                        for positive, negative in ((positive_x, negative_x), (positive_y, negative_y)))
        part("huge", "beam_former", {"OR": fallback})
    write(assets / "blockstates/beam_former.json", {"multipart": parts})
    print(f"Exported 14 Beam Former models and {len(parts)} multipart cases.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=REPO / "common/src/main/resources")
    export(parser.parse_args().output)
