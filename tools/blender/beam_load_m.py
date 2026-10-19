"""M coherent-stream load alternative, using the existing load-response contract.

All load ratios are explicit artist preview inputs. No game telemetry is used.
The geometry retains M's straight carrier and broad, smooth traveling light waves.
"""
import math

from beam_load import (RATIOS, PALETTE, response, alarm, palette_for,
                       emission_for, parameters_for as load_parameters)
from future_beams import Mesh
from optical_beams import filament, packet

PROFILES = {
    key: (title, f'M coherent stream at {ratio:.0%} preview load; no measured traffic.')
    for (key, ratio), title in zip(RATIOS.items(), (
        'M / IDLE 0%', 'M / LIGHT 25%', 'M / MEDIUM 60%',
        'M / HEAVY 90%', 'M / FULL 100%', 'M / OVERLOAD 125%'))
}


def parameters_for(profile):
    params = load_parameters(profile)
    del params['strand_radius']
    params['wave_sharpness'] = 12
    return params


def generate(profile, mask, world, origin, cycle):
    return generate_for_load(RATIOS[profile], mask, world, origin, cycle)


def generate_for_load(ratio, mask, world, origin, cycle):
    state = response(ratio)
    mesh = Mesh()
    breath = 1 + (.10 * alarm(cycle) if state.overloaded else .018 * math.sin(math.tau * cycle))
    for axis in range(3):
        pair = (mask >> (2 * axis)) & 3
        if not pair:
            continue
        start, end = (0 if pair & 2 else .5), (1 if pair & 1 else .5)

        def wave(s):
            lower = math.floor(state.pulse_density)
            blend = state.pulse_density - lower
            position = world[axis] + s
            return ((1 - blend) * packet(position, cycle * lower, period=2 / lower, sharpness=12)
                    + blend * packet(position, cycle * (lower + 1), period=2 / (lower + 1), sharpness=12))

        def tube(radius, material):
            filament(mesh, axis, start, end, lambda s: (0, 0), radius, material,
                     round((end - start) * 64))

        tube(lambda s: state.core_radius * breath, 0)
        tube(lambda s: state.core_radius * 1.3 + state.pulse_amplitude * wave(s), 1)
        tube(lambda s: state.core_radius * .8 + state.pulse_amplitude * wave(s), 6)
        # M's optical shoulder follows the moving wave; its broad halo stays subtle.
        tube(lambda s: state.halo_radius * .62 * breath + .35 * state.pulse_amplitude * wave(s), 2)
        tube(lambda s: state.halo_radius * breath, 5)
    mesh.vertices = [(p[0] + origin[0], -p[2] + origin[1], p[1] + origin[2]) for p in mesh.vertices]
    return mesh
