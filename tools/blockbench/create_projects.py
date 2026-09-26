"""Create editable Blockbench snapshots of the real game assets (stdlib only).

Existing projects are never overwritten. Choose a fresh --output directory to
refresh the baseline without destroying an artist's edits.
"""
from __future__ import annotations

import argparse
import base64
import copy
import json
from pathlib import Path
import struct
import uuid

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'common/src/main/resources/assets/ae2federation'
DIRECTIONS = ('north', 'east', 'south', 'west', 'up', 'down')
CABLES = {'isolated': 0, 'end': 1, 'straight_x': 3, 'straight_y': 12,
          'straight_z': 48, 'corner': 17, 'tee': 19, 'cross': 51, 'six_way': 63}


def uid(value: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'ae2federation:blockbench/' + value))


def model_layers(reference: str, layer: str = 'solid'):
    model = json.loads((ASSETS / 'models' / (reference + '.json')).read_text())
    if 'children' in model:
        for name in sorted(model['children'], key=lambda value: value == 'glass'):
            child = model['children'][name]
            yield from model_layers(child['parent'].split(':')[1], name)
    elif 'elements' in model:
        yield layer, model
    elif model.get('parent', '').startswith('ae2federation:'):
        yield from model_layers(model['parent'].split(':')[1], layer)
    else:
        raise ValueError(f'Unsupported model: {reference}')


def make_project(name: str, entries: list, overview: bool = False) -> dict:
    project = {
        'meta': {'format_version': '5.0', 'model_format': 'free' if overview else 'java_block', 'box_uv': False},
        'name': name, 'model_identifier': name, 'resolution': {'width': 16, 'height': 16},
        'elements': [], 'groups': [], 'outliner': [], 'textures': [],
    }
    if not overview:
        project['java_block_version'] = '1.9.0'  # Blockbench's 1.9-1.21.5 format range.
    texture_ids = {}
    for label, reference, offset in entries:
        parent = {'name': label, 'uuid': uid(name + '/' + label), 'origin': list(offset),
                  'export': True, 'isOpen': False, 'visibility': True}
        project['groups'].append(parent)
        parent_tree = {'uuid': parent['uuid'], 'children': []}
        project['outliner'].append(parent_tree)
        for layer, model in model_layers(reference):
            group_key = name + '/' + label + '/' + layer
            group = {'name': layer, 'uuid': uid(group_key), 'origin': list(offset),
                     'export': True, 'isOpen': False, 'visibility': True}
            project['groups'].append(group)
            tree = {'uuid': group['uuid'], 'children': []}
            parent_tree['children'].append(tree)
            for index, source in enumerate(model['elements']):
                cube = {'name': f'{layer}_{index:03}', 'uuid': uid(group_key + f'/{index}'),
                        'type': 'cube', 'box_uv': False, 'autouv': 0, 'export': True,
                        'from': [a+b for a,b in zip(source['from'], offset)],
                        'to': [a+b for a,b in zip(source['to'], offset)],
                        'origin': [8+a for a in offset], 'shade': source.get('shade', True),
                        'faces': {d: {'uv': [0,0,0,0], 'texture': None} for d in DIRECTIONS}}
                if 'rotation' in source:
                    rotation = source['rotation']
                    cube['origin'] = [a+b for a,b in zip(rotation['origin'], offset)]
                    cube['rotation'] = [rotation['angle'] if axis == rotation['axis'] else 0 for axis in 'xyz']
                    cube['rescale'] = rotation.get('rescale', False)
                for direction, face in source['faces'].items():
                    resource = face['texture']
                    while resource.startswith('#'):
                        resource = model['textures'][resource[1:]]
                    if resource not in texture_ids:
                        texture_path = ASSETS / 'textures' / (resource.split(':')[1] + '.png')
                        data = texture_path.read_bytes()
                        width, height = struct.unpack('>II', data[16:24])
                        texture_ids[resource] = len(project['textures'])
                        texture = {'name': texture_path.name, 'id': str(texture_ids[resource]),
                                   'uuid': uid(resource), 'namespace': 'ae2federation', 'folder': 'block',
                                   'width': width, 'height': height, 'uv_width': 16, 'uv_height': 16,
                                   'internal': True, 'mode': 'bitmap',
                                   'source': 'data:image/png;base64,' + base64.b64encode(data).decode()}
                        mcmeta = texture_path.with_suffix('.png.mcmeta')
                        if mcmeta.exists():
                            animation = json.loads(mcmeta.read_text())['animation']
                            texture.update(frame_time=animation.get('frametime', 1),
                                           frame_interpolate=animation.get('interpolate', False))
                        project['textures'].append(texture)
                    bb_face = copy.deepcopy(face)
                    bb_face.pop('neoforge_data', None)
                    bb_face['texture'] = texture_ids[resource]
                    cube['faces'][direction] = bb_face
                project['elements'].append(cube)
                tree['children'].append(cube['uuid'])
    if overview:
        project['editor_state'] = {'selected_elements': [], 'selected_groups': [], 'previews': {'main': {
            'position': [155, 125, 180], 'target': [40, 6, 38],
            'orthographic': False, 'zoom': 0.5, 'angle': None}}}
    # Animation strips use per-frame UV dimensions. Free format is for the
    # overview only; Java projects retain Minecraft texture animation support.
    return project


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'tools/blockbench/projects')
    args = parser.parse_args()
    entries = [(name, 'block/' + name, (0,0,0)) for name in
               ('router', 'pattern_provider', 'processing_endpoint')]
    entries.append(('bridge', 'part/bridge', (0,0,0)))
    entries += [('cable_' + name, f'block/cable/{mask:02d}', (0,0,0)) for name,mask in CABLES.items()]
    projects = {name: make_project(name, [(name,ref,offset)]) for name,ref,offset in entries}
    gallery = [(name,ref,((i%4)*24,0,(i//4)*24)) for i,(name,ref,_) in enumerate(entries)]
    projects['ae2_federation_overview'] = make_project('ae2_federation_overview', gallery, True)
    targets = [args.output / (name + '.bbmodel') for name in projects]
    if any(path.exists() for path in targets):
        parser.error('Output projects already exist; use --output with a fresh directory to preserve edits.')
    args.output.mkdir(parents=True, exist_ok=True)
    for name,project in projects.items():
        path = args.output / (name + '.bbmodel')
        path.write_text(json.dumps(project, indent=2) + '\n')
        print(f'{path.name}: {len(project["elements"])} cubes, {len(project["textures"])} embedded textures')


if __name__ == '__main__':
    main()
