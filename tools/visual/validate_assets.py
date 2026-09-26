"""Production checks derived from the v07 validator, including the revised continuous-flow contract."""
from pathlib import Path
from PIL import Image
import hashlib
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
for ref, model in models.items():
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

for path in (BASE / 'textures').rglob('*.png'):
    im = Image.open(path)
    meta = path.with_suffix('.png.mcmeta')
    assert im.width == 16
    if meta.exists():
        anim = json.loads(meta.read_text())['animation']
        assert im.height == 256 and anim == {'width': 16, 'height': 16, 'frametime': 2, 'interpolate': False}
        # Continuous low flow never disappears; a moving bright band distinguishes frames.
        assert im.getextrema()[3] == (255, 255)
        assert im.crop((0, 0, 16, 16)).tobytes() != im.crop((0, 16, 16, 32)).tobytes()
    else:
        assert im.height == 16

def elements(mask, layer):
    return models[f'{NS}block/cable/{mask:02d}_{layer}']['elements']

for mask in range(64):
    root = models[f'{NS}block/cable/{mask:02d}']
    assert root['loader'] == 'neoforge:composite'
    layers = ['solid', 'glass', 'stream']
    for layer in layers:
        for e in elements(mask, layer):
            for bit, (face, axis, end) in enumerate(directions):
                if mask & (1 << bit) and (e['to'][axis] if end else e['from'][axis]) == end:
                    assert face not in e['faces'], (mask, layer, 'connected cap', face)
    if not mask:
        assert elements(mask, 'solid') == [], 'Isolated node must not have a collar'
        for layer, lo, hi in [('glass', 5, 11), ('stream', 6, 10)]:
            e, = elements(mask, layer)
            assert e['from'] == [lo]*3 and e['to'] == [hi]*3
            assert set(e['faces']) == {d[0] for d in directions}
    streams = elements(mask, 'stream')
    # Revised v0.2: one connected 4x4 flow volume, rather than two diagonal 1x1 streams.
    cells = set()
    for e in streams:
        assert e['neoforge_data']['block_light'] == 15
        cells.update(itertools.product(*(range(e['from'][i], e['to'][i]) for i in range(3))))
    # Fully enclosed core cells have no renderable faces in the six-way model.
    if mask == 63:
        assert not cells & set(itertools.product(range(6,10), repeat=3))
        cells.update(itertools.product(range(6,10), repeat=3))
    reached = {next(iter(cells))}
    todo = list(reached)
    while todo:
        cell = todo.pop()
        for axis in range(3):
            for sign in [-1, 1]:
                other = list(cell); other[axis] += sign; other = tuple(other)
                if other in cells and other not in reached:
                    reached.add(other); todo.append(other)
    assert reached == cells, (mask, 'disconnected flow')
    for bit, (face, axis, end) in enumerate(directions):
        boundary = [c for c in cells if c[axis] == (15 if end else 0)]
        assert len(boundary) == (16 if mask & (1 << bit) else 0), (mask, face, 'flow section')
    for e in elements(mask, 'solid'):
        assert all(f['texture'] == '#collar' for f in e['faces'].values())
        assert any(mask & (1 << bit) and (e['from'][axis] >= 15 if end else e['to'][axis] <= 1)
                   for bit, (_, axis, end) in enumerate(directions)), (mask, 'collar away from boundary')
# Compare every visible unit face against the union boundary, including junction seams.
for mask in range(64):
    for layer, core in [('stream',range(6,10)),('glass',range(5,11))]:
        geometry=elements(mask,layer)
        cells=set(itertools.product(core,repeat=3))
        for e in geometry:
            cells.update(itertools.product(*(range(e['from'][i],e['to'][i]) for i in range(3))))
        expected=set()
        for cell in cells:
            for bit,(face,axis,end) in enumerate(directions):
                neighbor=list(cell);neighbor[axis]+=1 if end else -1
                if tuple(neighbor) in cells:continue
                plane=cell[axis]+(1 if end else 0)
                if mask&(1<<bit) and plane==end:continue
                other=[i for i in range(3) if i!=axis]
                expected.add((face,plane,cell[other[0]],cell[other[1]]))
        actual=set()
        for e in geometry:
            for face in e['faces']:
                _,axis,end=next(d for d in directions if d[0]==face)
                other=[i for i in range(3) if i!=axis]
                for a,b in itertools.product(*(range(e['from'][i],e['to'][i]) for i in other)):
                    unit=(face,e['to'][axis] if end else e['from'][axis],a,b)
                    assert unit not in actual,(mask,layer,'duplicate face',unit)
                    actual.add(unit)
        assert actual==expected,(mask,layer,'incorrect exposed surface',len(actual^expected))
