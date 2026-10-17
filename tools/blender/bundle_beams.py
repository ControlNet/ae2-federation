"""Concepts for the middle beam of a 2x2 bundled cable, and the shell and rings around it (stdlib only).

Everything is in blocks, in Minecraft's axes, for a straight run along X: the run's cross-section spans 0..2 in Y and
Z around the axis at (1, 1). Loads are preview inputs (capacity count over limit), not measured traffic.
"""
import json
import math
from pathlib import Path

from beam_load import alarm
from optical_beams import packet

TAU = math.tau
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'common/src/main/resources/assets/ae2federation'
CORE_TEXTURE = ASSETS / 'textures/part/cable/dense/core.png'
RING_TEXTURE = ASSETS / 'textures/block/large_cable/large_cable.png'
RING_PROJECT = ROOT / 'tools/blockbench/projects/large_cable_straight.bbmodel'

# The artist's ring bars leave 12 of 32 voxels, but its 1-voxel flanges narrow the bore to 10: anything that writes
# depth must pass through that.
RING_OPENING = 10 / 32
SOLID_LIMIT = .30
# The shell sits 2 voxels inside the cross-section, as an ordinary cable's core does.
SHELL_INSET = 2 / 16
RING_SPACING = 2
LENGTH = 6

PROFILES = {
    'base': ('BASE / CURRENT TUBE', 'The shipped volume tube: the ordinary cable beam scaled 2x across.'),
    'q_column': ('Q / SOLID COLUMN', 'A thick white-hot column near the ring opening, with a dense shoulder.'),
    'r_plasma': ('R / PLASMA', 'A broad core inside twisting plasma sheaths that roll along the run.'),
    's_braid': ('S / FOUR STRAND BRAID', 'Four beams, one per merged cable, braided round a shared core.'),
    't_accelerator': ('T / ACCELERATOR', 'A thick core with bright bands that travel through the rings.'),
    'u_flood': ('U / FLOODED SHELL', 'A heavy core whose glow fills the whole glass, the rings dark against it.'),
    'v_pinch': ('V / RING PINCH', 'A fat plasma body that bulges between the rings and is squeezed through each.'),
}
LOADS = {'idle': 0.0, 'medium': .6, 'full': 1.0}


class Mesh:
    """Faces with a material index each; `materials` lists (name, rgb, alpha, mode) with mode 'solid' or 'add'."""

    def __init__(self):
        self.vertices = []
        self.faces = []
        self.indices = []
        self.materials = []

    def material(self, name, rgb, alpha, mode):
        self.materials.append((name, tuple(rgb), alpha, mode))
        return len(self.materials) - 1

    def face(self, points, material):
        first = len(self.vertices)
        self.vertices.extend(points)
        self.faces.append(tuple(range(first, first + len(points))))
        self.indices.append(material)


def sweep(mesh, start, end, radius, material, center=lambda s: (0, 0), sides=12, segments=None, caps=True):
    """A tube along X from start to end; radius(s, phi) may vary round the tube as well as along it."""
    segments = segments or max(2, round((end - start) * 48))
    rings = []
    for i in range(segments + 1):
        s = start + (end - start) * i / segments
        dy, dz = center(s)
        ring = []
        for j in range(sides):
            phi = TAU * j / sides
            r = radius(s, phi)
            ring.append((s, 1 + dy + r * math.cos(phi), 1 + dz + r * math.sin(phi)))
        rings.append(ring)
    for before, after in zip(rings, rings[1:]):
        for j in range(sides):
            k = (j + 1) % sides
            mesh.face([before[j], before[k], after[k], after[j]], material)
    if caps:
        mesh.face(rings[0][::-1], material)
        mesh.face(rings[-1], material)


class Style:
    """The shipped load response (CableBeamStyle.fromLoad), in the 2x2 frame: radii doubled across."""

    def __init__(self, load, cycle):
        self.load = load
        self.u = min(load, 1)
        self.overloaded = load > 1
        self.cycle = cycle
        self.core = 2 * (.018 + .027 * self.u)
        self.halo = 2 * (.06 + .09 * self.u)
        self.amplitude = 2 * .028 * self.u
        self.density = 1 + 2 * self.u
        self.emission = .45 + 1.55 * self.u
        self.strength = self.emission * (.7 + .4 * alarm(cycle) if self.overloaded else 1)
        self.breath = 1 + (.1 * alarm(cycle) if self.overloaded else .018 * math.sin(TAU * cycle))
        self.opacity = (.12 + .88 * self.u) * self.strength

    def wave(self, s):
        low = math.floor(self.density)
        blend = self.density - low
        return ((1 - blend) * packet(s, self.cycle * low, period=2 / low, sharpness=12)
                + blend * packet(s, self.cycle * (low + 1), period=2 / (low + 1), sharpness=12))

    def tint(self, cyan, red=(1, .045, .015)):
        return red if self.overloaded else cyan


