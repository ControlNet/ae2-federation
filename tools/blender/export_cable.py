"""Compile production cable calculations against the functional offline quad backend.

Only JDK 21 and the Python standard library are required. Generated Java stays in
build/. The production sources are never edited. Unsupported API changes fail at
compile time rather than silently producing an approximate cable.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def remove_method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[:start] + source[end:]


def export(root, output):
    base = root / 'common/src/main/java/space/controlnet/ae2federation'
    build = output.parent / 'java'
    build.mkdir(parents=True, exist_ok=True)
    files = ['client/FederationCableBuilder.java', 'router/CableCoreFaces.java',
             'router/CableVisualConnections.java']
    hashes = {}
    for relative in files:
        path = base / relative
        source = path.read_text()
        hashes[str(path.relative_to(root))] = hashlib.sha256(source.encode()).hexdigest()
        if path.stem == 'CableVisualConnections':
            for signature in ['public static int connections(BlockGetter', 'public static int mask(BlockGetter',
                              'private static int kind(BlockGetter']:
                source = remove_method(source, signature)
        source = re.sub(r'^package .*;\n|^import (?!java\.).*;\n', '', source, flags=re.M)
        for old, new in [('CubeBuilder', 'CaptureCubeBuilder'), ('BakedQuad', 'CapturedQuad'),
                         ('TextureAtlasSprite', 'TextureRef'), ('Direction', 'PreviewDirection')]:
            source = re.sub(r'\b' + old + r'\b', new, source)
        (build / path.name).write_text(source)
    sources = [str(build / Path(f).name) for f in files]
    sources += [str(p) for p in (Path(__file__).parent / 'java').glob('*.java')]
    subprocess.run(['javac', '-d', str(build), *sources], check=True)
    connections = {sum(3 << (bit*2) for bit in range(6) if mask & (1 << bit)) for mask in range(64)}
    connections.update([1, 2, 5, 10, 7, 13, 14, 11])
    raw = subprocess.check_output(['java', '-cp', str(build), 'ExportCable', ','.join(map(str, sorted(connections)))])
    data = json.loads(raw)
    data['source_sha256'] = hashes
    output.write_text(json.dumps(data, separators=(',', ':')))
    print(f'Exported {len(data["shells"])} shells, 64 flow masks; validated all 4096 connection sets: {output}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    export(args.repo.resolve(), args.output or args.repo / 'tools/blender/build/cable.json')
