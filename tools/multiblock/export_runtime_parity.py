"""Export the accepted prototype geometry per connection set, for the Java parity test.

A member's quads depend only on which of its six neighbours belong to the same body. For each device and each
of the 64 connection sets this builds the smallest cuboid with exactly those neighbours around the origin cell,
keeps the origin cell's quads, and records a SHA-256 of their canonical text. The Minecraft model
(`DeviceStructureGeometry`) must produce the same text. Standard library only.

    python tools/multiblock/export_runtime_parity.py            # rewrite the test resource
    python tools/multiblock/export_runtime_parity.py --check    # fail if the resource is stale
    python tools/multiblock/export_runtime_parity.py --dump router 5 build/parity-router-5.txt
"""
import hashlib
import json
import math
import sys
from itertools import product
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import geometry  # noqa: E402

OUTPUT = geometry.ROOT / 'common/src/test/resources/multiblock/device-structure-parity.json'
# Bit i of a mask is SIDE_ORDER[i]; DeviceStructureGeometry.Side has the same order.
SIDE_ORDER = ['west', 'east', 'down', 'up', 'north', 'south']
STYLES = {'router_style': 'raised', 'switch_style': 'white-backed'}


def cells_for(mask):
    lo, hi = [0, 0, 0], [1, 1, 1]
    for bit, side in enumerate(SIDE_ORDER):
        if mask >> bit & 1:
            axis, sign = geometry.SIDES[side]
            if sign < 0:
                lo[axis] = -1
            else:
                hi[axis] = 2
    return set(product(*(range(lo[a], hi[a]) for a in range(3))))


def fixed(value):
    """Thousandths of a model unit or pixel, rounded the same way as the Java test."""
    return str(math.floor(value * 16 * 1000 + 0.5))


def canonical(quads):
    lines = []
    for q in quads:
        numbers = [fixed(c) for p in q['positions'] for c in p] + [fixed(c) for t in q['uv'] for c in t]
        lines.append(q['kind'] + ' ' + ' '.join(numbers))
    return sorted(lines)


def member_quads(device, mask):
    quads = geometry.build(device, cells_for(mask), True, **STYLES)
    return [q for q in quads if tuple(q['cell']) == (0, 0, 0)]


def export():
    result = {'styles': STYLES, 'sideOrder': SIDE_ORDER, 'devices': {}}
    for device in ('router', 'switch'):
        masks = []
        for mask in range(64):
            lines = canonical(member_quads(device, mask))
            masks.append({'mask': mask, 'quads': len(lines),
                          'sha256': hashlib.sha256('\n'.join(lines).encode()).hexdigest()})
        result['devices'][device] = masks
    return json.dumps(result, indent=1) + '\n'


def main(argv):
    if argv[:1] == ['--dump']:
        device, mask, path = argv[1], int(argv[2]), Path(argv[3])
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('\n'.join(canonical(member_quads(device, mask))) + '\n')
        return 0
    text = export()
    if argv[:1] == ['--check']:
        if not OUTPUT.exists() or OUTPUT.read_text() != text:
            print('STALE ' + str(OUTPUT.relative_to(geometry.ROOT)))
            return 1
        print('PARITY_FIXTURE_CURRENT')
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(text)
    print('PARITY_FIXTURE_WRITTEN ' + str(OUTPUT.relative_to(geometry.ROOT)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
