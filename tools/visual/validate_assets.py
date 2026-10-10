"""Checks on the generated Federation Cable assets and on the shipped textures: its placeholder models, that every
texture is drawn, and the shape of the artist's animated and flow textures. The cable's geometry is AE2's dense cable, built in code by FederationCableBuilder and checked by
JUnit and in-game renders, not here. The hand-made Router, Pattern Provider, Processing Endpoint and Bridge art is not checked here.
"""
from pathlib import Path
from PIL import Image
import itertools
import json
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

# Every shipped texture is drawn by a model, by the cable's code-built model, or is the cable flow renderer's own
# texture.
CODE_DRAWN = {NS + 'part/cable/dense/' + name for name in ('core', 'core_connected_1', 'core_connected_2',
                                                           'core_connected_3', 'core_connected_4',
                                                           'core_connected_opposite', 'line')}
# Shipped but not drawn yet: the artist's dense cable textures not wired up yet (the connector's machine cap went
# when Provider and Endpoint fronts became dense connections), and the old cable's textures the artist still edits.
NOT_DRAWN_YET = ({NS + 'part/cable/dense/' + name for name in ('connector', 'collar', 'stream_u', 'stream_v')}
                 | {NS + 'block/' + name for name in ('glass', 'collar', 'stream_u', 'stream_v')})
used = {texture for model in models.values() for texture in model.get('textures', {}).values()} | CODE_DRAWN \
    | NOT_DRAWN_YET
for path in (BASE / 'textures').rglob('*.png'):
    im = Image.open(path)
    ref = NS + path.relative_to(BASE / 'textures').as_posix()[:-4]
    assert ref in used or ref == NS + 'entity/cable_flow', ('unused texture', ref)
    meta = path.with_suffix('.png.mcmeta')
    assert im.width == 16
    if meta.exists() and ref.startswith(NS + 'item/'):
        # The artist's animated item icons: every listed frame must exist in the strip.
        frames = [f if isinstance(f, int) else f['index'] for f in json.loads(meta.read_text())['animation']['frames']]
        assert im.height % 16 == 0 and max(frames) < im.height // 16, ref
    elif meta.exists():
        anim = json.loads(meta.read_text())['animation']
        assert im.height == 256 and anim == {'width': 16, 'height': 16, 'frametime': 2, 'interpolate': False}
        # Continuous low flow never disappears; a moving bright band distinguishes frames.
        assert im.getextrema()[3] == (255, 255)
        assert im.crop((0, 0, 16, 16)).tobytes() != im.crop((0, 16, 16, 32)).tobytes()
    else:
        assert im.height == (32 if path.name == 'cable_flow.png' else 16)

for path in (BASE / 'blockstates').glob('*.json'):
    for variant in json.loads(path.read_text())['variants'].values():
        assert variant['model'] in models
# The block model only gives the code-built model its transforms and particle; the item inherits it.
assert models[NS+'block/cable'] == {'parent': 'minecraft:block/block', 'textures': {'particle': NS+'block/armor'}}
assert models[NS+'item/cable'] == {'parent': NS+'block/cable'}
assert not (BASE / 'models/block/cable').exists(), 'per-mask cable models are no longer generated'
# Regenerate into a temporary tree, compare every generated byte with the checked-in output.
with tempfile.TemporaryDirectory() as temp:
    subprocess.run([sys.executable, str(Path(__file__).with_name('build_assets.py')), '--output', temp], check=True)
    for path in (Path(temp)/'assets/ae2federation').rglob('*'):
        if path.is_file():
            target = BASE / path.relative_to(Path(temp)/'assets/ae2federation')
            assert target.read_bytes() == path.read_bytes(), ('stale generated asset', target)
print(json.dumps({'passed': True, 'models': len(generated), 'faces': faces_checked,
                  'cable_geometry': 'code', 'reproducible': True, 'in_game_tested': False}, indent=2))
