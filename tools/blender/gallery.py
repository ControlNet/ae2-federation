"""Render compact gallery connection diagrams from the existing asset importers.

Studio diagrams omit power supplies and machine internals; they are not build
tutorials. Run with Blender's Python, then tools/visual/compose_gallery.py.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / 'tools/multiblock'))
sys.path.insert(0, str(ROOT / 'tools/blockbench'))
from assets import Assets, dependency_packs, model_quads
from materials import Materials
from build_scene import camera, collection, mesh_object, setup_scene
from bundle_beams import Mesh, Style, shell_tiles
from export_beam_former_models import export_elements
from render_bundle_beams import beam_material, build_object
import cable_beams
import plasma_beam
import geometry


def main(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if (output / 'renders.json').exists():
        raise FileExistsError('Choose a new output directory')
    exported = json.loads((output / 'cable.json').read_text())
    assets = Assets(ROOT, output / 'resources', dependency_packs(ROOT))
    materials = Materials(assets, [])
    if args.device != 'cpu':
        prefs = bpy.context.preferences.addons['cycles'].preferences
        prefs.compute_device_type = args.device.upper()
        prefs.get_devices()
        for device in prefs.devices:
            device.use = device.type == args.device.upper()
        if not any(d.use for d in prefs.devices):
            raise RuntimeError('Requested render device is unavailable')
    records = {}

    def block(coll, name, position, facing=None):
        variant = {}
        if facing:
            variant = assets.json('assets/ae2federation/blockstates/' + name + '.json')['variants']['facing=' + facing]
        model = name if ':' in name else 'ae2federation:block/' + name
        return mesh_object(name, model_quads(assets, assets.model(model), variant),
                           coll, materials, position, cutout=True)

    def light(coll, length, origin, bundle=False):
        if bundle:
            mesh = plasma_beam.generate('ship_soft', .6, .25,
                                       [plasma_beam.straight(0, 0, length, (0, 0, 0), 16)])
        else:
            mesh = Mesh()
            cable_beams.r_beam(mesh, Style(.6, .25),
                              [plasma_beam.straight(0, 0, length, (0, 0, 0), 16)],
                              cable_beams.FILL, 2, 8)
        mats = [beam_material('Gallery / ' + m[0], *m[1:]) for m in mesh.materials]
        obj = build_object('Cable light / current R study adapter',
                           [[mesh.vertices[i] for i in f] for f in mesh.faces], coll, mats, mesh.indices)
        obj.location = origin

    def wire(coll, start, length):
        for i in range(length):
            mask = '7' if i == 0 else '13' if i == length - 1 else '15'
            mesh_object('Cable / production shell', exported['shells'][mask], coll, materials,
                        (start + i, .5, 0))
        light(coll, length, (start, 0, .5))

    def formed(coll, name, x):
        assets.model('ae2federation:block/' + name)  # Include geometry.py's source in provenance.
        quads = geometry.build(name, geometry.cuboid((1, 2, 2)), True,
                               router_style='tiles', switch_style='tiles')
        for q in quads:
            q['texture'] = 'ae2federation:' + q['texture']
        mesh_object('Formed ' + name + ' / production tile layout', quads, coll, materials, (x, 1, 0))

    for key in ('network-overview', 'processing-installation', 'bundled-cable', 'beam-sizes'):
        scene = setup_scene('Gallery / ' + key)
        scene.render.resolution_x = 1600
        scene.render.resolution_y = 1000
        scene.cycles.samples = args.samples
        scene.cycles.device = 'CPU' if args.device == 'cpu' else 'GPU'
        scene.cycles.transparent_max_bounces = 48
        scene.view_settings.exposure = -.3
        floor = next(o for o in scene.objects if o.name.startswith('Stage'))
        floor.data.materials[0].node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value = (.009, .015, .024, 1)
        scene.camera = camera(scene, key, (3, -12, 8), (0, 0, .65), 9.3)
        coll = collection(scene, key + ' / actual assets')
        if key == 'network-overview':
            for x in (-2.5, 1.5):
                block(coll, 'switch', (x, .5, 0))
            block(coll, 'ae2:block/interface', (-3.5, .5, 0))
            block(coll, 'ae2:block/interface', (2.5, .5, 0))
            wire(coll, -1.5, 3)
            anchors = {'left': (-3, 0, 1), 'right': (3, 0, 1),
                       'switch_left': (-2, -.5, .5), 'switch_right': (2, -.5, .5), 'cable': (0, 0, .5)}
        elif key == 'processing-installation':
            block(coll, 'pattern_provider', (-2.5, .5, 0), 'east')
            block(coll, 'processing_endpoint', (1.5, .5, 0), 'west')
            block(coll, 'ae2:block/interface', (-3.5, .5, 0))
            block(coll, 'ae2:block/interface', (2.5, .5, 0))
            wire(coll, -1.5, 3)
            anchors = {'left': (-3, 0, 1), 'right': (3, 0, 1),
                       'provider': (-2, -.5, .5), 'endpoint': (2, -.5, .5), 'cable': (0, 0, .5)}
        elif key == 'bundled-cable':
            scene.camera = camera(scene, key, (4, -12, 8), (0, 0, 1), 9.6)
            formed(coll, 'router', -2.5)
            formed(coll, 'router', 1.5)
            quads = [{'positions': p, 'uv': [((t[0]+.5)/16, (t[1]+.5)/16)]*4,
                      'texture': 'ae2federation:part/cable/dense/core'} for p, t in shell_tiles(length=3)]
            project = json.loads((ROOT / 'tools/blockbench/projects/large_cable_straight.bbmodel').read_text())
            ring = model_quads(assets, {'elements': export_elements(project),
                                      'textures': {'0': 'ae2federation:block/large_cable/large_cable'}})
            for q in ring:
                q['positions'] = [(1.5 + z - 1, y, 2 - x) for x, y, z in q['positions']]
            quads += ring
            mesh_object('Bundle / shell and original artist ring', quads, coll, materials, (-1.5, 1, 0))
            # Device contacts use the same dense arm model as ordinary cables.
            for x, side in [(-1.5, '7'), (.5, '13')]:
                qs = [q for q in exported['shells'][side]
                      if q['texture'] == 'ae2federation:part/cable/dense/line'
                      and all(p[0] <= .125 if x < 0 else p[0] >= .875 for p in q['positions'])]
                for y in (-.5, .5):
                    for z in (0, 1):
                        mesh_object('Bundle / dense contact', qs, coll, materials, (x, y, z))
            light(coll, 3, (-1.5, 0, 1), bundle=True)
            anchors = {'router': (-2, -1, 1), 'bundle': (0, -.9, 1), 'ring': (0, 0, 1.8)}
        else:
            scene.camera = camera(scene, key, (3, -12, 7), (0, 0, 1.3), 11)
            anchors = {}
            for size, x in [(1, -4), (2, -1.8), (3, 1.5)]:
                for u in range(size):
                    for v in range(size):
                        name = 'beam_former' if size == 1 else f'beam_former_{"large" if size == 2 else "huge"}_{u}{v}'
                        block(coll, name, (x + u, .5, v))
                anchors[str(size)] = (x + size/2, -.5, 0)
        bpy.context.window.scene = scene
        bpy.context.view_layer.update()
        projected = {}
        for name, position in anchors.items():
            p = world_to_camera_view(scene, scene.camera, Vector(position))
            projected[name] = [round(p.x*1600), round((1-p.y)*1000)]
        scene.render.filepath = str(output / (key + '.png'))
        bpy.ops.render.render(write_still=True)
        records[key] = {'anchors': projected}
    bpy.ops.wm.save_as_mainfile(filepath=str(output / 'gallery.blend'))
    (output / 'renders.json').write_text(json.dumps({
        'images': records, 'resource_sha256': assets.used, 'blender': bpy.app.version_string,
        'cable_source_sha256': exported['source_sha256'],
        'tool_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                       for p in [Path(__file__), HERE/'cable_beams.py', HERE/'plasma_beam.py',
                                 HERE/'bundle_beams.py', ROOT/'tools/multiblock/geometry.py',
                                 ROOT/'tools/blockbench/export_beam_former_models.py',
                                 ROOT/'tools/blockbench/projects/large_cable_straight.bbmodel']},
        'notes': 'Connection diagrams; power and machine internals omitted. Cable light is a studio adaptation of the R/LITE and R/PLASMA study, not an in-game capture.'
    }, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--device', choices=['optix', 'cuda', 'cpu'], default='optix')
    parser.add_argument('--samples', type=int, default=48)
    main(parser.parse_args(sys.argv[sys.argv.index('--') + 1:]))