def generate(profile, load, cycle, start=.5, end=LENGTH - .5):
    """The beam of one concept from `start` to `end` along the run, at `cycle` of its 64-tick loop."""
    st = Style(load, cycle)
    mesh = Mesh()
    pale = st.tint((.55, .95, 1), (1, .62, .55))
    hot = st.tint((.96, 1, 1), (1, .9, .82))
    shoulder_rgb = st.tint((.14, .85, 1))
    middle_rgb = st.tint((.08, .7, 1), (1, .025, .012))
    halo_rgb = st.tint((.15, .68, 1), (1, .025, .012))
    solid = lambda name, rgb: mesh.material(name, rgb, min(1, st.strength), 'solid')
    glow = lambda name, rgb, alpha: mesh.material(name, rgb, min(1, alpha * st.opacity), 'add')
    tube = lambda radius, material, **kw: sweep(mesh, start, end, radius, material, **kw)

    if profile == 'base':
        tube(lambda s, p: st.core * st.breath, solid('core', pale), sides=8)
        tube(lambda s, p: st.core * 1.6 + st.amplitude * st.wave(s), glow('shoulder', shoulder_rgb, .35), sides=8)
        tube(lambda s, p: st.halo * .62 * st.breath + .35 * st.amplitude * st.wave(s),
             glow('middle', middle_rgb, .30), sides=8)
        tube(lambda s, p: st.halo * st.breath, glow('halo', halo_rgb, .10), sides=8)
    elif profile == 'q_column':
        core = (.10 + .10 * st.u) * st.breath
        tube(lambda s, p: core * .55, solid('white core', hot))
        tube(lambda s, p: core, glow('core skin', pale, .55))
        tube(lambda s, p: core * 1.35 + .6 * st.amplitude * st.wave(s), glow('shoulder', shoulder_rgb, .45))
        tube(lambda s, p: .27 + .02 * st.u + .5 * st.amplitude * st.wave(s), glow('middle', middle_rgb, .32))
        tube(lambda s, p: .46 * st.breath, glow('halo', halo_rgb, .12))
    elif profile == 'r_plasma':
        core = (.08 + .06 * st.u) * st.breath
        tube(lambda s, p: core, solid('core', hot))

        def sheath(base, lobes, speed, depth):
            # Lobes twist round the axis and roll along it, repeating every 2 blocks as the game's beam clock does;
            # the wave swells them as the pulses pass.
            return lambda s, p: base * (1 + depth * math.sin(lobes * p + TAU * (s / 2 - speed * st.cycle))
                                        + .6 * st.wave(s) * st.u * .3)
        tube(sheath(core * 1.6, 3, 1, .22), glow('inner sheath', pale, .40), sides=24)
        tube(sheath(.22 + .04 * st.u, 4, -1, .16), glow('sheath', shoulder_rgb, .32), sides=24)
        tube(sheath(.31 + .02 * st.u, 5, 2, .10), glow('outer sheath', middle_rgb, .22), sides=24)
        tube(lambda s, p: .44 * st.breath, glow('halo', halo_rgb, .10))
    elif profile == 's_braid':
        core = (.05 + .04 * st.u) * st.breath
        strand = (.04 + .025 * st.u) * st.breath
        orbit = .17 + .03 * st.u
        tube(lambda s, p: core, solid('core', pale))
        strand_core = solid('strand', hot)
        strand_glow = glow('strand glow', shoulder_rgb, .40)
        for k in range(4):
            angle = lambda s, k=k: k * TAU / 4 + TAU * (s / 2 - st.cycle)
            center = lambda s, angle=angle: (orbit * math.cos(angle(s)), orbit * math.sin(angle(s)))
            tube(lambda s, p: strand, strand_core, center=center, sides=8, segments=round((end - start) * 32))
            tube(lambda s, p: strand * 1.9 + .3 * st.amplitude * st.wave(s), strand_glow, center=center, sides=8,
                 segments=round((end - start) * 32))
        tube(lambda s, p: .30 + .4 * st.amplitude * st.wave(s), glow('envelope', middle_rgb, .26))
        tube(lambda s, p: .42 * st.breath, glow('halo', halo_rgb, .10))
    elif profile == 't_accelerator':
        core = (.09 + .07 * st.u) * st.breath
        tube(lambda s, p: core, solid('core', hot))
        tube(lambda s, p: core * 1.5, glow('shoulder', shoulder_rgb, .40))
        # Bands two blocks apart, as the rings are, travelling one spacing per loop; more of them under load.
        bands = lambda s: max(packet(s, st.cycle * k, period=RING_SPACING / k, sharpness=40)
                              for k in (1, 2) if k == 1 or st.u > 0)
        tube(lambda s, p: core * 1.5 + (.30 - core * 1.5) * bands(s), glow('band', pale, .55),
             segments=round((end - start) * 64))
        tube(lambda s, p: .30, glow('sheath', middle_rgb, .18))
        tube(lambda s, p: .42 * st.breath, glow('halo', halo_rgb, .08))
    elif profile == 'u_flood':
        core = (.12 + .06 * st.u) * st.breath
        tube(lambda s, p: core, solid('core', hot))
        tube(lambda s, p: core * 1.5 + .5 * st.amplitude * st.wave(s), glow('shoulder', shoulder_rgb, .45))
        tube(lambda s, p: .40 + .5 * st.amplitude * st.wave(s), glow('middle', middle_rgb, .28))

        def squircle(half):
            # A rounded square just inside the shell, so the whole glass lights evenly.
            return lambda s, p: half / (abs(math.cos(p)) ** 4 + abs(math.sin(p)) ** 4) ** .25
        tube(squircle(.72), glow('fill', halo_rgb, .16), sides=32)
        tube(squircle(.84), glow('fill edge', halo_rgb, .10), sides=32)
    elif profile == 'v_pinch':
        # 0 on a ring's boundary, 1 halfway between two; rings sit on world boundaries RING_SPACING apart.
        bulge = lambda s: .5 - .5 * math.cos(TAU * s / RING_SPACING)
        core = (.11 + .06 * st.u) * st.breath
        tube(lambda s, p: core, solid('core', hot))
        tube(lambda s, p: .16 + .05 * st.u + (.08 + .12 * st.u) * bulge(s) + .4 * st.amplitude * st.wave(s),
             glow('inner body', pale, .45))
        tube(lambda s, p: .24 + (.14 + .22 * st.u) * bulge(s) + .5 * st.amplitude * st.wave(s),
             glow('body', shoulder_rgb, .35), sides=16)
        tube(lambda s, p: (.30 + (.20 + .25 * st.u) * bulge(s)) * st.breath, glow('halo', halo_rgb, .12), sides=16)
    else:
        raise KeyError(profile)
    return mesh


