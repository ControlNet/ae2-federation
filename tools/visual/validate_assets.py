"""Checks on the generated Federation Cable assets and on the shipped textures: its placeholder models, that every
texture is drawn, and the shape of the artist's animated item icons. The cable's geometry is AE2's dense cable, built in code by FederationCableBuilder and checked by
JUnit and in-game renders, not here. The hand-made Router, Pattern Provider, Processing Endpoint and Bridge art is not checked here.
"""
from pathlib import Path
from PIL import Image
import itertools
import json
import re
import subprocess
import sys
import tempfile

REPO = Path(__file__).resolve().parents[2]
BASE = REPO / 'common/src/main/resources/assets/ae2federation'
NS = 'ae2federation:'
models = {NS + p.relative_to(BASE / 'models').as_posix()[:-5]: json.loads(p.read_text())
          for p in (BASE / 'models').rglob('*.json')}
directions = [('east', 0, 16), ('west', 0, 0), ('up', 1, 16),
              ('down', 1, 0), ('south', 2, 16), ('north', 2, 0)]
faces_checked = 0
generated = {ref: model for ref, model in models.items() if ref.startswith((NS + 'block/cable', NS + 'item/cable'))}
for ref, model in generated.items():
    assert 'ae2_federation' not in json.dumps(model) and 'preview/' not in json.dumps(model), ref
    parent = model.get('parent')
    if parent:
        assert parent == 'minecraft:block/block' or parent in models, (ref, parent)
    for child in model.get('children', {}).values():
        assert child['parent'] in models, (ref, child)
    for texture in model.get('textures', {}).values():
        assert texture.startswith(NS), (ref, texture)
        assert (BASE / 'textures' / (texture.split(':')[1] + '.png')).is_file(), (ref, texture)
    occupied = set()
    for element in model.get('elements', []):
        assert 1 <= len(element['faces']) <= 6, (ref, 'Minecraft requires 1..6 faces')
        a, b = element['from'], element['to']
        assert all(type(v) is int for v in a + b), ref
        assert all(0 <= a[i] < b[i] <= 16 for i in range(3)), (ref, a, b)
        cells = set(itertools.product(*(range(a[i], b[i]) for i in range(3))))
        assert not occupied & cells, (ref, 'overlapping geometry')
        occupied |= cells
        for face, data in element['faces'].items():
            faces_checked += 1
            uv = data['uv']
            assert all(type(v) is int and 0 <= v <= 16 for v in uv), (ref, uv)
            axes = {'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2),
                    'north': (0, 1), 'south': (0, 1)}[face]
            assert [uv[2]-uv[0], uv[3]-uv[1]] == [b[i]-a[i] for i in axes], (ref, face)
            assert data['texture'][1:] in model['textures'], ref
            assert data.get('rotation', 0) in [0, 90, 180, 270]

# Every shipped texture is drawn by a model or by the cable's code-built model.
CODE_DRAWN = {NS + 'part/cable/dense/' + name for name in ('core', 'core_connected_1', 'core_connected_2',
                                                           'core_connected_3', 'core_connected_4',
                                                           'core_connected_2_straight', 'line', 'collar')} \
    | {NS + 'block/large_cable/large_cable'} \
    | {NS + f'block/{device}/{device}_{tile}' for device in ('router', 'switch', 'quantum_cube')
       for tile in ('base', 'corner', 'side_hor', 'side_ver')}  # DeviceStructureGeometry's connected-face tiles
# Textures the client code binds by path, such as the bundle selection's hatch, are drawn too.
JAVA = '\n'.join(path.read_text() for path in (REPO / 'common/src/main/java').rglob('*.java'))
CODE_BOUND = {NS + ref for ref in re.findall(r'"textures/([a-z0-9_/]+)\.png"', JAVA)}
used = {texture for model in models.values()
        for part in [model, *model.get('children', {}).values()] for texture in part.get('textures', {}).values()} \
    | CODE_DRAWN | CODE_BOUND
for path in (BASE / 'textures').rglob('*.png'):
    im = Image.open(path)
    ref = NS + path.relative_to(BASE / 'textures').as_posix()[:-4]
    assert ref in used, ('unused texture', ref)
    # Overlays the client code draws, such as the bundle selection's hatch, keep their own size.
    if ref.startswith(NS + 'misc/'):
        continue
    # The Federation computation system's placeholders are Neo ECO's textures recoloured, in Neo ECO's sizes.
    if ref.startswith(NS + 'block/eco_computation/'):
        continue
    meta = path.with_suffix('.png.mcmeta')
    # The artist's 2x2 and 3x3 Beam Formers are modelled over two and three blocks, on 64- and 128-pixel sheets.
    size = {NS + 'block/beam_emitter/large_light_emitter': 64, NS + 'block/beam_emitter/huge_light_emitter': 128,
            NS + 'block/large_cable/large_cable': 32}.get(ref, 16)
    assert im.width == size, ref
    if meta.exists() and ref.startswith(NS + 'item/'):
        # The artist's animated item icons: every listed frame must exist in the strip.
        frames = [f if isinstance(f, int) else f['index'] for f in json.loads(meta.read_text())['animation']['frames']]
        assert im.height % 16 == 0 and max(frames) < im.height // 16, ref
    else:
        assert im.height == size, ref

