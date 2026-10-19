"""Four light-only data-beam prototypes with continuous cores and spatial phase.

Emission and translucent envelopes approximate optical layers in Blender; they
are not Minecraft additive framebuffer blending or measured network traffic.
All coordinates use Minecraft block units until the final scene transform.
"""
import math

from future_beams import Mesh

TAU = math.tau
PROFILES = {
    'm_stream': ('M / COHERENT STREAM', 'A continuous white-cyan carrier with smooth traveling intensity packets.'),
    'n_phase': ('N / PHASE INTERFERENCE', 'Two fine optical helices close to a continuous axial carrier.'),
    'o_mux': ('O / OPTICAL MULTIPLEX', 'Four parallel optical lanes with staggered packet phases.'),
    'p_burst': ('P / BURST ENCODING', 'Grouped short axial light pulses over a persistent fine carrier.'),
}
PALETTE = [
    (.82, .99, 1.0, 1.0),   # Pale cyan carrier.
    (.14, .85, 1.0, .65),   # Saturated cyan shoulder.
    (.15, .68, 1.0, .10),   # Wide low-opacity halo.
    (.60, .36, 1.0, .60),   # Violet optical channel.
    (.76, .68, 1.0, 1.0),   # Pale violet carrier.
    (.24, .70, 1.0, .035),  # Broad faint optical envelope.
    (.96, 1.0, 1.0, 1.0),  # Bright packet center.
]


def packet(position, cycle, offset=0.0, period=2.0, sharpness=12):
    """Periodic moving envelope, continuous at every block boundary and cycle."""
    return (.5 + .5 * math.cos(TAU * (position / period - cycle - offset))) ** sharpness


def filament(mesh, axis, start, end, center, radius, material, segments=64):
    """Open swept optical surface; no internal end caps or repeated joint discs."""
    b, c = (axis + 1) % 3, (axis + 2) % 3
    rings = []
    for i in range(segments + 1):
        s = start + (end - start) * i / segments
        y, z = center(s)
        r = radius(s)
        ring = []
        for j in range(8):
            phi = TAU * j / 8
            point = [.5, .5, .5]
            point[axis] = s
            point[b] += y + r * math.cos(phi)
            point[c] += z + r * math.sin(phi)
            ring.append(tuple(point))
        rings.append(ring)
    for before, after in zip(rings, rings[1:]):
        for j in range(8):
            k = (j + 1) % 8
            mesh.face([before[j], before[k], after[k], after[j]], material)


def generate(profile, mask, world, origin, cycle):
    if profile not in PROFILES:
        raise ValueError(profile)
    mesh = Mesh()
    for axis in range(3):
        pair = (mask >> (2 * axis)) & 3
        if not pair:
            continue
        start = 0.0 if pair & 2 else .5
        end = 1.0 if pair & 1 else .5
        coordinate = world[axis]
        segments = round((end - start) * 64)

        # Secondary lanes converge at the junction; the carrier stays connected.
        def junction_taper(s):
            if mask.bit_count() <= 2:
                return 1.0
            return min(1.0, abs(s - .5) / .22)

        def tube(center, radius, material):
            filament(mesh, axis, start, end, center, radius, material, segments)

        center = lambda s: (0.0, 0.0)
        wave = lambda s: packet(coordinate + s, cycle)
        if profile == 'm_stream':
            tube(center, lambda s: .010, 0)
            tube(center, lambda s: .014 + .014 * wave(s), 1)
            tube(center, lambda s: .007 + .008 * wave(s), 6)
            tube(center, lambda s: .033 + .021 * wave(s), 2)
            tube(center, lambda s: .052 + .022 * wave(s), 5)
        elif profile == 'n_phase':
            tube(center, lambda s: .008, 0)
            tube(center, lambda s: .065, 5)
            for lane in range(2):
                def strand(s, lane=lane):
                    phi = TAU * ((coordinate + s) / 1.25 - cycle) + lane * math.pi
                    radius = .038 * junction_taper(s)
                    return radius * math.cos(phi), radius * math.sin(phi)
                tube(strand, lambda s: .0045 + .004 * wave(s), 0 if lane == 0 else 4)
                tube(strand, lambda s: .009 + .006 * wave(s), 1 if lane == 0 else 3)
                tube(strand, lambda s: .018 + .008 * wave(s), 2)
        elif profile == 'o_mux':
            tube(center, lambda s: .0045, 0)
            tube(center, lambda s: .085, 5)
            for lane in range(4):
                phi = TAU * lane / 4 + math.pi / 4
                def channel(s, phi=phi):
                    radius = .048 * junction_taper(s)
                    return radius * math.cos(phi), radius * math.sin(phi)
                envelope = lambda s, lane=lane: packet(coordinate + s, cycle, lane / 4)
                tube(channel, lambda s: .005, 0 if lane % 2 == 0 else 4)
                tube(channel, lambda s: .008 + .007 * envelope(s), 1 if lane % 2 == 0 else 3)
                tube(channel, lambda s: .015 + .010 * envelope(s), 2)
        else:
            def burst(s):
                return max(packet(coordinate + s, cycle, offset, sharpness=180)
                           for offset in (0.0, .105, .21))
            tube(center, lambda s: .0065, 0)
            tube(center, lambda s: .007 + .013 * burst(s), 6)
            tube(center, lambda s: .011 + .015 * burst(s), 1)
            tube(center, lambda s: .021 + .025 * burst(s), 2)
            tube(center, lambda s: .034 + .022 * burst(s), 5)
    mesh.vertices = [(p[0] + origin[0], -p[2] + origin[1], p[1] + origin[2]) for p in mesh.vertices]
    return mesh
