"""Concepts for an ordinary 1x1 Federation Cable's beam: the shipped M beam and R / PLASMA fitted to its glass.

Standard library only. Positions are blocks in Minecraft's axes; each cable cell is drawn about its own centre, with
pulses and lobes phased from world coordinates, as the game's beams are. The 1x1 shell's glass window has a
half-width of 3/16 block (12-voxel core, 3-voxel rails): light outside it falls behind the rails, so every concept
keeps its glow within it. Loads are preview inputs (capacity count over limit), not measured traffic.
"""
import math

from bundle_beams import TAU, Mesh, Style
from plasma_beam import Sample, cross, tube, unit

WINDOW = 3 / 16
# R's radii in the 2x2 bundle, whose glass reaches 11/16 block from the axis.
BUNDLE_WINDOW = 11 / 16
BUNDLE_HALO = .44
# An alarm breathes the halo out by up to a tenth; filled to the glass, R must still fit then.
FILL = WINDOW / (BUNDLE_HALO * 1.1)

# key: (title, description, radius scale against the bundle's R, sheaths, sides round a sheath)
PROFILES = {
    'm_current': ('M / CURRENT', "The shipped beam, as tubes: a thin core, shoulders and halo with in-shell fog.",
                  None, 0, 0),
    'r_scaled': ('R / SAME PROPORTION', "The bundle's R at the same share of the glass as in a 2x2.",
                 WINDOW / BUNDLE_WINDOW, 3, 12),
    'r_fill': ('R / FILLS THE GLASS', "R scaled until its halo meets the glass's edge.", FILL, 3, 12),
    'r_lite': ('R / LITE', "R filling the glass with two sheaths of 8 sides, for many cables at once.",
               FILL, 2, 8),
}
LOADS = {'idle': 0.0, 'medium': .6, 'full': 1.0, 'overload': 1.25}
SAMPLES_PER_BLOCK = 16
CORE_SIDES = 8
HALO_SIDES = 8


def arms(mask, world):
    """The straight arms of a cell's beam, as world-coordinate Samples, from the cell's centre to each side it
    leaves by (bit 2 * axis: positive side, bit 2 * axis + 1: negative side), or a short stub when it has none."""
    paths = []
    centre = tuple(c + .5 for c in world)
    for axis in range(3):
        pair = (mask >> (2 * axis)) & 3
        if not pair:
            continue
        start = 0 if pair & 2 else .5
        end = 1 if pair & 1 else .5
        n = unit((axis + 1) % 3)
        b = cross(unit(axis), n)
        count = max(1, round((end - start) * SAMPLES_PER_BLOCK))
        samples = []
        for i in range(count + 1):
            point = list(centre)
            point[axis] = world[axis] + start + (end - start) * i / count
            samples.append(Sample(tuple(point), n, b, axis, axis, 0))
        paths.append(samples)
    return paths


def m_beam(mesh, st, paths):
    """M as the bundle study's BASE drew it, at a 1x1 cable's radii, with a faint fog filling the glass."""
    core, halo, amplitude = st.core / 2, st.halo / 2, st.amplitude / 2
    pale = st.tint((.55, .95, 1), (1, .62, .55))
    solid = mesh.material('core', pale, min(1, st.strength), 'solid')
    shoulder = mesh.material('shoulder', st.tint((.14, .85, 1)), min(1, .35 * st.opacity), 'add')
    middle = mesh.material('middle', st.tint((.08, .7, 1), (1, .025, .012)), min(1, .30 * st.opacity), 'add')
    halo_layer = mesh.material('halo', st.tint((.15, .68, 1), (1, .025, .012)), min(1, .10 * st.opacity), 'add')
    fog = mesh.material('fog', st.tint((.10, .72, 1), (1, .10, .04)), min(1, .06 * (.35 + .65 * st.u)
                                                                           * st.strength), 'soft')
    wave = lambda sample: st.wave(sample.point[sample.first])
    for samples in paths:
        tube(mesh, samples, lambda sample, phi: core * st.breath, solid, CORE_SIDES, caps=True)
        tube(mesh, samples, lambda sample, phi: core * 1.6 + amplitude * wave(sample), shoulder, CORE_SIDES)
        tube(mesh, samples, lambda sample, phi: halo * .62 * st.breath + .35 * amplitude * wave(sample), middle,
             CORE_SIDES)
        tube(mesh, samples, lambda sample, phi: halo * st.breath, halo_layer, HALO_SIDES)
        # The fog is a rounded square just inside the glass, as the shipped fog volumes fill the shell.
        tube(mesh, samples, lambda sample, phi: .95 * WINDOW / (abs(math.cos(phi)) ** 4 + abs(math.sin(phi)) ** 4)
             ** .25, fog, 16)


