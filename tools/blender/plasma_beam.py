"""The chosen 2x2 middle beam, R / PLASMA, at the precision the game can draw, on runs, branches and turns (stdlib).

Coordinates are blocks in Minecraft's axes. Beam paths follow CableBeamMesh.build: a branch is straight arms meeting at
the joint's centre, a turn a rounded elbow (radius 3/16 of the joint, a 2x2x2 box here) with the pulse phase faded
from one arm's axis to the other's. The joint's own shell is left out of the junction scenes; the runs' shells stop
open at it.
"""
import math

from bundle_beams import RING_PROJECT, ROOT, TAU, Mesh, Style, model_quads, texel

CORNER_PROJECT = ROOT / 'tools/blockbench/projects/large_cable_corner.bbmodel'

# key: (title, sides round a sheath, samples per block, sheaths, soft edges)
VARIANTS = {
    'approved': ('R / AS APPROVED 24 SIDES', 24, 48, 3, True),
    'ship_soft': ('R / SHIPPABLE SOFT', 16, 16, 3, True),
    'ship_six': ('R / SHIPPABLE SIX HARD SHELLS', 12, 16, 6, False),
}
CORE_SIDES = 8
HALO_SIDES = 8


# --- Beam paths -------------------------------------------------------------------------------------------------

class Sample:
    """A point of a centreline, its frame (n, b) and the axes whose world coordinate sets its phase, mixed by `mix`."""

    def __init__(self, point, n, b, first, second, mix):
        self.point, self.n, self.b, self.first, self.second, self.mix = point, n, b, first, second, mix