def solid_reach(mesh):
    """The largest distance from the axis of any face that writes depth."""
    reach = 0
    for face, index in zip(mesh.faces, mesh.indices):
        if mesh.materials[index][3] != 'solid':
            continue
        for i in face:
            _, y, z = mesh.vertices[i]
            reach = max(reach, math.hypot(y - 1, z - 1))
    return reach



# --- Shell, from BundleShellLayout's rule on the real core texture ---------------------------------------------

RAIL = 3
FRAME = 2
STRAIGHT_ROW = 6
GLASS = 6


def texel(a, b, u0, u1, v0, v1, closed=(True, True, True, True)):
    """The core texel (column, row) of voxel a, b of a panel; `closed` says which of its edges u0, u1, v0, v1 end the
    shell's face and so carry a rail."""
    best = RAIL
    corner_u = corner_v = -1
    for across, at, low, u_edge, rail in ((a, u0, True, True, closed[0]), (a, u1, False, True, closed[1]),
                                          (b, v0, True, False, closed[2]), (b, v1, False, False, closed[3])):
        if not rail:
            continue
        perp = across - at if low else at - (across + 1)
        if 0 <= perp < RAIL:
            best = min(best, perp)
            if u_edge:
                corner_u = perp
            else:
                corner_v = perp
    if corner_u >= 0 and corner_v >= 0:
        return FRAME + corner_u, FRAME + corner_v
    if best < RAIL:
        return FRAME + best, STRAIGHT_ROW
    return GLASS, GLASS


