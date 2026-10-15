"""Artist-controlled load response; no game capacity or telemetry implementation.

The six showcased ratios are deliberate design inputs, not measured mod data.
Normal load changes geometry/opacity/brightness; overload uses a red optical alarm.
"""
import math
from dataclasses import asdict, dataclass

from future_beams import Mesh
from optical_beams import PALETTE, filament, packet

RATIOS = {'a_idle': 0.0, 'b_light': .25, 'c_medium': .60,
          'd_heavy': .90, 'e_full': 1.0, 'f_overload': 1.25}
PROFILES = {
    key: (title, f'Preview input: {ratio:.0%} demand/capacity; not measured traffic.')
    for (key, ratio), title in zip(RATIOS.items(), (
        'A / IDLE 0%', 'B / LIGHT 25%', 'C / MEDIUM 60%',
        'D / HEAVY 90%', 'E / FULL 100%', 'F / OVERLOAD 125%'))
}


@dataclass(frozen=True)
class Response:
    ratio: float
    overloaded: bool
    core_radius: float
    strand_radius: float
    pulse_density: float
    pulse_amplitude: float
    halo_radius: float
    emission: float


def response(ratio):
    if not math.isfinite(ratio) or ratio < 0:
        raise ValueError('Preview ratio must be finite and nonnegative')
    u = min(ratio, 1.0)
    return Response(ratio, ratio > 1.0, .005 + .007 * u, .017 + .016 * u,
                    1 + 2 * u, .013 * u, .027 + .045 * u, .45 + 1.55 * u)


def smooth_load(previous, target, delta_seconds):
    """Frame-rate-independent proposed display smoothing; unrelated to gameplay."""
    for value in (previous, target, delta_seconds):
        if not math.isfinite(value) or value < 0:
            raise ValueError('Smoothing inputs must be finite and nonnegative')
    tau = .25 if target > previous else .8
    return target + (previous - target) * math.exp(-delta_seconds / tau)


def alarm(cycle):
    # Two gentle peaks in the 3.2 s preview; never switch the carrier off.
    return .5 + .5 * math.cos(4 * math.pi * cycle)


def palette_for(profile):
    return palette_for_load(RATIOS[profile])


def palette_for_load(ratio):
    state = response(ratio)
    u = min(state.ratio, 1)
    colors = list(PALETTE)
    if state.overloaded:
        colors = [(1, .72, .66, 1), (1, .045, .015, .70), (1, .025, .012, .14),
                  (1, .12, .04, .65), (1, .46, .35, 1), (1, .025, .01, .055), (1, .90, .82, 1)]
    return [(r, g, b, a * (1 if i in (0, 6) else .12 + .88 * u))
            for i, (r, g, b, a) in enumerate(colors)]


def emission_for(profile, cycle):
    return emission_for_load(RATIOS[profile], cycle)


def emission_for_load(ratio, cycle):
    state = response(ratio)
    return state.emission * (.70 + .40 * alarm(cycle) if state.overloaded else 1)


def parameters_for(profile):
    return asdict(response(RATIOS[profile]))


def generate(profile, mask, world, origin, cycle):
    return generate_for_load(RATIOS[profile], mask, world, origin, cycle)


def generate_for_load(ratio, mask, world, origin, cycle):
    state = response(ratio)
    mesh = Mesh()
    for axis in range(3):
        pair = (mask >> (2 * axis)) & 3
        if not pair:
            continue
        start, end = (0 if pair & 2 else .5), (1 if pair & 1 else .5)
        def tube(center, radius, color):
            filament(mesh, axis, start, end, center, radius, color, round((end-start)*64))
        def wave(s):
            # Crossfade packet trains to change density without popping topology.
            count = state.pulse_density
            lower = math.floor(count)
            blend = count - lower
            q = world[axis] + s
            return ((1-blend) * packet(q, cycle*lower, period=2/lower, sharpness=32)
                    + blend * packet(q, cycle*(lower+1), period=2/(lower+1), sharpness=32))
        center = lambda s: (0, 0)
        breath = 1 + (.10 * alarm(cycle) if state.overloaded else .018 * math.sin(math.tau*cycle))
        tube(center, lambda s: state.core_radius * breath, 0)
        tube(center, lambda s: state.core_radius * 1.3 + state.pulse_amplitude * wave(s), 1)
        tube(center, lambda s: state.core_radius * .8 + state.pulse_amplitude * wave(s), 6)
        tube(center, lambda s: state.halo_radius * breath, 5)
        for lane in range(2):
            def strand(s, lane=lane):
                phi = math.tau * ((world[axis]+s)/1.25-cycle) + lane*math.pi
                taper = min(1, abs(s-.5)/.22) if mask.bit_count() > 2 else 1
                r = state.strand_radius * taper
                return r*math.cos(phi), r*math.sin(phi)
            tube(strand, lambda s: .002 + .003 * min(state.ratio, 1) + .003*wave(s)*min(state.ratio, 1),
                 1 if lane == 0 else 3)
            tube(strand, lambda s: .008 + .007 * min(state.ratio, 1), 2)
    mesh.vertices = [(p[0]+origin[0], -p[2]+origin[1], p[1]+origin[2]) for p in mesh.vertices]
    return mesh
