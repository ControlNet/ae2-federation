"""Check the isolated cable reuses the unchanged connected cable materials."""
from pathlib import Path
import json
import sys
ROOT = Path(__file__).resolve().parents[2]
folder = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'tools/blockbench/.local/v07-work'
base = ROOT / 'tools/blockbench/versions/v06-dense-panels'
for file in (folder / 'textures').iterdir():
    assert file.read_bytes() == (base / 'textures' / file.name).read_bytes(), file.name
m = json.loads((folder / 'models/cable_isolated.bbmodel').read_text())
end = json.loads((base / 'models/cable_end.bbmodel').read_text())
assert len(m['elements']) == 2
for cube in m['elements']:
    layer = cube['name'].split('_')[0]
    lo, hi = {'stream': (6, 10), 'glass': (5, 11)}[layer]
    assert cube['from'] == [lo] * 3 and cube['to'] == [hi] * 3
    assert set(cube['faces']) == {'east', 'west', 'up', 'down', 'south', 'north'}
    for face in cube['faces'].values():
        assert face['texture'] is not None
        texture = m['textures'][face['texture']]
        native = next(t for t in end['textures'] if t['name'] == texture['name'])
        assert texture['source'] == native['source']
        assert texture['frame_time'] == native['frame_time']
        assert texture['name'] == ('glass.png' if layer == 'glass' else 'stream_v.png')
        assert all(0 <= uv <= 16 for uv in face['uv'])
kept = 0
for file in (folder / 'models').glob('*.bbmodel'):
    if file.name not in ('cable_isolated.bbmodel', 'v07_family.bbmodel', 'isolated_comparison.bbmodel'):
        assert file.read_bytes() == (base / 'models' / file.name).read_bytes(), file.name
        kept += 1
print(f'V07: two closed nested cubes; shared glass/animated core verified; {kept} device/connected-cable models and every texture unchanged from V06.')