for mask, axis in [(3, 0), (12, 1), (48, 2)]:
    spans = sorted({(e['from'][axis], e['to'][axis]) for e in elements(mask, 'solid')})
    assert spans == [(0, 1), (15, 16)]
    # Neighbor halves meet without a gap, with 16 model units between complete collars.
    assert spans[1][1] == spans[0][0] + 16 and spans[0][1] + 16 - spans[1][0] == 2
for name in ['router', 'processing_endpoint', 'pattern_provider']:
    e, = models[NS+'block/'+name]['elements']
    assert e['from'] == [0]*3 and e['to'] == [16]*3
for name in ['pattern_provider','processing_endpoint']:
    assert models[NS+'item/'+name]['elements'][0]['faces']['north']['texture'] == '#'+name
assert len({f['texture'] for f in models[NS+'block/router']['elements'][0]['faces'].values()}) == 1
for path in (BASE / 'blockstates').glob('*.json'):
    for variant in json.loads(path.read_text())['variants'].values():
        assert variant['model'] in models
provider = json.loads((BASE/'blockstates/pattern_provider.json').read_text())['variants']
assert set(provider) == {'facing='+d[0] for d in directions}
expected = {'south': (0,0), 'west': (0,90), 'north': (0,180), 'east': (0,270), 'up': (90,0), 'down': (270,0)}
for face, (x,y) in expected.items():
    variant=provider['facing='+face]
    assert (variant.get('x',0),variant.get('y',0)) == (x,y)
# Check the authored cue and all rotated ME faces, not the obsolete V1 pixel palette.
approved = REPO / 'tools/blockbench/versions/v07-isolated-cable'
for source in (approved / 'textures').iterdir():
    assert (BASE / 'textures/block' / source.name).read_bytes() == source.read_bytes(), source.name
west = Image.open(BASE/'textures/block/me_side_west.png').convert('RGBA')
assert west.getpixel((12, 8))[:3] == (162, 220, 226)
for name, transform in [('east', Image.Transpose.FLIP_LEFT_RIGHT),
                        ('up', Image.Transpose.ROTATE_270), ('down', Image.Transpose.ROTATE_90)]:
    assert Image.open(BASE/f'textures/block/me_side_{name}.png').convert('RGBA').tobytes() == west.transpose(transform).tobytes()
assert models[NS+'item/cable']['parent'] == NS+'block/cable/00'
assert models[NS+'block/cable/00_glass']['render_type'] == 'minecraft:translucent'
assert models[NS+'block/cable/00_stream']['render_type'] == 'minecraft:cutout'
assert json.loads((BASE/'blockstates/processing_endpoint.json').read_text())['variants']['']['y'] == 270
bridge = models[NS+'part/bridge']['elements']
assert [min(e['from'][i] for e in bridge) for i in range(3)] == [4,4,0]
assert [max(e['to'][i] for e in bridge) for i in range(3)] == [12,12,6]
# Regenerate into a temporary tree, compare every generated byte with the checked-in output.
with tempfile.TemporaryDirectory() as temp:
    subprocess.run([sys.executable, str(Path(__file__).with_name('build_assets.py')), '--output', temp], check=True)
    for path in (Path(temp)/'assets/ae2federation').rglob('*'):
        if path.is_file():
            target = BASE / path.relative_to(Path(temp)/'assets/ae2federation')
            assert target.read_bytes() == path.read_bytes(), ('stale generated asset', target)
print(json.dumps({'passed': True, 'models': len(models), 'faces': faces_checked,
                  'cable_masks': 64, 'reproducible': True, 'in_game_tested': False}, indent=2))