for path in (BASE / 'blockstates').glob('*.json'):
    blockstate = json.loads(path.read_text())
    # Router and Switch use multipart blockstates; the others use variants.
    variants = list(blockstate['variants'].values()) if 'variants' in blockstate \
        else [part['apply'] for part in blockstate['multipart']]
    for variant in variants:
        for model in variant if isinstance(variant, list) else [variant]:
            assert model['model'] in models, (path.name, model['model'])
# The block model only gives the code-built model its transforms and particle; the item inherits it.
assert models[NS+'block/cable'] == {'parent': 'minecraft:block/block', 'textures': {'particle': NS+'part/cable/dense/core'}}
assert models[NS+'item/cable'] == {'parent': NS+'block/cable'}
assert not (BASE / 'models/block/cable').exists(), 'per-mask cable models are no longer generated'
# Regenerate into a temporary tree, compare every generated byte with the checked-in output.
with tempfile.TemporaryDirectory() as temp:
    subprocess.run([sys.executable, str(Path(__file__).with_name('build_assets.py')), '--output', temp], check=True)
    subprocess.run([sys.executable, str(REPO / 'tools/blockbench/export_beam_former_models.py'),
                    '--output', temp], check=True)
    for path in (Path(temp)/'assets/ae2federation').rglob('*'):
        if path.is_file():
            target = BASE / path.relative_to(Path(temp)/'assets/ae2federation')
            assert target.read_bytes() == path.read_bytes(), ('stale generated asset', target)
# Every size and connection mask selects exactly one model, including partially unloaded bodies.
parts = json.loads((BASE / 'blockstates/beam_former.json').read_text())['multipart']
assert len(parts) == 96


def matches(condition, state):
    if 'AND' in condition:
        return all(matches(child, state) for child in condition['AND'])
    if 'OR' in condition:
        return any(matches(child, state) for child in condition['OR'])
    return all(state[key] == value for key, value in condition.items())


# Independent world-direction expectations for each model's local +X/-X and +Y/-Y axes.
quadrant_axes = {
    'south': ('east', 'west', 'up', 'down', 'south', 'north'),
    'west': ('south', 'north', 'up', 'down', 'west', 'east'),
    'north': ('west', 'east', 'up', 'down', 'north', 'south'),
    'east': ('north', 'south', 'up', 'down', 'east', 'west'),
    'up': ('east', 'west', 'north', 'south', 'up', 'down'),
    'down': ('east', 'west', 'south', 'north', 'down', 'up'),
}
for facing, (px, nx, py, ny, front, back) in quadrant_axes.items():
    for size in ('small', 'large', 'huge'):
        for bits in itertools.product(('false', 'true'), repeat=6):
            state = dict(zip(('north', 'south', 'east', 'west', 'up', 'down'), bits))
            state.update(facing=facing, size=size)
            selected = [part for part in parts if matches(part['when'], state)]
            assert len(selected) == 1, state
            expected = 'beam_former'
            intact = (state[px] != state[nx] and state[py] != state[ny]
                      and state[front] == state[back] == 'false')
            if size == 'large' and intact:
                expected = f"beam_former_large_{int(state[px] == 'false')}{int(state[py] == 'false')}"
            # A 3x3 column: +only is 0, both is 1, -only is 2; no connection on an axis is a damaged body.
            column = {('true', 'false'): 0, ('true', 'true'): 1, ('false', 'true'): 2}
            if (size == 'huge' and (state[px], state[nx]) in column and (state[py], state[ny]) in column
                    and state[front] == state[back] == 'false'):
                expected = f"beam_former_huge_{column[state[px], state[nx]]}{column[state[py], state[ny]]}"
            assert selected[0]['apply']['model'] == NS + 'block/' + expected, (state, expected, selected)

print(json.dumps({'passed': True, 'models': len(generated), 'beam_models': 14, 'beam_multipart_cases': len(parts), 'faces': faces_checked,
                  'cable_geometry': 'code', 'reproducible': True, 'in_game_tested': False}, indent=2))
