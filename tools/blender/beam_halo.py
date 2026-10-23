"""Pure geometry for an aperture design study; coordinates are Minecraft blocks."""
import math
from bundle_beams import Style, Mesh

CONCEPTS = {'BASE': 'CURRENT', 'B': 'SOFT APERTURE', 'D': 'LOAD PULSE',
            'F': 'CARDINAL BRACKETS', 'G': 'OUTER HALO', 'H': 'DOUBLE RING', 'I': 'OUTER LOAD PULSE',
            'J': 'PORTAL RING / CLAMPED', 'J2': 'PORTAL RING / FLOATING'}
OUTER_INNER, OUTER_RADIUS, OUTER_Z = .640, .685, 17.1 / 16
LOADS = {'idle': 0, 'medium': .6, 'full': 1, 'overload': 1.25}


def clear_cardinal_solids(quad):
    """Clip hidden inlay portions out of the real +/-1-voxel cardinal struts.

    Retain a .0016-voxel clearance; all returned polygons remain quads (triangles
    repeat their last vertex), compatible with the proposed quad render path.
    """
    pieces = []
    for sx, sy in ((1,1), (1,-1), (-1,1), (-1,-1)):
        polygon = quad
        for axis, sign in ((0,sx), (1,sy)):
            clipped = []
            for a, b in zip(polygon, polygon[1:] + polygon[:1]):
                da = sign*(a[axis]-1) - .0626
                db = sign*(b[axis]-1) - .0626
                if da >= 0:
                    clipped.append(a)
                if (da >= 0) != (db >= 0):
                    t = da/(da-db)
                    clipped.append(tuple(x+t*(y-x) for x,y in zip(a,b)))
            polygon = clipped
        if len(polygon) >= 3:
            if len(polygon) <= 4:
                pieces.append(polygon + [polygon[-1]]*(4-len(polygon)))
            else:
                for i in range(1,len(polygon)-1):
                    pieces.append([polygon[0], polygon[i], polygon[i+1], polygon[i+1]])
    return pieces


def generate(key, load, cycle, linked=True):
    """Return (quad, alpha) pairs for one former facing positive z; all unlinked rings are off."""
    if key not in CONCEPTS:
        raise ValueError(key)
    if not math.isfinite(load) or load < 0:
        raise ValueError(load)
    if key == 'BASE' or not linked:
        return []
    if key in ('J', 'J2'):
        mesh = portal(key, load, cycle, linked)
        return [([mesh.vertices[i] for i in face], mesh.materials[index][2])
                for face, index in zip(mesh.faces, mesh.indices)]
    st = Style(load, cycle)
    result = []

    def arc(inner, outer, z, alpha, start=0, end=math.tau, segments=64):
        for i in range(segments):
            a = start + (end - start) * i / segments
            b = start + (end - start) * (i + 1) / segments
            result.append(([(1 + r * math.cos(t), 1 + r * math.sin(t), z)
                            for r, t in ((inner, a), (outer, a), (outer, b), (inner, b))], alpha))

    if key in ('B', 'H'):
        for i, alpha in enumerate((.06, .2, .65, .2, .06)):
            arc(.265 + i * .009, .274 + i * .009, 14.05 / 16, alpha)
    elif key == 'D':
        wave = st.wave(1)
        width = (.012 + .024 * st.u) * (1 + .25 * wave)
        arc(.305 - width, .305, 14.05 / 16, (.25 + .55 * st.u) * (.7 + .3 * wave))
    elif key == 'F':
        for i in range(4):
            start = i * math.pi / 2 - math.pi / 7
            arc(.266, .305, 14.05 / 16, .85, start, start + 2 * math.pi / 7, 12)
    if key in ('G', 'H'):
        for i, alpha in enumerate((.06, .2, .65, .2, .06)):
            arc(OUTER_INNER + i * .009, OUTER_INNER + (i + 1) * .009,
                OUTER_Z, alpha * (.55 if key == 'H' else 1))
    elif key == 'I':
        wave = st.wave(1)
        width = (.012 + .024 * st.u) * (1 + .25 * wave)
        arc(OUTER_RADIUS - width, OUTER_RADIUS, OUTER_Z,
            (.25 + .55 * st.u) * (.7 + .3 * wave))
    return [(piece, alpha) for quad, alpha in result
            for piece in (clear_cardinal_solids(quad) if quad[0][2] < 1 else [quad])]


def palette(load):
    """BundleBeamMesh's middle sheath palette, under CableBeamStyle's alarm flag."""
    return Style(load, 0).tint((.14, .85, 1))


def portal(key, load, cycle, linked=True):
    """Quad-only portal; distances below are voxels. No textures or shader animation.

    The clamped ring sits .01 voxel below the coplanar plate/diagonal arm tops,
    outside the small centre plate, so the authored arm faces really occlude it.
    The depth disc stays on the plate in both variants, behind the beam start.
    """
    mesh = Mesh()
    if not linked:
        return mesh
    st = Style(load, cycle)
    phase = cycle % 1
    strength = (.82 + .12 * st.u) * (1 + .04 * math.sin(math.tau * phase))
    z = (13.99 if key == 'J' else 19) / 16

    def band(name, inner, outer, alpha, rgb=(.14, .85, 1), start=0,
             end=math.tau, segments=96, plane=z, mode='add', breathe=True):
        mat = mesh.material(name, st.tint(rgb), alpha * strength if breathe else alpha, mode)
        for i in range(segments):
            a, b = (start + (end-start)*j/segments for j in (i, i+1))
            mesh.face([(1+r/16*math.cos(t), 1+r/16*math.sin(t), plane)
                       for r,t in ((inner,a),(outer,a),(outer,b),(inner,b))], mat)

    # Small dark disc covers the centre plate but clears the nearest arm at r=5.
    band('Depth disc', 0, 4.8, .68, (.008, .018, .028), plane=14.01/16,
         mode='solid', breathe=False)
    for inner, outer, alpha in ((5.5,6.4,.025),(6.4,7,.045),(7,8,.06),
                                (8,8.6,.045),(8.6,9.2,.025),(9.2,9.8,.012)):
        band('Glow spill', inner, outer, alpha, plane=z-.001/16)
    for inner, outer, alpha in ((6.4,6.7,.10),(6.7,7,.32),(7,8,.98),
                                (8,8.3,.32),(8.3,8.6,.10)):
        band('White core' if inner == 7 else 'Cyan falloff', inner, outer, alpha,
             (.92,1,1) if inner == 7 else (.14,.85,1))
    # One segment advances per 64 ticks: symmetry makes the slow rotations loop.
    for i in range(96):
        a = math.tau * (i + phase) / 96
        band('Outer line', 8.94, 9.06, .65, start=a, end=a+math.tau/96, segments=1)
    for i in range(16):
        a = math.tau * (i-phase) / 16
        band('Dash', 9.35, 9.50, .48, start=a, end=a+.60*math.tau/16, segments=4)
    for i in range(6):
        travel = (phase+i/6) % 1
        radius = 4.6-2.8*travel
        angle = i*math.tau/6+.2
        band('Inward streak', radius-.55, radius, .07*math.sin(math.pi*travel)**2,
             start=angle, end=angle+.025, segments=1, plane=14.02/16)
    return mesh