def shell_tiles(length=LENGTH, open_end=False):
    """The shell's faces as voxel squares: (corners, texel). With `open_end` the far cap is left off for a cutaway."""
    lo, hi = 2, 30
    along_lo, along_hi = 2, 16 * length - 2
    tiles = []

    def panel(point, u_range, v_range):
        for a in range(*u_range):
            for b in range(*v_range):
                corners = [point(a, b), point(a + 1, b), point(a + 1, b + 1), point(a, b + 1)]
                tiles.append(([tuple(c / 16 for c in p) for p in corners],
                              texel(a, b, u_range[0], u_range[1], v_range[0], v_range[1])))

    for side in (lo, hi):
        panel(lambda a, b, side=side: (a, side, b), (along_lo, along_hi), (lo, hi))
        panel(lambda a, b, side=side: (a, b, side), (along_lo, along_hi), (lo, hi))
    panel(lambda a, b: (along_lo, a, b), (lo, hi), (lo, hi))
    if not open_end:
        panel(lambda a, b: (along_hi, a, b), (lo, hi), (lo, hi))
    return tiles


# --- The artist's straight ring --------------------------------------------------------------------------------

FACE_AXES = {
    # Face -> (fixed axis, value index (0 low / 1 high), u from, v from) as Minecraft's default UVs run.
    'north': (2, 0, (0, -1), (1, -1)),
    'south': (2, 1, (0, 1), (1, -1)),
    'west': (0, 0, (2, 1), (1, -1)),
    'east': (0, 1, (2, -1), (1, -1)),
    'up': (1, 1, (0, 1), (2, 1)),
    'down': (1, 0, (0, 1), (2, -1)),
}


def face_uv(face, rotation, fu, fv):
    """The texture point (in 0..1 of the 32-pixel image) at face position fu, fv, Blockbench's UV and rotation."""
    for _ in range(rotation // 90):
        fu, fv = fv, 1 - fu
    u0, v0, u1, v1 = face
    return (u0 + (u1 - u0) * fu) / 32, (v0 + (v1 - v0) * fv) / 32


def model_quads(project_path):
    """The faces of a Blockbench project as quads in its 32-voxel frame, each with its texture UVs (0..1)."""
    project = json.loads(Path(project_path).read_text())
    quads = []
    for element in project['elements']:
        low, high = element['from'], element['to']
        for side, face in element['faces'].items():
            if face.get('texture') is None:
                continue
            axis, end, (ua, us), (va, vs) = FACE_AXES[side]
            corners = []
            for fu, fv in ((0, 0), (1, 0), (1, 1), (0, 1)):
                point = [0, 0, 0]
                point[axis] = high[axis] if end else low[axis]
                point[ua] = low[ua] + (high[ua] - low[ua]) * (fu if us > 0 else 1 - fu)
                point[va] = low[va] + (high[va] - low[va]) * (fv if vs > 0 else 1 - fv)
                corners.append((tuple(point), face_uv(face['uv'], face.get('rotation', 0), fu, fv)))
            # Wind the quad to face outward.
            outward = (1 if end else -1)
            normal = cross(sub(corners[1][0], corners[0][0]), sub(corners[3][0], corners[0][0]))
            if normal[axis] * outward < 0:
                corners = corners[::-1]
            quads.append(([p for p, _ in corners], [uv for _, uv in corners]))
    return quads


def ring_quads(boundaries):
    """Quads (positions in blocks, uvs) of the straight ring on each boundary, turned so its axis runs along X."""
    quads = []
    for points, uvs in model_quads(RING_PROJECT):
        for boundary in boundaries:
            # The model's Z (its axis) turns onto X; its X onto -Z; the frame's middle (16) sits on the boundary.
            quads.append(([(boundary + (z - 16) / 16, y / 16, (32 - x) / 16) for x, y, z in points], uvs))
    return quads


def sub(a, b):
    return tuple(p - q for p, q in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def ring_boundaries(length=LENGTH):
    """Inner layer boundaries that carry a ring: every RING_SPACING layers, counted from the world origin."""
    return [x for x in range(1, length) if x % RING_SPACING == 0]
