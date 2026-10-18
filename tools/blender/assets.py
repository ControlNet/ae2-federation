"""Minecraft asset resolution and geometry conversion, using only the standard library."""
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import zipfile

TERMINALS = {'minecraft:block/block', 'minecraft:item/generated', 'minecraft:builtin/generated'}


def resource_id(value):
    return value if ':' in value else 'minecraft:' + value


class Assets:
    def __init__(self, repo, cache, packs=()):
        self.repo, self.cache = Path(repo), Path(cache)
        self.root = self.repo / 'common/src/main/resources/assets'
        self.packs = [Path(p) for p in packs]
        self.used = {}
        self.models = {}
        self.omitted_internal_faces = []
        self._raw = {}
        self._archives = {}

    def read(self, relative):
        if relative in self._raw:
            return self._raw[relative]
        paths = [self.root.parent, *self.packs]
        for path in paths:
            if path.is_dir():
                candidate = path / relative
                if not candidate.is_file():
                    continue
                raw = candidate.read_bytes()
            else:
                if path not in self._archives:
                    self._archives[path] = zipfile.ZipFile(path)
                try:
                    raw = self._archives[path].read(relative)
                except KeyError:
                    continue
            self.used[relative] = hashlib.sha256(raw).hexdigest()
            self._raw[relative] = raw
            return raw
        raise FileNotFoundError(f'Unresolved resource: {relative}; add --resource-pack directory-or-jar')

    def json(self, relative):
        return json.loads(self.read(relative))

    def model(self, identifier, chain=()):
        identifier = resource_id(identifier)
        if identifier in chain:
            raise ValueError('Model parent cycle: ' + ' -> '.join((*chain, identifier)))
        if identifier in self.models:
            return copy.deepcopy(self.models[identifier])
        namespace, name = identifier.split(':', 1)
        try:
            child = self.json(f'assets/{namespace}/models/{name}.json')
        except FileNotFoundError:
            if identifier not in TERMINALS:
                raise
            # These vanilla roots contribute no world geometry. GUI transforms are not used.
            return {'textures': {}, 'generated': 'generated' in identifier}
        if 'loader' in child:
            raise ValueError(f'Unsupported custom model loader in {identifier}: {child["loader"]}')
        parent = self.model(child['parent'], (*chain, identifier)) if 'parent' in child else {}
        result = {**parent, **child, 'textures': {**parent.get('textures', {}), **child.get('textures', {})}}
        self.models[identifier] = result
        return copy.deepcopy(result)

    def texture_id(self, reference, model):
        visited = set()
        while reference.startswith('#'):
            if reference in visited:
                raise ValueError(f'Texture alias cycle: {reference}')
            visited.add(reference)
            reference = model['textures'][reference[1:]]
        return resource_id(reference)

    def texture(self, identifier):
        namespace, name = resource_id(identifier).split(':', 1)
        relative = f'assets/{namespace}/textures/{name}.png'
        raw = self.read(relative)
        path = self.cache / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists() or path.read_bytes() != raw:
            path.write_bytes(raw)
        try:
            metadata = self.json(relative + '.mcmeta')
        except FileNotFoundError:
            metadata = {}
        return path, metadata.get('animation')


def dependency_packs(repo):
    """AE2's jar and Minecraft's client jar, as the Gradle build has cached them; models may inherit from either."""
    properties = (Path(repo) / 'gradle.properties').read_text()
    version = re.search(r'^ae2_version=(.+)$', properties, re.M).group(1).strip()
    minecraft = re.search(r'^minecraft_version=(.+)$', properties, re.M).group(1).strip()
    caches = Path.home() / '.gradle/caches'
    cache = caches / 'modules-2/files-2.1/org.appliedenergistics/appliedenergistics2' / version
    client = caches / f'neoformruntime/artifacts/minecraft_{minecraft}_client.jar'
    return sorted(cache.glob(f'*/appliedenergistics2-{version}.jar')) + ([client] if client.is_file() else [])


