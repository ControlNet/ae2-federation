"""Verify frozen art snapshots without modifying any asset (stdlib only)."""
from pathlib import Path
import base64
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ROOT / 'tools/blockbench/versions'


def main():
    for folder in sorted(VERSIONS.glob('v*')):
        manifest_path = folder / 'manifest.json'
        if not manifest_path.exists():
            raise RuntimeError(f'Missing version manifest: {folder}')
        manifest = json.loads(manifest_path.read_text())
        for name, digest in manifest['files'].items():
            actual = hashlib.sha256((folder / name).read_bytes()).hexdigest()
            assert actual == digest, f'Frozen asset changed: {folder.name}/{name}'
        files = list((folder / 'models').glob('*.bbmodel'))
        for p in files:
            model = json.loads(p.read_text())
            ids = set()
            for element in model.get('elements', []) + model.get('groups', []):
                assert element['uuid'] not in ids, f'Duplicate element UUID: {p}'
                ids.add(element['uuid'])
            textures = model['textures']
            for texture in textures:
                source = texture['source']
                assert source.startswith('data:image/png;base64,'), p
                data = base64.b64decode(source.split(',')[1], validate=True)
                assert data[:8] == b'\x89PNG\r\n\x1a\n', p
                width, height = struct.unpack('>II', data[16:24])
                assert width == 16 and height in (16, 256), (p, width, height)
            for cube in model['elements']:
                assert all(a < b for a, b in zip(cube['from'], cube['to'])), p
                for face in cube['faces'].values():
                    if face['texture'] is None:
                        continue
                    assert isinstance(face['texture'], int), p
                    assert 0 <= face['texture'] < len(textures), p
                    assert all(0 <= value <= 16 for value in face['uv']), p
            if model['meta']['model_format'] == 'java_block':
                assert model['java_block_version'] == '1.9.0', p
        for p in (folder / 'textures').glob('stream_*.png'):
            metadata = json.loads(p.with_suffix('.png.mcmeta').read_text())['animation']
            assert metadata['width'] == metadata['height'] == 16
            assert metadata['frametime'] == 2
        print(f'{folder.name}: {len(files)} models, {len(manifest["files"])} hashes verified')


if __name__ == '__main__':
    main()
