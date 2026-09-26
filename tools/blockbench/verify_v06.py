"""Verify V06 painted pixels, unchanged geometry and preserved V02 devices.

Run with the approved tools/visual pixi environment (Pillow is development-only).
"""
from pathlib import Path
import base64
import io
import json
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
folder = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'tools/blockbench/.local/v06-work'
base = ROOT / 'tools/blockbench/versions/v02-ae2-ceramic'
plan_path = folder / 'pixel-plan.json'
if not plan_path.exists():
    plan_path = folder / 'source/pixel-plan.json'
tiles = json.loads(plan_path.read_text())['tiles']
for name, grid in tiles.items():
    image = Image.open(folder / 'textures' / (name + '.png')).convert('RGBA')
    assert image.size == (16, 16), name
    expected = [tuple(bytes.fromhex(color[1:])) + (255,) for row in grid for color in row]
    assert list(image.getdata()) == expected, f'Painted pixels differ: {name}'
for name in ('router', 'pattern_provider', 'processing_endpoint'):
    old = json.loads((base / 'models' / (name + '.bbmodel')).read_text())
    new = json.loads((folder / 'models' / (name + '.bbmodel')).read_text())
    assert len(old['elements']) == len(new['elements']) == 1
    for a, b in zip(old['elements'], new['elements']):
        for key in ('from', 'to', 'origin', 'faces'):
            assert a[key] == b[key], (name, key)
    for texture in new['textures']:
        embedded = Image.open(io.BytesIO(base64.b64decode(texture['source'].split(',')[1]))).convert('RGBA')
        external = Image.open(folder / 'textures' / texture['name']).convert('RGBA')
        assert embedded.tobytes() == external.tobytes(), (name, texture['name'])
kept = 0
for file in (folder / 'models').glob('*.bbmodel'):
    if file.name == 'bridge.bbmodel' or file.name.startswith('cable_'):
        assert file.read_bytes() == (base / 'models' / file.name).read_bytes(), file.name
        kept += 1
for file in (base / 'textures').iterdir():
    if file.stem not in tiles:
        assert file.read_bytes() == (folder / 'textures' / file.name).read_bytes(), file.name
router = json.loads((folder / 'models/router.bbmodel').read_text())
assert len({face['texture'] for face in router['elements'][0]['faces'].values()}) == 1
print(f'V06: {len(tiles)} painted tiles match; block geometry/UVs unchanged; Router faces equivalent; {kept} Bridge/cable projects and all unrelated textures byte-identical to V02.')
# Preserve the V04 border while replacing its sparse foreground with full panels.
approved = ROOT / 'tools/blockbench/versions/v04-ae2-quartz'
neutral = {'#f2f2f2', '#e8e8ea', '#413f54', '#4d4d67'}
coverage = {}
for name, grid in tiles.items():
    old = Image.open(approved / 'textures' / (name + '.png')).convert('RGBA')
    new = Image.open(folder / 'textures' / (name + '.png')).convert('RGBA')
    for y in range(16):
        for x in range(16):
            if x < 3 or x >= 13 or y < 3 or y >= 13:
                assert old.getpixel((x, y)) == new.getpixel((x, y)), (name, x, y)
    count = sum(grid[y][x] not in neutral for y in range(3, 13) for x in range(3, 13))
    assert count == 100, (name, count)
    coverage[name] = count
print('V06: V04 exterior pixels preserved on all 8 tiles; all 100 central pixels per tile carry functional color.')
