"""Asset-backed, matched-camera halo study. Run with Blender's bundled Python."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys
sys.dont_write_bytecode = True

import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from assets import Assets, model_quads
from materials import Materials
from build_scene import mesh_object, camera
import render_bundle_beams as renderer
import plasma_beam
from beam_halo import CONCEPTS, LOADS, generate, palette, portal

ROOT = HERE.parents[1]
OUTPUT = HERE / 'build/beam-halo'


def hashes():
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in (ROOT / 'common/src').rglob('*') if p.is_file()}


def main(args):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    before = hashes()
    scene = renderer.scene_setup()
    if args.device == 'cpu':
        scene.cycles.device = 'CPU'
        scene.render.threads_mode = 'FIXED'
        scene.render.threads = 8
    else:
        preferences = bpy.context.preferences.addons['cycles'].preferences
        preferences.compute_device_type = args.device.upper()
        preferences.get_devices()
        for device in preferences.devices:
            device.use = device.type == args.device.upper()
        if not any(device.use for device in preferences.devices):
            raise RuntimeError('No compatible GPU device available for ' + args.device)
        scene.cycles.device = 'GPU'
    scene.cycles.samples = 32
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.exposure = -0.25
    scene.cycles.transparent_max_bounces = 64
    scene.render.resolution_x, scene.render.resolution_y = 960, 720
    scene.render.use_stamp = False
    if args.preview:
        scene.render.resolution_percentage = 50
        scene.cycles.samples = 8
    assets = Assets(ROOT, OUTPUT / 'resources')
    materials = Materials(assets, [])
    for end in range(2):
        for x in range(2):
            for y in range(2):
                quads = model_quads(assets, assets.model(f'ae2federation:block/beam_former_large_{x}{y}'))
                for q in quads:
                    q['positions'] = [(px+x if end == 0 else 2-px-x, py+y,
                                       pz if end == 0 else 8-pz) for px, py, pz in q['positions']]
                mesh_object(f'Former {end} member {x}{y}', quads, scene.collection, materials, cutout=True)
    cameras = {
        'level': camera(scene, 'Level beside beam', (2.5, -3.875, 1.60), (1, -.875, 1), 3.5),
        'oblique': camera(scene, 'Oblique 45 degrees', (1+4/math.sqrt(2), -.875-4/math.sqrt(2), 1.9), (1, -.875, 1), 3.5),
        'close': camera(scene, 'Close 30 degrees', (2, -.875-math.sqrt(3), 1), (1, -.875, 1), 2.6),
        'pair': camera(scene, 'Pair three quarter', (10, -12, 7), (1, -4, 1), 8.7),
    }
    for key in ('level', 'oblique', 'close'):
        cameras[key].data.type = 'PERSP'
        cameras[key].data.lens = {'level': 37, 'oblique': 40, 'close': 18}[key]
        cameras[key].data.shift_x = 0
    lights = [(obj, obj.data.energy) for obj in scene.objects if obj.type == 'LIGHT']
    moving = []

    def rebuild(key, load_name, cycle):
        for obj in moving:
            data = obj.data
            mats = list(data.materials)
            bpy.data.objects.remove(obj, do_unlink=True)
            bpy.data.meshes.remove(data)
            for mat in mats:
                if mat.users == 0:
                    bpy.data.materials.remove(mat)
        moving.clear()
        linked = load_name != 'unlinked'
        load = LOADS.get(load_name, 0)
        if linked:
            # BeamFormerRenderer emits precisely the six gap cells z=1..7.
            mesh = plasma_beam.generate('ship_soft', load, cycle,
                                        [plasma_beam.straight(2, 1, 7, (1, 1, 0), 16)])
            mats = [renderer.beam_material('Plasma ' + m[0], *m[1:]) for m in mesh.materials]
            # Study-only exposure calibration; preserve all imported geometry and RGB values.
            for mat in mats:
                for node in mat.node_tree.nodes:
                    if node.type == 'EMISSION':
                        socket = node.inputs['Strength']
                        if socket.is_linked:
                            source = socket.links[0].from_socket
                            scale = mat.node_tree.nodes.new('ShaderNodeMath')
                            scale.operation = 'MULTIPLY'
                            scale.inputs[1].default_value = .65
                            mat.node_tree.links.new(source, scale.inputs[0])
                            mat.node_tree.links.new(scale.outputs[0], socket)
                        else:
                            socket.default_value *= .65
            moving.append(renderer.build_object('Shipped R plasma',
                          [[mesh.vertices[i] for i in f] for f in mesh.faces], scene.collection, mats, mesh.indices))
        quads = generate(key, load, cycle, linked)
        if key in ('J', 'J2') and linked:
            mesh = portal(key, load, cycle)
            mats = [renderer.beam_material('Portal ' + name, rgb, alpha, mode)
                    for name, rgb, alpha, mode in mesh.materials]
            faces, indices = [], []
            for end in range(2):
                for face, index in zip(mesh.faces, mesh.indices):
                    faces.append([(x if end == 0 else 2-x, y, z if end == 0 else 8-z)
                                  for x,y,z in (mesh.vertices[i] for i in face)])
                    indices.append(index)
            moving.append(renderer.build_object('Proposed halo ' + key, faces, scene.collection, mats, indices))
        elif quads:
            alphas = sorted({a for _, a in quads})
            mats = [renderer.beam_material('Proposed aperture', palette(load), a, 'add') for a in alphas]
            faces, indices = [], []
            for end in range(2):
                for quad, alpha in quads:
                    faces.append([(x if end == 0 else 2-x, y, z if end == 0 else 8-z) for x, y, z in quad])
                    indices.append(alphas.index(alpha))
            moving.append(renderer.build_object('Proposed halo ' + key, faces, scene.collection, mats, indices))

    def render(path, view, dark=False):
        if path.exists() and not args.replace:
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        scene.camera = cameras[view]
        for obj, energy in lights:
            obj.data.energy = energy * (.09 if dark else 1)
        scene.world.node_tree.nodes['Background'].inputs[1].default_value = .04 if dark else .45
        scene.render.filepath = str(path.relative_to(ROOT))
        bpy.ops.render.render(write_still=True, scene=scene.name)

    for key in args.only or CONCEPTS:
        for load in (('medium',) if args.preview else ('idle', 'medium', 'full', 'overload', 'unlinked')):
            rebuild(key, load, .25)
            destination = OUTPUT / 'preview' / key if args.preview else OUTPUT / key
            for view in args.views or cameras:
                render(destination / f'{load}-{view}-day.png', view)
            if not args.preview:
                for view in args.views or cameras:
                    render(destination / f'{load}-{view}-dark.png', view, True)
        if not args.stills_only and not args.preview:
            for load in ('idle', 'medium', 'full'):
                for frame in range(32):
                    path = OUTPUT / key / load / f'{frame:03d}.png'
                    if path.exists() and not args.replace:
                        continue
                    rebuild(key, load, frame / 32)
                    render(path, 'level')
        print('HALO_CONCEPT_OK ' + key, flush=True)
    rebuild('B', 'medium', .25)
    scene.camera = cameras['pair']
    for obj, energy in lights:
        obj.data.energy = energy
    scene.world.node_tree.nodes['Background'].inputs[1].default_value = .45
    # Make all packed asset references relative; avoid storing machine-specific paths.
    for image in bpy.data.images:
        if image.packed_file:
            image.filepath = '//resources/' + Path(image.filepath).name
    if not args.preview and (not args.only or 'B' in args.only):
        bpy.context.window.scene = scene
        bpy.context.view_layer.update()
        bpy.ops.wm.save_as_mainfile(filepath=str(OUTPUT.relative_to(ROOT) / 'beam-halo.blend'), relative_remap=True)
    assert before == hashes(), 'Production sources changed'
    report = {'concepts': CONCEPTS, 'loads': LOADS, 'frames': 32, 'fps': 10,
              'vertices_per_former': {key: 4*len(generate(key, .6, .25)) for key in CONCEPTS},
              'source_hashes': before, 'assets_unchanged': True,
              'approximation': 'Blender additive transparency and lighting; imported R near-detail geometry'}
    (OUTPUT / ('report-' + '-'.join(args.only) + '.json' if args.only else 'report.json')).write_text(json.dumps(report, indent=2) + '\n')
    print('BEAM_HALO_OK: assets and Java unchanged', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', choices=('cpu', 'optix', 'cuda'), default='cpu')
    parser.add_argument('--only', nargs='+', choices=tuple(CONCEPTS))
    parser.add_argument('--views', nargs='+', choices=('level', 'oblique', 'close', 'pair'))
    parser.add_argument('--preview', action='store_true')
    parser.add_argument('--stills-only', action='store_true')
    parser.add_argument('--replace', action='store_true', help='Replace generated study renders')
    main(parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []))
