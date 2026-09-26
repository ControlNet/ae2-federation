"""Read original AE2 19.2.17 reference textures/models into ignored local storage."""
from pathlib import Path
import argparse
import base64
import copy
import hashlib
import json
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'tools/blockbench/.local/v03-work'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar', type=Path)
args = parser.parse_args()
jar = args.jar or next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/19.2.17').rglob('appliedenergistics2-19.2.17.jar'))
WORK.mkdir(parents=True, exist_ok=True)
base = json.loads((ROOT / 'tools/blockbench/versions/v02-ae2-ceramic/models/router.bbmodel').read_text())
names = ['pattern_provider', 'pattern_provider_alternate_front', 'interface', 'energy_acceptor', 'controller', 'io_port_front']
scene = {'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False}, 'name': 'AE2 original face texture study', 'resolution': {'width': 16, 'height': 16}, 'elements': [], 'textures': [], 'outliner': []}
with zipfile.ZipFile(jar) as archive:
    for i, name in enumerate(names):
        data = archive.read(f'assets/ae2/textures/block/{name}.png')
        texture = copy.deepcopy(base['textures'][0])
        texture.update(name=name + '.png', namespace='ae2', uuid=str(uuid.uuid4()), id=str(i), source='data:image/png;base64,' + base64.b64encode(data).decode())
        scene['textures'].append(texture)
        cube = copy.deepcopy(base['elements'][0])
        cube.update(name=name, uuid=str(uuid.uuid4()))
        cube['from'] = [(i % 3) * 24, 24 - (i // 3) * 24, 0]
        cube['to'] = [(i % 3) * 24 + 16, 40 - (i // 3) * 24, 1]
        for face in cube['faces'].values():
            face['texture'] = i
        scene['elements'].append(cube)
        scene['outliner'].append(cube['uuid'])
        if name in ('pattern_provider', 'interface'):
            native = json.loads(archive.read(f'assets/ae2/models/block/{name}.json'))
            assert native == {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'ae2:block/{name}'}}
            model = copy.deepcopy(base)
            model['name'] = 'AE2 ' + name
            model['textures'] = [copy.deepcopy(texture)]
            model['textures'][0]['id'] = '0'
            (WORK / f'ae2_{name}.bbmodel').write_text(json.dumps(model) + '\n')
(WORK / 'ae2_texture_study.bbmodel').write_text(json.dumps(scene) + '\n')
(WORK / 'reference-provenance.json').write_text(json.dumps({'artifact': jar.name, 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'texture_study_order': names, 'context_models': ['pattern_provider', 'interface'], 'note': 'Texture study uses flat display surfaces; context uses verified native cube_all models.'}, indent=2) + '\n')
print('Prepared actual AE2 references:', jar.name)