def face_points(lo, hi):
    x,y,z = lo; X,Y,Z = hi
    return {
        'north': [(X,Y,z),(X,y,z),(x,y,z),(x,Y,z)],
        'south': [(x,Y,Z),(x,y,Z),(X,y,Z),(X,Y,Z)],
        'west': [(x,Y,z),(x,y,z),(x,y,Z),(x,Y,Z)],
        'east': [(X,Y,Z),(X,y,Z),(X,y,z),(X,Y,z)],
        'up': [(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],
        'down': [(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)],
    }


def default_uv(face, lo, hi):
    x,y,z = lo; X,Y,Z = hi
    return {'down': [x,16-Z,X,16-z], 'up': [x,z,X,Z],
            'north': [16-X,16-Y,16-x,16-y], 'south': [x,16-Y,X,16-y],
            'west': [z,16-Y,Z,16-y], 'east': [16-Z,16-Y,16-z,16-y]}[face]


def rotate(point, axis, degrees, origin=(8,8,8), rescale=False):
    a = 'xyz'.index(axis)
    b, c = (a+1)%3, (a+2)%3
    p = [point[i]-origin[i] for i in range(3)]
    angle = math.radians(degrees)
    p[b], p[c] = p[b]*math.cos(angle)-p[c]*math.sin(angle), p[b]*math.sin(angle)+p[c]*math.cos(angle)
    if rescale:
        factor = 1 / math.cos(angle)
        p[b] *= factor; p[c] *= factor
    return [p[i]+origin[i] for i in range(3)]


def model_quads(assets, model, variant=None):
    variant = variant or {}
    if variant.get('uvlock'):
        raise ValueError('UV-locked blockstates need explicit support; refusing an inaccurate import')
    result = []
    for element in model.get('elements', []):
        points = face_points(element['from'], element['to'])
        for side, face in element['faces'].items():
            positions = points[side]
            try:
                texture = assets.texture_id(face['texture'],model)
            except KeyError:
                # Some authored Bridge elements have unresolved aliases on fully buried faces.
                # Omit only a provably covered, axis-aligned face, never invent a texture.
                normals = {'north':(0,0,-1),'south':(0,0,1),'west':(-1,0,0),
                           'east':(1,0,0),'up':(0,1,0),'down':(0,-1,0)}
                probes = [[p[i]+normals[side][i]*0.0001 for i in range(3)] for p in positions]
                covered = not element.get('rotation',{}).get('angle',0) and any(
                    other is not element and not other.get('rotation',{}).get('angle',0)
                    and all(other['from'][i] <= p[i] <= other['to'][i] for p in probes for i in range(3))
                    for other in model.get('elements',[]))
                if not covered:
                    raise
                assets.omitted_internal_faces.append({'from':element['from'],'to':element['to'],'side':side,'texture':face['texture']})
                continue
            r = element.get('rotation')
            if r:
                positions = [rotate(p, r['axis'], r['angle'], r.get('origin',(8,8,8)), r.get('rescale',False)) for p in positions]
            for axis in ('x','y'):
                if variant.get(axis):
                    positions = [rotate(p, axis, -variant[axis]) for p in positions]
            u,v,U,V = face.get('uv', default_uv(side,element['from'],element['to']))
            uv = [(u/16,v/16),(u/16,V/16),(U/16,V/16),(U/16,v/16)]
            k = face.get('rotation',0)//90
            uv = uv[k:]+uv[:k]
            result.append({'positions': [[c/16 for c in p] for p in positions], 'uv': uv,
                           'texture': texture,
                           'emissive': face.get('neoforge_data',{}).get('block_light',0) == 15})
    return result


def animation_schedule(animation, width, height):
    """Return actual atlas dimensions and ordered (index, duration-in-ticks) pairs."""
    if animation is None:
        return width, height, [(0, 1)]
    fw = animation.get('width', width if 'height' in animation else min(width,height))
    fh = animation.get('height', height if 'width' in animation else min(width,height))
    if fw <= 0 or fh <= 0 or width%fw or height%fh:
        raise ValueError('Animation dimensions must divide the texture')
    count = width//fw * (height//fh)
    sequence = []
    for entry in animation.get('frames', range(count)):
        index = entry if isinstance(entry,int) else entry['index']
        duration = animation.get('frametime',1) if isinstance(entry,int) else entry.get('time',animation.get('frametime',1))
        if not 0 <= index < count or duration <= 0:
            raise ValueError('Invalid animation frame or duration')
        sequence.append((index,duration))
    if not sequence:
        raise ValueError('Empty animation')
    return fw,fh,sequence