def r_beam(mesh, st, paths, scale, shells, sides):
    """BundleBeamMesh's R with every radius scaled by `scale`; with two shells the middle sheath is left out."""
    hot = st.tint((.96, 1, 1), (1, .9, .82))
    pale = (1, .25, .18) if st.overloaded else (.55, .95, 1)
    shoulder = st.tint((.14, .85, 1))
    middle = st.tint((.08, .7, 1), (1, .025, .012))
    core = (.10 + .05 * st.u) * st.breath * scale
    specs = [(core * 1.6, 3, 1, .22, pale, .40), (scale * (.22 + .04 * st.u), 4, -1, .16, shoulder, .32),
             (scale * (.31 + .02 * st.u), 5, 2, .10, middle, .22)]
    if shells == 2:
        specs = [specs[0], specs[2]]
    solid = mesh.material('core', hot, min(1, st.strength), 'solid')
    opacity = (.35 + .65 * st.u) * min(max(st.strength, .8), 1.15)
    layers = [(spec, mesh.material('sheath %d' % i, spec[4], min(1, spec[5] * opacity), 'soft'))
              for i, spec in enumerate(specs)]
    halo = mesh.material('halo', st.tint((.15, .68, 1), (1, .025, .012)), min(1, .10 * opacity), 'soft')
    for samples in paths:
        tube(mesh, samples, lambda sample, phi: core, solid, CORE_SIDES, caps=True)
        for (base, lobes, speed, depth, _, _), material in layers:
            # Lobes repeat every 2 blocks of world position and roll with the beam clock, as in the game.
            radius = (lambda sample, phi, base=base, lobes=lobes, speed=speed, depth=depth: base * (
                1 + depth * math.sin(lobes * phi + TAU * (sample.point[sample.first] / 2 - speed * st.cycle))
                + .18 * st.wave(sample.point[sample.first]) * st.u))
            tube(mesh, samples, radius, material, sides)
        tube(mesh, samples, lambda sample, phi: BUNDLE_HALO * scale * st.breath, halo, HALO_SIDES)


def generate(profile, load, cycle, cells):
    """The beams of `cells`, each (mask, world block, Blender origin of its low corner), in Blender's axes."""
    _, _, scale, shells, sides = PROFILES[profile]
    st = Style(load, cycle)
    mesh = Mesh()
    starts = []
    for mask, world, origin in cells:
        first = len(mesh.vertices)
        paths = arms(mask, world)
        if profile == 'm_current':
            m_beam(mesh, st, paths)
        else:
            r_beam(mesh, st, paths, scale, shells, sides)
        starts.append((first, world, origin))
    # Materials repeat per cell; keep the first cell's list and point every face at it by name.
    names = []
    remap = []
    for name, rgb, alpha, mode in mesh.materials:
        if name not in names:
            names.append(name)
        remap.append(names.index(name))
    mesh.materials = [next(m for m in mesh.materials if m[0] == name) for name in names]
    mesh.indices = [remap[i] for i in mesh.indices]
    # World blocks to each cell's Blender placement: (x, y, z) -> (x, -z, y) from its origin.
    bounds = starts + [(len(mesh.vertices), None, None)]
    for (first, world, origin), (end, _, _) in zip(bounds, bounds[1:]):
        for i in range(first, end):
            x, y, z = (p - w for p, w in zip(mesh.vertices[i], world))
            mesh.vertices[i] = (origin[0] + x, origin[1] - z, origin[2] + y)
    return mesh


def solid_reach(mesh, cells):
    """The largest distance of a depth-writing vertex from its cell's axis lines, across the run."""
    reach = 0
    for face, index in zip(mesh.faces, mesh.indices):
        if mesh.materials[index][3] != 'solid':
            continue
        for i in face:
            reach = max(reach, axis_distance(mesh.vertices[i], cells))
    return reach


def glow_reach(mesh, cells):
    """The largest distance of any vertex but the fog's from its cell's axis lines."""
    fog = [i for i, m in enumerate(mesh.materials) if m[0] == 'fog']
    reach = 0
    for face, index in zip(mesh.faces, mesh.indices):
        if index in fog:
            continue
        for i in face:
            reach = max(reach, axis_distance(mesh.vertices[i], cells))
    return reach


def axis_distance(vertex, cells):
    """Distance from the nearest beam axis of the cell whose block holds `vertex` (Blender axes)."""
    best = math.inf
    for mask, _, origin in cells:
        x, y, z = vertex[0] - origin[0], vertex[2] - origin[2], origin[1] - vertex[1]
        if not (-1e-6 <= x <= 1 + 1e-6 and -1e-6 <= y <= 1 + 1e-6 and -1e-6 <= z <= 1 + 1e-6):
            continue
        local = (x, y, z)
        for axis in range(3):
            if (mask >> (2 * axis)) & 3:
                others = [local[k] - .5 for k in range(3) if k != axis]
                best = min(best, math.hypot(*others))
    return best


def quads_per_block(profile):
    """Quads per block of straight run, unmerged, as the game would draw them; None for M, whose shipped ribbons
    and fog (CableBeamMesh) these tubes only stand in for."""
    _, _, _, shells, sides = PROFILES[profile]
    if profile == 'm_current':
        return None
    return SAMPLES_PER_BLOCK * (CORE_SIDES + shells * sides + HALO_SIDES)