def unit(axis, sign=1):
    v = [0, 0, 0]
    v[axis] = sign
    return tuple(v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def straight(axis, start, end, centre, per_block):
    """An arm along `axis` from `start` to `end` through `centre` (its other coordinates), as Java frames it."""
    count = max(1, round(abs(end - start) * per_block))
    n = unit((axis + 1) % 3)
    b = cross(unit(axis), n)
    samples = []
    for i in range(count + 1):
        point = list(centre)
        point[axis] = start + (end - start) * i / count
        samples.append(Sample(tuple(point), n, b, axis, axis, 0))
    return samples


def elbow(centre, a, sa, b, sb, size, per_block):
    """CableBeamMesh's elbow through a joint of edge `size` about `centre`, from the `sa` face of axis `a` to `sb` of
    `b`; the frame's normal is the third axis throughout."""
    third = 3 - a - b
    n = unit(third)
    radius = .1875 * size
    count = max(8, round(size * per_block))
    points = []
    for i in range(count + 1):
        t = i / count
        p = list(centre)
        if t < .3125:
            p[a] += sa * (.5 - t) * size
            mix = 0
        elif t > .6875:
            p[b] += sb * (t - .5) * size
            mix = 1
        else:
            mix = (t - .3125) / .375
            angle = mix * math.pi / 2
            p[a] += sa * radius * (1 - math.sin(angle))
            p[b] += sb * radius * (1 - math.cos(angle))
        points.append((tuple(p), mix))
    samples = []
    for i, (p, mix) in enumerate(points):
        before, after = points[max(0, i - 1)][0], points[min(count, i + 1)][0]
        t = [q - r for q, r in zip(after, before)]
        length = math.sqrt(sum(c * c for c in t))
        t = tuple(c / length for c in t)
        samples.append(Sample(p, n, cross(t, n), a, b, mix))
    return samples


# --- R ----------------------------------------------------------------------------------------------------------

def sheaths(st, core, shells):
    """(base radius, lobes, speed, depth, colour, alpha) of each sheath, inside out."""
    pale = st.tint((.55, .95, 1), (1, .62, .55))
    shoulder = st.tint((.14, .85, 1))
    middle = st.tint((.08, .7, 1), (1, .025, .012))
    if st.overloaded:
        # The alarm must read red: no pale sheath that would add up to white.
        pale = (1, .25, .18)
    if shells == 3:
        return [(core * 1.6, 3, 1, .22, pale, .40), (.22 + .04 * st.u, 4, -1, .16, shoulder, .32),
                (.31 + .02 * st.u, 5, 2, .10, middle, .22)]
    # Six thinner shells carry the same light in smaller steps, so hard edges band less.
    inner, outer = core * 1.5, .33 + .02 * st.u
    colours = (pale, pale, shoulder, shoulder, middle, middle)
    alphas = (.22, .20, .17, .15, .12, .10)
    return [(inner + (outer - inner) * k / 5, (3, 3, 4, 4, 5, 5)[k], (1, 1, -1, -1, 2, 2)[k],
             (.22, .20, .17, .15, .12, .10)[k], colours[k], alphas[k]) for k in range(6)]


def tube(mesh, samples, radius, material, sides, caps=False):
    rings = []
    for sample in samples:
        ring = []
        for j in range(sides):
            phi = TAU * j / sides
            r = radius(sample, phi)
            c, s = r * math.cos(phi), r * math.sin(phi)
            ring.append(tuple(p + c * n + s * b for p, n, b in zip(sample.point, sample.n, sample.b)))
        rings.append(ring)
    for before, after in zip(rings, rings[1:]):
        for j in range(sides):
            k = (j + 1) % sides
            mesh.face([before[j], before[k], after[k], after[j]], material)
    if caps:
        mesh.face(rings[0][::-1], material)
        mesh.face(rings[-1], material)


def generate(variant, load, cycle, paths):
    """R on each path (a list of Samples) at `load`, `cycle` of its 64-tick loop."""
    _, sides, _, shells, soft = VARIANTS[variant]
    st = Style(load, cycle)
    mesh = Mesh()
    mode = 'soft' if soft else 'add'
    hot = st.tint((.96, 1, 1), (1, .9, .82))
    halo_rgb = st.tint((.15, .68, 1), (1, .025, .012))
    core = (.10 + .05 * st.u) * st.breath
    solid = mesh.material('core', hot, min(1, st.strength), 'solid')
    # Even idle, the sheaths keep a third of their light, so an idle 2x2 still reads as one broad beam; past 1.15 they
    # stop brightening, so full load keeps its colour instead of clipping to white.
    opacity = (.35 + .65 * st.u) * min(max(st.strength, .8), 1.15)
    layers = [(spec, mesh.material('sheath %d' % i, spec[4], min(1, spec[5] * opacity), mode))
              for i, spec in enumerate(sheaths(st, core, shells))]
    halo = mesh.material('halo', halo_rgb, min(1, .10 * opacity), mode)

    def at(sample, value):
        # A value read along the first axis, faded into the second's across an elbow, as Java's pulses are.
        first = value(sample.point[sample.first])
        if sample.first == sample.second:
            return first
        return first + (value(sample.point[sample.second]) - first) * sample.mix

    for samples in paths:
        tube(mesh, samples, lambda sample, phi: core, solid, CORE_SIDES, caps=True)
        for (base, lobes, speed, depth, _, _), material in layers:
            # Lobes twist round the axis and roll along it, repeating every 2 blocks as the game's beam clock does;
            # the wave swells them as the pulses pass.
            radius = (lambda sample, phi, base=base, lobes=lobes, speed=speed, depth=depth: base * at(
                sample, lambda s: 1 + depth * math.sin(lobes * phi + TAU * (s / 2 - speed * st.cycle))
                + .18 * st.wave(s) * st.u))
            tube(mesh, samples, radius, material, sides)
        tube(mesh, samples, lambda sample, phi: .44 * st.breath, halo, HALO_SIDES)
    return mesh


def quads_per_block(variant):
    """Quads the game would draw per block of straight run, unmerged: sides x samples for each tube."""
    _, sides, per_block, shells, _ = VARIANTS[variant]
    return per_block * (CORE_SIDES + shells * sides + HALO_SIDES)


# --- Scenes -----------------------------------------------------------------------------------------------------

def run_paths(variant, scene):
    per_block = VARIANTS[variant][2]
    if scene == 'straight':
        return [straight(0, .5, 5.5, (0, 1, 1), per_block)]
    if scene == 'branch':
        # A run along X with a branch up from the joint at x 2..4; the run's beam goes straight through.
        return [straight(0, .5, 7.5, (0, 1, 1), per_block), straight(1, 1, 5.5, (3, 0, 1), per_block)]
    if scene == 'turn':
        return [straight(0, .5, 2, (0, 1, 1), per_block), elbow((3, 1, 1), 0, -1, 1, 1, 2, per_block),
                straight(1, 2, 5.5, (3, 0, 1), per_block)]
    raise KeyError(scene)


# Shell pieces: (axis, from, to, a point on the piece's axis line in blocks, closed at from, closed at to).
SHELLS = {
    'straight': [(0, 0, 6, (0, 1, 1), True, True)],
    'branch': [(0, 0, 2, (0, 1, 1), True, False), (0, 4, 8, (0, 1, 1), False, True),
               (1, 2, 6, (3, 0, 1), False, True)],
    'turn': [(0, 0, 2, (0, 1, 1), True, False), (1, 2, 6, (3, 0, 1), False, True)],
}
# Straight rings: (axis, boundary, centre); corner rings: low corner of the 2x2x2 joint. Rings skip the boundaries
# next to the layers a branch leaves.
RINGS = {
    'straight': ([(0, 2, (1, 1)), (0, 4, (1, 1))], []),
    'branch': ([(0, 6, (1, 1)), (1, 4, (3, 1))], [(2, 0, 0)]),
    'turn': ([(1, 4, (3, 1))], [(2, 0, 0)]),
}
SCENE_SIZE = {'straight': (6, 2), 'branch': (8, 6), 'turn': (4, 6)}


def shell_tiles(scene):
    """The voxel squares of each shell piece with their core texels: rails where a piece's face ends, glass between."""
    tiles = []
    for axis, start, end, centre, low_closed, high_closed in SHELLS[scene]:
        u, v = (axis + 1) % 3, (axis + 2) % 3
        lo = start * 16 + (2 if low_closed else 0)
        hi = end * 16 - (2 if high_closed else 0)
        cu, cv = round(centre[u] * 16), round(centre[v] * 16)

        def point(along, a, b, axis=axis, u=u, v=v):
            p = [0, 0, 0]
            p[axis], p[u], p[v] = along, a, b
            return tuple(c / 16 for c in p)

        def panel(corner, u_range, v_range, closed):
            for a in range(*u_range):
                for b in range(*v_range):
                    quad = [corner(a, b), corner(a + 1, b), corner(a + 1, b + 1), corner(a, b + 1)]
                    tiles.append((quad, texel(a, b, *u_range, *v_range, closed=closed)))

        ends = (low_closed, high_closed, True, True)
        for side in (cu - 14, cu + 14):
            panel(lambda a, b, side=side: point(a, side, b), (lo, hi), (cv - 14, cv + 14), ends)
        for side in (cv - 14, cv + 14):
            panel(lambda a, b, side=side: point(a, b, side), (lo, hi), (cu - 14, cu + 14), ends)
        for at, closed in ((lo, low_closed), (hi, high_closed)):
            if closed:
                panel(lambda a, b, at=at: point(at, a, b), (cu - 14, cu + 14), (cv - 14, cv + 14), (True,) * 4)
    return tiles


def ring_quads(scene):
    """The artist's straight rings on their boundaries and corner rings in each joint, in blocks."""
    straight_rings, corners = RINGS[scene]
    quads = []
    model = model_quads(RING_PROJECT)
    for axis, boundary, centre in straight_rings:
        for points, uvs in model:
            placed = []
            for x, y, z in points:
                if axis == 0:
                    # Model Z (the ring's axis) onto X, model X onto -Z.
                    placed.append((boundary + (z - 16) / 16, centre[0] - 1 + y / 16, centre[1] - 1 + (32 - x) / 16))
                else:
                    # Model Z onto Y, model Y onto -Z.
                    placed.append((centre[0] - 1 + x / 16, boundary + (z - 16) / 16, centre[1] - 1 + (32 - y) / 16))
            quads.append((placed, uvs))
    for x0, y0, z0 in corners:
        # The corner model's Y (across its two flat rings) onto the third axis, Z here; its Z onto -Y.
        for points, uvs in model_quads(CORNER_PROJECT):
            quads.append(([(x0 + x / 16, y0 + (32 - z) / 16, z0 + y / 16) for x, y, z in points], uvs))
    return quads

